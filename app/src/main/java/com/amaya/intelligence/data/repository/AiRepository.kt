package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.ProjectDao
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.remote.api.*
import com.amaya.intelligence.data.remote.mcp.McpClientManager
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.tools.ClarificationRequest
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.TodoRepository
import com.amaya.intelligence.tools.ToolExecutor
import com.amaya.intelligence.tools.toAiToolDefinition
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog

import com.amaya.intelligence.di.ApplicationScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentRuntimeTarget {
    LOCAL,
    WINDOWS_BRIDGE
}

private const val TITLE_FALLBACK = "New Chat"
internal val TITLE_PREFIX = Regex("(?i)^\\s*(?:title|judul)\\s*:\\s*")
internal val TITLE_TAG = Regex("(?is)<title>\\s*(.*?)\\s*</title>")
internal val TITLE_QUOTED = Regex("[\"“]([^\"”\\r\\n]{2,60})[\"”]")
internal val TITLE_SEPARATOR = Regex("\\s+(?:[–—|]|-)\\s+")
internal val TITLE_EDGE_MARKUP = Regex("^[\\s*#>`_-]+|[\\s*#>`_-]+$")
internal val TITLE_META = Regex("(?i)^(?:here is|this is|the title|your title|berikut|judulnya)\\b")
internal val TITLE_TRAILING_PUNCTUATION = Regex("[.!?:;]+$")
internal val TITLE_WHITESPACE = Regex("\\s+")
internal val THINK_BLOCK = Regex("(?is)<think>.*?</think>")
internal val INTEGER_TEXT = Regex("[+-]?\\d+")
internal const val MAX_STREAM_CONTINUATIONS = 3
internal const val MAX_STREAM_BACKOFF_MS = 4_000L
/**
 * How many invalid/duplicate/unadvertised tool calls a single turn tolerates before the
 * tool loop stops. Every rejection is fed back to the model as a failure so it can
 * self-correct; this bound prevents an infinite mis-call loop.
 */
internal const val MAX_FAILED_TOOL_ATTEMPTS = 3
/**
 * Scheme C: how many extra verification passes a turn may run after the model first stops.
 * One pass is enough to catch a premature "done" without dragging every tool-using turn
 * through a second full loop.
 */
internal const val MAX_VERIFICATION_PASSES = 1
/** Scheme E: identical tool failures before the host steers the model to revise its plan. */
internal const val REPLAN_AFTER_REPEATED_FAILURES = 2
internal const val STREAM_CONTINUATION_PROMPT = "Continue the previous response exactly where it stopped. Do not repeat any text. Use tools if needed to complete the request."
internal const val TOOL_RESULT_TRUNCATION_MARKER = "\n… [tool result truncated by context budget]"
internal const val AUTO_COMPACTION_MARKER = "[AUTO-COMPACTED ACTIVE CONTEXT]"
/** Keeps the machine-written ledger out of the slot holding the curated recall summary. */
internal const val AUTO_COMPACTION_SUMMARY_SUFFIX = "#autocompact"

internal fun truncateToolResultForContext(content: String, maxChars: Int): String = when {
    content.length <= maxChars -> content
    maxChars <= TOOL_RESULT_TRUNCATION_MARKER.length -> TOOL_RESULT_TRUNCATION_MARKER.take(maxChars)
    else -> content.take(maxChars - TOOL_RESULT_TRUNCATION_MARKER.length).trimEnd() + TOOL_RESULT_TRUNCATION_MARKER
}

internal fun verificationPrompt(goal: String): String = """
        Host verification step: You are attempting to finalize this turn after tool operations.
        The user's original goal is: "$goal"
        
        Sanity Check Requirements:
        1. Verify every explicit requirement and constraint requested in the goal has been addressed.
        2. Cite concrete file paths, symbols, or tool outputs from your execution evidence.
        3. If any task component remains incomplete, unverified, or failed: DO NOT STOP — continue executing the necessary tools immediately.
        4. If and only if all deliverables are complete and verified: reply 'VERIFIED' followed by a concise 1-2 sentence evidence summary.
    """.trimIndent()

