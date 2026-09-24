package com.amaya.intelligence.tools

import com.amaya.intelligence.data.remote.api.ProviderConnection
import com.amaya.intelligence.data.remote.api.AiProvider
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.AmayaProviderRegistry
import com.amaya.intelligence.data.remote.api.AnthropicProvider
import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.ChatRequest
import com.amaya.intelligence.data.remote.api.ChatResponse
import com.amaya.intelligence.data.remote.api.GeminiProvider
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.remote.api.OpenAiProvider

import com.amaya.intelligence.data.remote.api.ToolCallMessage
import com.amaya.intelligence.data.remote.api.ToolResultMessage
import com.amaya.intelligence.data.repository.responseItemOutputText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Tool that allows the AI to spawn multiple subagents in parallel.
 *
 * Rate-limit strategy:
 *  - Staggered start: subagent N starts after (N-1) * STAGGER_DELAY_MS delay.
 *    This spreads API calls in time so they don't all hit the provider simultaneously.
 *  - On 429 rate-limit error: respect Retry-After if present (max 30s), retry once.
 *    If still fails → return error for that subagent (no infinite loop).
 *
 * Max subagents per call: 4.
 */
@Singleton
class InvokeSubagentsTool @Inject constructor(
    private val subagentRunner: SubagentRunner
) : Tool, ContextAwareTool {

    override val name = "invoke_subagents"
    override val description =
        "Spawn multiple temporary, read-only research workers in parallel. This is not delegation: workers have no persistent Agent identity or conversation and do not receive Agent memory/history. Use delegate_agent for one named group member who must continue its persistent conversation. " +
        "Each subagent receives its own task description and read-only workspace research tools. " +
        "Use this when a task can be split into independent sub-tasks (e.g. reading multiple folders at once, " +
        "auditing different layers of the codebase simultaneously). " +
        "Subagents do NOT see conversation history — provide ALL context in the task description. " +
        "Maximum 4 subagents per call. Returns a combined summary from all subagents."

    // Per-call emitter, call ID, provider, and workspace live in ToolExecutionContext.
    // The singleton keeps no mutable execution state.

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult {
        return try {
            @Suppress("UNCHECKED_CAST")
            val subagentsRaw = arguments["subagents"] as? List<Map<String, Any?>>
                ?: return ToolResult.Error(
                    "Missing 'subagents' argument. Expected a list of {task_name, task} objects.",
                    ErrorType.VALIDATION_ERROR
                )

            if (subagentsRaw.isEmpty()) {
                return ToolResult.Error("Subagents list is empty.", ErrorType.VALIDATION_ERROR)
            }

            // FIX #2: Validate all tasks first before building the list, to ensure proper early return
            val rawList = subagentsRaw.take(4)
            for ((idx, map) in rawList.withIndex()) {
                if (map["task"] as? String == null) {
                    return ToolResult.Error(
                        "Subagent at index $idx is missing the 'task' field.",
                        ErrorType.VALIDATION_ERROR
                    )
                }
            }
            val emitter = context.onEvent
            val parentId = context.toolCallId ?: "subagents_${System.currentTimeMillis()}"
            val providerConnection = context.providerConnection
            val selectedModelId = context.selectedModelId

            val subagents = rawList.mapIndexed { idx, map ->
                SubagentTask(
                    index       = idx,
                    taskName    = map["task_name"] as? String ?: "Subagent ${idx + 1}",
                    task        = map["task"] as String,  // safe: already validated non-null above
                    workspacePath = context.workspacePath,
                    providerConnection = providerConnection,
                    selectedModelId = selectedModelId
                )
            }

            // Emit RUNNING state for all subagents immediately
            subagents.forEach { sub ->
                emitter?.invoke(com.amaya.intelligence.data.repository.AgentEvent.SubagentUpdate(
                    parentToolCallId = parentId,
                    index    = sub.index,
                    taskName = sub.taskName,
                    prompt   = sub.task,
                    result   = null,
                    isComplete = false,
                    isError    = false
                ))
            }

            // Run all subagents in parallel with staggered starts
            val results = coroutineScope {
                subagents.map { subagent ->
                    async {
                        val result = subagentRunner.run(subagent)
                        // Emit COMPLETE state when each agent finishes
                        val isErr = result.summary.startsWith("[ERROR]") || result.summary.startsWith("[RATE LIMITED]")
                        emitter?.invoke(com.amaya.intelligence.data.repository.AgentEvent.SubagentUpdate(
                            parentToolCallId = parentId,
                            index    = subagent.index,
                            taskName = subagent.taskName,
                            prompt   = subagent.task,
                            result   = result.summary,
                            isComplete = true,
                            isError    = isErr
                        ))
                        result
                    }
                }.awaitAll()
            }

            // Format results as structured output for the main AI
            val output = buildString {
                appendLine("=== SUBAGENT EXECUTION SYNTHESIS (${results.size} parallel workers) ===")
                appendLine()
                val successful = results.count { !it.summary.startsWith("[ERROR]") && !it.summary.startsWith("[RATE LIMITED]") }
                appendLine("Status: $successful/${results.size} subagents succeeded.")
                appendLine()
                results.forEach { result ->
                    val isErr = result.summary.startsWith("[ERROR]") || result.summary.startsWith("[RATE LIMITED]")
                    val statusBadge = if (isErr) "[FAILED]" else "[SUCCESS]"
                    appendLine("----------------------------------------")
                    appendLine("Worker: ${result.taskName} $statusBadge")
                    appendLine("----------------------------------------")
                    appendLine(result.summary.trim())
                    appendLine()
                }
                appendLine("=== END OF SUBAGENT SYNTHESIS ===")
            }

            ToolResult.Success(output)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult.Error("Subagent execution failed: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}

data class SubagentTask(
    val index: Int,
    val taskName: String,
    val task: String,
    val workspacePath: String? = null,  // FIX 2.11: workspace for tool execution context
    val providerConnection: ProviderConnection? = null,
    val selectedModelId: String? = null,
    val conversationHistory: List<ChatMessage> = emptyList(),
    val systemInstructions: String? = null,
    /** `false` is reserved for persistent `delegate_agent` turns. */
    val readOnly: Boolean = true,
    val assistantMode: com.amaya.intelligence.domain.models.AssistantMode = com.amaya.intelligence.domain.models.AssistantMode.PROJECT,
    val conversationId: String? = null,
    val ownerId: String? = null,
    val agentId: Long? = null,
    val agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null,
    val delegationAgentIds: List<Long> = emptyList(),
    val onConfirmationRequired: suspend (ConfirmationRequest) -> Boolean = { false }
)

data class SubagentResult(
    val taskName: String,
    val summary: String,
    val turnMessages: List<ChatMessage> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = System.currentTimeMillis()
)

internal fun completedSubagentResponse(
    continueLoop: Boolean,
    response: String
): String = when {
    response.isBlank() -> "[INCOMPLETE] No final response."
    else -> response.trim()
}

/**
 * Runs a single subagent — a full AI chat call with tool access.
 *
 * Rate-limit handling:
 *  - Stagger: delay (index * STAGGER_DELAY_MS) before first API call.
 *  - 429 retry: if error message contains "429" or "rate limit", wait RETRY_AFTER_MS, retry once.
 */
@Singleton
class SubagentRunner @Inject constructor(
    private val anthropicProvider: AnthropicProvider,
    private val openAiProvider: OpenAiProvider,
    private val geminiProvider: GeminiProvider,
    private val settingsManager: AiSettingsManager,
    // Use Provider to break circular dependency:
    // ToolExecutor → InvokeSubagentsTool → SubagentRunner → ToolExecutor
    private val toolExecutorProvider: Provider<ToolExecutor>
) {
    companion object {
        private const val STAGGER_DELAY_MS = 2_000L  // 2s between each subagent start
        private const val MAX_RETRY_WAIT_MS = 30_000L // max 30s retry-after
        private const val DEFAULT_RETRY_MS  = 10_000L // default retry wait if no header
    }

    suspend fun run(task: SubagentTask): SubagentResult {
        // Stagger: subagent N waits N * 2s before starting
        if (task.index > 0) {
            delay(task.index * STAGGER_DELAY_MS)
        }

        return try {
            runInternal(task, isRetry = false)
        } catch (e: RateLimitException) {
            // Wait and retry once
            val waitMs = e.retryAfterMs.coerceIn(1_000L, MAX_RETRY_WAIT_MS)
            delay(waitMs)
            try {
                runInternal(task, isRetry = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e2: Exception) {
                SubagentResult(
                    taskName = task.taskName,
                    summary  = "[RATE LIMITED] Subagent failed after retry: ${e2.message}"
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SubagentResult(
                taskName = task.taskName,
                summary  = "[ERROR] ${e.message}"
            )
        }
    }

    private suspend fun runInternal(task: SubagentTask, isRetry: Boolean): SubagentResult {
        val startedAt = System.currentTimeMillis()
        val toolExecutor = toolExecutorProvider.get()

        val settings = settingsManager.getSettings()
        val activeConnection = task.providerConnection ?: settings.activeSelection?.let { selection ->
            settings.connections.firstOrNull { it.id == selection.connectionId }
        } ?: error("No model selected. Open Settings → Manage Models and select a model.")
        val adapter = AmayaProviderRegistry.require(activeConnection.providerId).adapter

        val provider: AiProvider = when (adapter) {
            com.amaya.intelligence.data.remote.api.ProviderAdapter.ANTHROPIC -> anthropicProvider
            com.amaya.intelligence.data.remote.api.ProviderAdapter.GEMINI -> geminiProvider
            com.amaya.intelligence.data.remote.api.ProviderAdapter.OPENAI_RESPONSES,
            com.amaya.intelligence.data.remote.api.ProviderAdapter.OPENAI_COMPATIBLE,
            com.amaya.intelligence.data.remote.api.ProviderAdapter.CODEX -> openAiProvider
        }
        val model = task.selectedModelId?.takeIf { it.isNotBlank() }
            ?: settings.activeSelection
                ?.takeIf { it.connectionId == activeConnection.id }
                ?.modelId
            ?: error("No model selected for ${activeConnection.name}")
        require(activeConnection.visibleModels.any { it.id == model && it.enabled }) {
            "The selected model is not shown for ${activeConnection.name}"
        }

        val systemPrompt = buildString {
            task.systemInstructions?.takeIf(String::isNotBlank)?.let { appendLine(it) }
            if (task.readOnly) {
                appendLine("You are a focused, high-precision research subagent.")
                appendLine("Use only the provided read-only tools. Do not modify state, write files, or request approval.")
                if (task.systemInstructions == null) {
                    appendLine("Structure your final answer clearly:")
                    appendLine("1. Status: [SUCCESS / BLOCKED / INCOMPLETE]")
                    appendLine("2. Key Findings: (concise bullet points)")
                    appendLine("3. Files / Symbols Inspected: (exact paths)")
                    appendLine("4. Actionable Next Steps / Blockers:")
                }
            }
            if (isRetry) appendLine("NOTE: This is a retry after a rate limit error.")
        }.trim()

        val tools = (if (task.readOnly) toolExecutor.getReadOnlyToolDefinitions()
        else toolExecutor.getToolDefinitions(task.assistantMode, task.agentCapabilityProfile, task.delegationAgentIds))
            .map { it.toAiToolDefinition(truncateDesc = true) }

        val historySize = task.conversationHistory.size
        val messages = (task.conversationHistory + ChatMessage(role = MessageRole.USER, content = task.task)).toMutableList()

        var finalResponse = ""
        var continueLoop = true

        while (continueLoop) {

            val request = ChatRequest(
                model        = model,
                messages     = messages,
                systemPrompt = systemPrompt,
                tools        = tools,
                stream       = true,
                connectionId = activeConnection.id
            )

            var textBuffer  = StringBuilder()
            val toolCalls   = mutableListOf<ToolCallMessage>()
            val responseItems = mutableListOf<String>()
            var hasToolCall = false

            provider.chat(request).collect { response ->
                when (response) {
                    is ChatResponse.TextDelta -> textBuffer.append(response.text)
                    is ChatResponse.ThinkingDelta -> { /* reasoning — not surfaced in subagent result */ }
                    is ChatResponse.ToolCall  -> {
                        if (response.id.isBlank() || tools.none { it.name == response.name } || toolCalls.any { it.id == response.id }) {
                            finalResponse = "[ERROR] Invalid, duplicate, or unadvertised tool call: ${response.name}"
                            continueLoop = false
                        } else {
                            hasToolCall = true
                            toolCalls.add(
                                ToolCallMessage(
                                    id        = response.id,
                                    name      = response.name,
                                    arguments = response.arguments,
                                    metadata  = response.metadata
                                )
                            )
                        }
                    }
                    is ChatResponse.ResponseItem -> responseItems.add(response.json)
                    is ChatResponse.Done  -> { /* no-op */ }
                    is ChatResponse.Incomplete -> {
                        finalResponse = "[ERROR] ${response.reason}"
                        continueLoop = false
                    }
                    is ChatResponse.Error -> {
                        val msg = response.message
                        // Detect rate limit — throw so caller can retry
                        if (msg.contains("429") || msg.contains("rate limit", ignoreCase = true)
                            || msg.contains("too many requests", ignoreCase = true)) {
                            // Try to parse Retry-After from message (providers often include it)
                            val retryAfter = parseRetryAfter(msg)
                            throw RateLimitException(msg, retryAfter)
                        }
                        finalResponse = "[ERROR] $msg"
                        continueLoop = false
                    }
                }
            }

            if (!continueLoop) break
            if (!hasToolCall && textBuffer.isBlank() && responseItemOutputText(responseItems).isNullOrBlank()) {
                finalResponse = "[INCOMPLETE] Provider completed without a final response"
                continueLoop = false
            }
            if (!hasToolCall) {
                finalResponse = textBuffer.toString().ifBlank { responseItemOutputText(responseItems).orEmpty() }
                continueLoop = false
            } else {
                messages.add(
                    ChatMessage(
                        role      = MessageRole.ASSISTANT,
                        content   = textBuffer.toString().takeIf { it.isNotEmpty() },
                        toolCalls = toolCalls,
                        responseItems = responseItems
                    )
                )

                for (toolCall in toolCalls) {
                    val result = toolExecutor.execute(
                        toolName      = toolCall.name,
                        arguments     = toolCall.arguments,
                        workspacePath = task.workspacePath,
                        toolCallId = toolCall.id,
                        onConfirmationRequired = task.onConfirmationRequired,
                        providerConnection = activeConnection,
                        selectedModelId = model,
                        conversationId = task.conversationId,
                        ownerId = task.ownerId,
                        agentId = task.agentId,
                        assistantMode = task.assistantMode,
                        agentCapabilityProfile = task.agentCapabilityProfile,
                        readOnly = task.readOnly
                    )
                    val resultContent = when (result) {
                        is ToolResult.Success              -> result.output
                        is ToolResult.Deferred             -> result.output
                        is ToolResult.Error                -> "Error: ${result.message}"
                        is ToolResult.RequiresConfirmation -> "Skipped (requires confirmation): ${result.reason}"
                    }

                    val resultMetadata = toolCall.metadata.toMutableMap()
                    resultMetadata["toolName"] = toolCall.name

                    messages.add(
                        ChatMessage(
                            role       = MessageRole.TOOL,
                            toolResult = ToolResultMessage(
                                toolCallId = toolCall.id,
                                content    = resultContent,
                                isError    = result is ToolResult.Error,
                                metadata   = resultMetadata
                            )
                        )
                    )
                }
            }
        }

        val summary = completedSubagentResponse(continueLoop, finalResponse)
        messages += ChatMessage(role = MessageRole.ASSISTANT, content = summary)
        return SubagentResult(
            taskName = task.taskName,
            summary = summary,
            turnMessages = messages.drop(historySize + 1),
            startedAt = startedAt,
            completedAt = System.currentTimeMillis()
        )
    }

    /** Parse retry-after seconds from error message. Returns ms. */
    private fun parseRetryAfter(message: String): Long {
        // Try "retry after X seconds" or "retry-after: X"
        val regex = Regex("""retry.after[:\s]+(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        return if (match != null) {
            (match.groupValues[1].toLongOrNull() ?: 10L) * 1000L
        } else {
            DEFAULT_RETRY_MS
        }
    }
}

/** Thrown when API returns a rate limit error. */
class RateLimitException(message: String, val retryAfterMs: Long) : Exception(message)
