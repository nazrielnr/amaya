package com.amaya.intelligence.tools

import com.amaya.intelligence.data.remote.api.AiProvider
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.AnthropicProvider
import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.ChatRequest
import com.amaya.intelligence.data.remote.api.ChatResponse
import com.amaya.intelligence.data.remote.api.GeminiProvider
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.remote.api.OpenAiProvider
import com.amaya.intelligence.data.remote.api.ProviderType
import com.amaya.intelligence.data.remote.api.ToolCallMessage
import com.amaya.intelligence.data.remote.api.ToolResultMessage
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
) : Tool {

    override val name = "invoke_subagents"
    override val description =
        "Spawn multiple independent AI subagents that run IN PARALLEL with staggered starts to avoid rate limits. " +
        "Each subagent receives its own task description and has access to all file tools. " +
        "Use this when a task can be split into independent sub-tasks (e.g. reading multiple folders at once, " +
        "auditing different layers of the codebase simultaneously). " +
        "Subagents do NOT see conversation history — provide ALL context in the task description. " +
        "Maximum 4 subagents per call. Returns a combined summary from all subagents."

    // FIX 1.6/3.6: Mutable singleton state removed. eventEmitter and currentToolCallId are now
    // passed per-call through the arguments map by ToolExecutor (keyed "__eventEmitter" and
    // "__toolCallId"). This eliminates the race condition where two concurrent chat sessions
    // would overwrite each other's emitter reference on this singleton.

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
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
            val subagents = rawList.mapIndexed { idx, map ->
                SubagentTask(
                    index    = idx,
                    taskName = map["task_name"] as? String ?: "Subagent ${idx + 1}",
                    task     = map["task"] as String  // safe: already validated non-null above
                )
            }

            // FIX 1.6/3.6: Read emitter and parentId from per-call arguments map (injected by ToolExecutor)
            // instead of mutable singleton fields. Safe for concurrent chat sessions.
            @Suppress("UNCHECKED_CAST")
            val emitter = arguments["__eventEmitter"] as? (suspend (Any) -> Unit)
            val parentId = arguments["__toolCallId"] as? String ?: "subagents_${System.currentTimeMillis()}"

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
                appendLine("=== SUBAGENT RESULTS (${results.size} agents ran in parallel) ===")
                appendLine()
                results.forEach { result ->
                    appendLine("--- [${result.taskName}] ---")
                    appendLine(result.summary)
                    appendLine()
                }
                appendLine("=== END OF SUBAGENT RESULTS ===")
            }

            ToolResult.Success(output)
        } catch (e: Exception) {
            ToolResult.Error("Subagent execution failed: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}

data class SubagentTask(
    val index: Int,
    val taskName: String,
    val task: String,
    val workspacePath: String? = null  // FIX 2.11: workspace for tool execution context
)

data class SubagentResult(
    val taskName: String,
    val summary: String
)

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
            } catch (e2: Exception) {
                SubagentResult(
                    taskName = task.taskName,
                    summary  = "[RATE LIMITED] Subagent failed after retry: ${e2.message}"
                )
            }
        } catch (e: Exception) {
            SubagentResult(
                taskName = task.taskName,
                summary  = "[ERROR] ${e.message}"
            )
        }
    }

    private suspend fun runInternal(task: SubagentTask, isRetry: Boolean): SubagentResult {
        val toolExecutor = toolExecutorProvider.get()
        val settings = settingsManager.getSettings()

        // FIX #9: Resolve provider and model from the ACTIVE AGENT config, not raw DataStore
        // activeProvider/activeModel fields which may be stale or mismatched.
        val activeAgent = settings.agentConfigs.find { it.id == settings.activeAgentId && it.enabled }
            ?: settings.agentConfigs.firstOrNull { it.enabled }
        val providerType = activeAgent?.let {
            runCatching { ProviderType.valueOf(it.providerType) }.getOrNull()
        } ?: ProviderType.OPENAI

        val provider: AiProvider = when (providerType) {
            ProviderType.ANTHROPIC -> anthropicProvider
            ProviderType.OPENAI    -> openAiProvider
            ProviderType.GEMINI    -> geminiProvider
        }
        val model = activeAgent?.modelId?.ifBlank { null }
            ?: settings.activeModel.ifBlank { null }
            ?: provider.supportedModels.firstOrNull()
            ?: ""

        val systemPrompt = """
            You are a subagent — a focused AI assistant with a single task to complete.
            You have access to file tools (read_file, write_file, list_files, search_files, run_shell, etc.)
            Complete your task thoroughly and return a clear, structured summary of what you found or did.
            Do NOT use invoke_subagents inside a subagent.
            ${if (isRetry) "NOTE: This is a retry after a rate limit error." else ""}
        """.trimIndent()

        // FIX 4.3: Use shared toAiToolDefinition() extension — removes duplicate mapping from AiRepository
        val tools = toolExecutor.getToolDefinitions()
            .filter { it.name != "invoke_subagents" } // no nested subagents
            .map { it.toAiToolDefinition(truncateDesc = true) }

        val messages = mutableListOf(
            ChatMessage(role = MessageRole.USER, content = task.task)
        )

        val resultBuffer = StringBuilder()
        var continueLoop = true
        var iterations   = 0
        val maxIterations = 8

        while (continueLoop && iterations < maxIterations) {
            iterations++

            val request = ChatRequest(
                model        = model,
                messages     = messages,
                systemPrompt = systemPrompt,
                tools        = tools,
                stream       = true
            )

            var textBuffer  = StringBuilder()
            val toolCalls   = mutableListOf<ToolCallMessage>()
            var hasToolCall = false

            provider.chat(request).collect { response ->
                when (response) {
                    is ChatResponse.TextDelta -> textBuffer.append(response.text)
                    is ChatResponse.ToolCall  -> {
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
                    is ChatResponse.Done  -> { /* no-op */ }
                    is ChatResponse.Error -> {
                        val msg = response.message
                        // Detect rate limit — throw so caller can retry
                        if (msg.contains("429") || msg.contains("rate limit", ignoreCase = true)
                            || msg.contains("too many requests", ignoreCase = true)) {
                            // Try to parse Retry-After from message (providers often include it)
                            val retryAfter = parseRetryAfter(msg)
                            throw RateLimitException(msg, retryAfter)
                        }
                        resultBuffer.appendLine("[ERROR] $msg")
                        continueLoop = false
                    }
                }
            }

            if (!hasToolCall) {
                resultBuffer.append(textBuffer)
                continueLoop = false
            } else {
                if (textBuffer.isNotBlank()) resultBuffer.appendLine(textBuffer)

                messages.add(
                    ChatMessage(
                        role      = MessageRole.ASSISTANT,
                        content   = textBuffer.toString().takeIf { it.isNotEmpty() },
                        toolCalls = toolCalls
                    )
                )

                for (toolCall in toolCalls) {
                    val result = toolExecutor.execute(
                        toolName      = toolCall.name,
                        arguments     = toolCall.arguments,
                        // FIX 2.11: Pass workspacePath so run_shell inside subagents
                        // gets the correct working directory (git, gradle, etc.)
                        workspacePath = task.workspacePath
                    )
                    val resultContent = when (result) {
                        is ToolResult.Success              -> result.output
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

        return SubagentResult(
            taskName = task.taskName,
            summary  = resultBuffer.toString().trim().ifBlank { "No output." }
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