internal fun responseItemOutputText(items: List<String>): String? = items.asSequence()
    .mapNotNull { raw ->
        runCatching { JSONObject(raw) }.getOrNull()?.let { item ->
            when (item.optString("type")) {
                "message" -> item.optJSONArray("content")?.let { content ->
                    (0 until content.length()).mapNotNull { index ->
                        content.optJSONObject(index)?.takeIf { it.optString("type") in setOf("output_text", "text") }
                            ?.optString("text")?.takeIf(String::isNotBlank)
                    }.joinToString("\n")
                }
                "output_text" -> item.optString("text")
                else -> null
            }
        }?.takeIf(String::isNotBlank)
    }
    .joinToString("\n")
    .takeIf(String::isNotBlank)

internal fun canContinueStream(response: ChatResponse, hasToolCalls: Boolean): Boolean =
    !hasToolCalls && when (response) {
        is ChatResponse.Incomplete -> response.retryable
        is ChatResponse.Error -> response.retryable
        else -> false
    }

internal fun repeatedToolFailureWarning(toolName: String, signature: String, count: Int): String? =
    if (count < 2) {
        null
    } else if (toolName == "browser") {
        "Warning: the same browser tool error has occurred $count times ($signature). Do not repeat the same call unchanged. Inspect the current page state, choose a different action, or ask the user for clarification."
    } else {
        "Warning: tool '$toolName' failed $count times with the same error ($signature). Do not repeat the same call unchanged; inspect the failure, adjust the arguments, try a different approach, or ask the user for clarification."
    }

internal fun shouldExecuteReceivedToolCalls(response: ChatResponse, hasToolCalls: Boolean): Boolean =
    hasToolCalls && when (response) {
        is ChatResponse.Incomplete -> response.retryable
        is ChatResponse.Error -> response.retryable
        else -> false
    }

internal fun hasProviderUserQuery(messages: List<ChatMessage>): Boolean = messages.any { message ->
    message.role == MessageRole.USER && (!message.content.isNullOrBlank() || message.images.isNotEmpty())
}

fun normalizeIntegerArgument(value: Any?): Any? = when (value) {
    is String -> value.trim().takeIf(INTEGER_TEXT::matches)?.toLongOrNull() ?: value
    is Number -> value.toDouble().takeIf(Double::isFinite)?.takeIf { it % 1.0 == 0.0 }?.let { number ->
        when {
            number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() -> number.toInt()
            number in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble() -> number.toLong()
            else -> value
        }
    } ?: value
    else -> value
}

internal fun fallbackConversationTitle(userMessage: String): String =
    userMessage
        .replace(TITLE_WHITESPACE, " ")
        .trim()
        .ifBlank { TITLE_FALLBACK }

internal fun extractConversationTitle(raw: String): String? {
    val cleaned = raw.replace(THINK_BLOCK, " ").trim()
    val candidates = buildList {
        TITLE_TAG.find(cleaned)?.groupValues?.getOrNull(1)?.let(::add)
        TITLE_QUOTED.findAll(cleaned).forEach { add(it.groupValues[1]) }
        cleaned.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.split(TITLE_SEPARATOR, limit = 2)
            ?.firstOrNull()
            ?.let(::add)
    }
    return candidates.firstNotNullOfOrNull { candidate ->
        candidate
            .replace(TITLE_EDGE_MARKUP, "")
            .replace(TITLE_PREFIX, "")
            .replace(TITLE_EDGE_MARKUP, "")
            .replace(TITLE_TRAILING_PUNCTUATION, "")
            .replace(TITLE_WHITESPACE, " ")
            .trim()
            .takeIf { title ->
                !TITLE_META.containsMatchIn(title) &&
                    title.length <= 60 &&
                    title.split(TITLE_WHITESPACE).size in 2..5
            }
    }
}

internal fun sanitizeConversationTitle(raw: String, fallback: String): String =
    extractConversationTitle(raw) ?: fallback

/**
 * Repository for AI interactions.
 *
 * Coordinates between:
 * - Multiple AI providers (Anthropic, OpenAI, Gemini)
 * - Tool execution engine
 * - Project context (file index)
 *
 * Implements the agentic loop:
 * 1. Send user message + context to AI
 * 2. AI responds with text and/or tool calls
 * 3. Execute tools and send results back
 * 4. Repeat until AI is done
 */
@Singleton
class AiRepository @Inject constructor(
    internal val anthropicProvider: AnthropicProvider,
    internal val openAiProvider: OpenAiProvider,
    internal val geminiProvider: GeminiProvider,
    internal val settingsManager: AiSettingsManager,
    internal val toolExecutor: ToolExecutor,
    internal val agentDao: AgentDao,
    internal val projectDao: ProjectDao,
    internal val mcpToolExecutor: com.amaya.intelligence.data.remote.mcp.McpToolExecutor,
    internal val windowsBridgeToolProvider: com.amaya.intelligence.impl.bridge.windows.tools.WindowsBridgeToolProvider,
    internal val fileIndexRepository: FileIndexRepository,
    internal val sessionMemoryRepository: SessionMemoryRepository,
    internal val workspaceMemoryStore: FileWorkspaceMemoryStore,
    internal val skillRepository: SkillRepository,
    internal val skillUsageLogRepository: SkillUsageLogRepository,
    internal val selfImprovementPipeline: SelfImprovementPipeline,
    internal val contextManager: ContextManager,
    internal val referenceDocumentRepository: ReferenceDocumentRepository,
    internal val agentMemoryRepository: AgentMemoryRepository,
    internal val mcpClientManager: McpClientManager,
    internal val ledgerStore: ActiveContextLedgerStore,
    internal val todoRepository: TodoRepository,
    // FIX 5.11: Inject application-scoped coroutine scope — no more manual SupervisorJob leak
    @ApplicationScope internal val repoScope: CoroutineScope
) {
    internal companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 8_192
        const val CONTEXT_SAFETY_RESERVE_TOKENS = 1_024
        const val AUTO_COMPACTION_OUTPUT_TOKENS = 2_048
        const val AUTO_COMPACTION_SAFETY_TOKENS = 1_024

        /** Compact only once the history genuinely crowds its budget. */
        const val COMPACTION_HIGH_WATER = 0.85
        /** Warm the ledger off the hot path once the history approaches the limit. */
        const val COMPACTION_WARM_WATER = 0.70
        /** Below this, evicting is cheaper than the round-trip that would describe it. */
        const val COMPACTION_MIN_RECLAIM_TOKENS = 2_048
        const val COMPACTION_TIMEOUT_MS = 20_000L
        /** Re-planning after a ledger update can evict more; bound how many times we chase that. */
        const val MAX_COMPACTION_PASSES = 3
        /** Share of the input budget the ledger may occupy inside the system prompt. */
        const val LEDGER_BUDGET_FRACTION = 0.15
        const val LEDGER_MIN_TOKENS = 256
        const val LEDGER_MAX_TOKENS = 4_096
    }

    // FIX 5.11: Removed manual repoJob/repoScope and close() — lifecycle managed by Hilt ApplicationScope

    /** Hermes-style compaction: model summary of the active session, optionally focused. */
    suspend fun compressConversation(
        conversationHistory: List<ChatMessage>,
        selectedModel: String,
        connectionId: String?,
        focus: String
    ): Result<String> = compressConversationImpl(conversationHistory, selectedModel, connectionId, focus)

    init {
        // Watch for MCP config changes and refresh tools automatically
        repoScope.launch {
            var lastMcpJson = ""
            settingsManager.settingsFlow.collect { settings ->
                if (settings.mcpConfigJson != lastMcpJson) {
                    lastMcpJson = settings.mcpConfigJson
                    debugLog("AiRepository") { "MCP config changed, refreshing tools..." }
                    try {
                        val tools = mcpClientManager.refreshTools()
                        debugLog("AiRepository") { "MCP tools refreshed: ${tools.size} tools" }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        errorLog("AiRepository", "Failed to refresh MCP tools", e)
                    }
                }
            }
        }
    }


    internal fun resolveProvider(connection: ProviderConnection): AiProvider =
        when (AmayaProviderRegistry.require(connection.providerId).adapter) {
            ProviderAdapter.ANTHROPIC -> anthropicProvider
            ProviderAdapter.GEMINI -> geminiProvider
            ProviderAdapter.OPENAI_RESPONSES, ProviderAdapter.OPENAI_COMPATIBLE, ProviderAdapter.CODEX -> openAiProvider
        }

    /**
     * Send a message and receive streaming responses.
     *
     * This handles the full agentic loop:
     * - Sends message to AI with project context and tools
     * - Executes tool calls as needed
     * - Returns final response
     *
     * @param message User's message
     * @param conversationHistory Previous messages in conversation
     * @param projectId Active project for context
     * @param onConfirmation Callback for tool confirmation
     * @return Flow of agent events (text, tool calls, results)
     */
    // FIX: Use channelFlow instead of flow to support concurrent emissions from subagent
    // async{} coroutines. flow{} is NOT thread-safe — concurrent emit() from different
    // coroutines (e.g. SubagentUpdate events from parallel async{}) causes:
    // "Flow invariant is violated: Emission from another coroutine is detected"
    // → IllegalStateException → FATAL EXCEPTION → app force close.
    // channelFlow uses a Channel internally which IS thread-safe for concurrent senders.
    fun chat(
        message: String, userImages: List<ChatImage> = emptyList(), conversationHistory: List<ChatMessage> = emptyList(), projectId: Long? = null, workspacePath: String? = null, assistantMode: AssistantMode = AssistantMode.forWorkspace(workspacePath), ownerId: String? = null, agentId: Long? = null, conversationId: Long? = null, connectionId: String? = null, selectedModel: String? = null, effort: ThinkingEffort? = null, runtimeTarget: AgentRuntimeTarget = AgentRuntimeTarget.LOCAL, onConfirmation: suspend (ConfirmationRequest) -> Boolean = { false }, onClarification: suspend (ClarificationRequest) -> String? = { null }, pendingConversationEvents: suspend () -> List<UiMessage> = { emptyList() }, onConversationEventsInjected: suspend (List<UiMessage>) -> Unit = {}, messageRole: MessageRole = MessageRole.USER
    ): Flow<AgentEvent> = chatImpl(message, userImages, conversationHistory, projectId, workspacePath, assistantMode, ownerId, agentId, conversationId, connectionId, selectedModel, effort, runtimeTarget, onConfirmation, onClarification, pendingConversationEvents, onConversationEventsInjected, messageRole)

    internal fun buildToolDefinitions(
        runtimeTarget: AgentRuntimeTarget = AgentRuntimeTarget.LOCAL,
        assistantMode: AssistantMode = AssistantMode.CHAT,
        hasWorkspace: Boolean = false,
        agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null,
        delegationAgentIds: List<Long> = emptyList()
    ): List<AiToolDefinition> {
        val bridgeTools = windowsBridgeToolProvider.getAvailableBridgeTools()
            .map { it.toAiToolDefinition(truncateDesc = true) }
        if (runtimeTarget == AgentRuntimeTarget.WINDOWS_BRIDGE) {
            if (bridgeTools.isNotEmpty()) {
                debugLog("AiRepository") { "Building Windows Bridge tool defs: bridge=${bridgeTools.size}" }
            }
            return bridgeTools
        }

        // FIX 4.3: Use shared toAiToolDefinition() extension (ToolExecutor.kt) — removes duplicate mapping
        val localTools = toolExecutor.getToolDefinitions(assistantMode, agentCapabilityProfile, delegationAgentIds)
            .filterNot { !hasWorkspace && it.name in setOf("workspace_search", "workspace_change", "read_file", "run_shell", "invoke_subagents") }
            .map { it.toAiToolDefinition(truncateDesc = true) }
        // MCP tools come from external servers — truncate their descriptions too.
        // CHAT mode excludes MCP by design (strict tool surface); AGENT mode defers to the
        // agent's capability profile (mcp=..., default true for legacy profiles); PROJECT
        // mode keeps today's behavior of advertising enabled servers.
        val mcpTools = mcpClientManager.getCachedToolDefinitions()
            .filter { tool ->
                when (assistantMode) {
                    AssistantMode.CHAT -> false
                    AssistantMode.AGENT -> agentCapabilityProfile?.allows(tool.name) ?: true
                    AssistantMode.PROJECT -> true
                }
            }
            .map { tool ->
                tool.copy(
                    description = tool.description.let { if (it.length > 1023) it.take(1023) + "…" else it },
                    parameters = tool.parameters.copy(
                        properties = tool.parameters.properties.mapValues { (_, prop) ->
                            prop.copy(description = prop.description.let { if (it.length > 1023) it.take(1023) + "…" else it })
                        }
                    )
                )
            }
        debugLog("AiRepository") { "Building tool defs: local=${localTools.size}, mcp=${mcpTools.size}" }
        return localTools + mcpTools
    }

    /** Generate a compact conversation title from the user's first message. */
    suspend fun generateTitle(
        userMessage: String,
        providerConnection: ProviderConnection,
        selectedModel: String?
    ): String = generateTitleImpl(userMessage, providerConnection, selectedModel)

}

/**
 * Events emitted during AI chat.
 */
sealed class AgentEvent {
    data class TextDelta(val text: String) : AgentEvent()
    /** Reasoning/thinking chunk, rendered separately from the answer. */
    data class ThinkingDelta(val text: String) : AgentEvent()
    data class ToolCallStart(
        val toolCallId: String,
        val name: String,
        val arguments: Map<String, Any?>,
        val metadata: Map<String, String> = emptyMap()
    ) : AgentEvent()
    data class ToolCallResult(
        val toolCallId: String,
        val toolName: String,
        val result: String,
        val isError: Boolean,
        val deferredTaskId: Long? = null
    ) : AgentEvent()
    data class Usage(val inputTokens: Int, val outputTokens: Int) : AgentEvent()
    data class ResponseItem(val json: String) : AgentEvent()
    data class Incomplete(val reason: String, val retryable: Boolean) : AgentEvent()
    data class Error(val message: String, val retryable: Boolean) : AgentEvent()
    data object NewIteration : AgentEvent()
    /** Context compaction started. Emitted before a blocking summarization round-trip. */
    data class Compacting(val evictedMessages: Int, val evictedTokens: Int) : AgentEvent()
    /**
     * Compaction finished. [ledger] is the durable session state the host should persist so the
     * next turn inherits it instead of re-deriving it.
     */
    data class Compacted(
        val ledger: String,
        val evictedMessages: Int,
        val reclaimedTokens: Int,
        val usedFallback: Boolean
    ) : AgentEvent()
    data object Done : AgentEvent()
    // Emitted by InvokeSubagentsTool as each subagent starts/completes
    data class SubagentUpdate(
        val parentToolCallId: String,
        val index: Int,
        val taskName: String,
        val prompt: String,
        val result: String? = null,
        val isComplete: Boolean = false,
        val isError: Boolean = false
    ) : AgentEvent()
}

// FIX 1.4: ProviderInfo fully removed — was only used by deleted getProviders() function.
