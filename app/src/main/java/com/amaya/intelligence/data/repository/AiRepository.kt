package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.*
import com.amaya.intelligence.data.remote.mcp.McpClientManager
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.ToolExecutor
import com.amaya.intelligence.tools.ToolResult
import com.amaya.intelligence.tools.toAiToolDefinition
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog

import com.amaya.intelligence.di.ApplicationScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
    private val anthropicProvider: AnthropicProvider,
    private val openAiProvider: OpenAiProvider,
    private val geminiProvider: GeminiProvider,
    private val settingsManager: AiSettingsManager,
    private val toolExecutor: ToolExecutor,
    private val mcpToolExecutor: com.amaya.intelligence.data.remote.mcp.McpToolExecutor,
    private val fileIndexRepository: FileIndexRepository,
    private val personaRepository: PersonaRepository,
    private val mcpClientManager: McpClientManager,
    // FIX 5.11: Inject application-scoped coroutine scope — no more manual SupervisorJob leak
    @ApplicationScope private val repoScope: CoroutineScope
) {
    // FIX 5.11: Removed manual repoJob/repoScope and close() — lifecycle managed by Hilt ApplicationScope

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
                    } catch (e: Exception) {
                        errorLog("AiRepository", "Failed to refresh MCP tools", e)
                    }
                }
            }
        }
    }
    
    // FIX 1.4: Removed getProviders() — dead code, no ViewModel/UI calls it (pre-agent era).
    // FIX 1.5/2.1: Removed getActiveProvider() — it read stale activeProvider DataStore field.
    //   Provider is now resolved from AgentConfig.providerType inline in chat().

    /**
     * Resolve the AiProvider from an AgentConfig, falling back to DataStore activeProvider.
     */
    private fun resolveProvider(agentConfig: AgentConfig): AiProvider {
        val type = runCatching { ProviderType.valueOf(agentConfig.providerType) }
            .getOrElse { ProviderType.OPENAI }
        return when (type) {
            ProviderType.ANTHROPIC -> anthropicProvider
            ProviderType.OPENAI    -> openAiProvider
            ProviderType.GEMINI    -> geminiProvider
        }
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
        message: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        projectId: Long? = null,
        workspacePath: String? = null,
        conversationId: Long? = null,
        activeAgentId: String? = null,
        selectedModel: String? = null,
        onConfirmation: suspend (ConfirmationRequest) -> Boolean = { false }
    ): Flow<AgentEvent> = channelFlow {
        
        val settings = settingsManager.getSettings()

        // Resolve agent config — use activeAgentId from UI (most up-to-date)
        val agentId = activeAgentId ?: settings.activeAgentId
        // Only use enabled agents — if selected agent is disabled, find first enabled one
        val agentConfig = settings.agentConfigs.find { it.id == agentId && it.enabled }
            ?: settings.agentConfigs.firstOrNull { it.enabled }

        // Block chat if no enabled agent exists
        if (agentConfig == null) {
            send(AgentEvent.Error("No AI agent configured. Please add and enable an agent in Settings → Agents.", retryable = false))
            send(AgentEvent.Done)
            return@channelFlow
        }

        // Block chat if model ID is blank
        if (agentConfig.modelId.isBlank() && selectedModel.isNullOrBlank()) {
            send(AgentEvent.Error("No model ID configured for agent \"${agentConfig.name}\". Please edit the agent in Settings → Agents and add a Model ID.", retryable = false))
            send(AgentEvent.Done)
            return@channelFlow
        }

        // FIX 2.1: Resolve provider from agentConfig (guaranteed non-null here)
        val provider = resolveProvider(agentConfig)
        // Priority: selectedModel from UI (always up-to-date) > agentConfig.modelId > activeModel in DataStore
        // Never fall through to agentConfig if selectedModel is explicitly set from UI
        val model = when {
            !selectedModel.isNullOrBlank()        -> selectedModel
            !agentConfig?.modelId.isNullOrBlank() -> agentConfig!!.modelId
            settings.activeModel.isNotBlank()     -> settings.activeModel
            else                                  -> provider.supportedModels.firstOrNull() ?: ""
        }
        debugLog("AiRepository") { "chat() resolved model=$model (from UI: $selectedModel, agent: ${agentConfig?.modelId}, datastore: ${settings.activeModel})" }
        
        // Build system prompt with project context
        val systemPrompt = buildSystemPrompt(projectId, workspacePath, conversationId)
        
        // Build tool definitions
        val tools = buildToolDefinitions()
        
        // Start conversation loop
        var messages = conversationHistory + ChatMessage(
            role = MessageRole.USER,
            content = message
        )
        
        var continueLoop = true
        var iterations = 0
        val maxIterations = 10 // Prevent infinite loops
        
        while (continueLoop && iterations < maxIterations) {
            iterations++
            
            // Emit NewIteration for subsequent iterations (after tool results)
            if (iterations > 1) {
                send(AgentEvent.NewIteration)
            }
            
            val request = ChatRequest(
                model        = model,
                messages     = messages,
                systemPrompt = systemPrompt,
                tools        = tools,
                maxTokens    = agentConfig.maxTokens,
                stream       = true,
                // Pass resolved agentId so providers use the correct API key
                agentId      = agentConfig.id
            )
            
            var textBuffer = StringBuilder()
            val toolCalls = mutableListOf<ToolCallMessage>()
            var hasToolCalls = false
            
            provider.chat(request).collect { response ->
                when (response) {
                    is ChatResponse.TextDelta -> {
                        textBuffer.append(response.text)
                        send(AgentEvent.TextDelta(response.text))
                    }
                    
                    is ChatResponse.ToolCall -> {
                        hasToolCalls = true
                        android.util.Log.d("AiRepository", "ToolCall received: name=${response.name} args=${response.arguments} id=${response.id}")
                        send(AgentEvent.ToolCallStart(response.id, response.name, response.arguments))
                        
                        toolCalls.add(ToolCallMessage(
                            id = response.id,
                            name = response.name,
                            arguments = response.arguments,
                            metadata = response.metadata
                        ))
                    }
                    
                    is ChatResponse.Done -> {
                        response.usage?.let { usage ->
                            send(AgentEvent.Usage(usage.inputTokens, usage.outputTokens))
                        }
                    }
                    
                    is ChatResponse.Error -> {
                        send(AgentEvent.Error(response.message, response.retryable))
                        continueLoop = false
                    }
                }
            }
            
            if (!hasToolCalls) {
                // No tool calls, we're done
                continueLoop = false
            } else {
                // Add assistant message with tool calls
                messages = messages + ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = textBuffer.toString().takeIf { it.isNotEmpty() },
                    toolCalls = toolCalls
                )
                
                // Execute each tool call
                for (toolCall in toolCalls) {
                    val channel = this
                    val result = mcpToolExecutor.execute(
                        toolName = toolCall.name,
                        arguments = toolCall.arguments,
                        workspacePath = workspacePath,
                        toolCallId = toolCall.id,
                        // FIX: Use channel.send() — channelFlow's ProducerScope is thread-safe,
                        // unlike flow{}'s emit() which panics on concurrent coroutine access.
                        onEvent = { event -> if (event is AgentEvent) channel.send(event) },
                        onConfirmationRequired = onConfirmation,
                        // Pass resolved agentConfig so SubagentRunner uses the SAME provider/model
                        // as the main chat loop — not a stale DataStore snapshot.
                        agentConfig = agentConfig
                    )
                    
                    val resultContent = when (result) {
                        is ToolResult.Success -> result.output
                        is ToolResult.Error -> "Error: ${result.message}"
                        is ToolResult.RequiresConfirmation -> "Confirmation required: ${result.reason}"
                    }
                    
                    send(AgentEvent.ToolCallResult(
                        toolCallId = toolCall.id,
                        toolName = toolCall.name,
                        result = resultContent,
                        isError = result is ToolResult.Error
                    ))
                    
                    // Add tool result to conversation
                    // Store both ID (for OpenAI) and name (for Gemini) in metadata
                    val resultMetadata = toolCall.metadata.toMutableMap()
                    resultMetadata["toolName"] = toolCall.name  // Gemini needs the function name
                    
                    messages = messages + ChatMessage(
                        role = MessageRole.TOOL,
                        toolResult = ToolResultMessage(
                            toolCallId = toolCall.id, // OpenAI requires original tool_call_id
                            content = resultContent,
                            isError = result is ToolResult.Error,
                            metadata = resultMetadata
                        )
                    )
                }
            }
        }
        
        if (iterations >= maxIterations) {
            send(AgentEvent.Error("Maximum iterations reached", retryable = false))
        }
        
        send(AgentEvent.Done)
    }
    
    /**
     * Build system prompt with project context.
     *
     * When Pro mode is active, the persona MD files (AGENTS.md, SOUL.md, IDENTITY.md, etc.)
     * provide all identity/behavior rules — so the base prompt is kept minimal to avoid
     * redundancy. In Simple/no-persona mode, the full base prompt is used.
     */
    private suspend fun buildSystemPrompt(projectId: Long?, workspacePath: String?, conversationId: Long? = null): String {
        val now = java.time.LocalDateTime.now()
        val dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val tz = java.util.TimeZone.getDefault().id

        val workspaceContext = if (workspacePath != null) {
            """

            CURRENT WORKSPACE:
            Path: $workspacePath
            
            When the user asks to list files, read files, or perform any file operation,
            use this workspace path as the base directory.
            """.trimIndent()
        } else ""

        val personaFragment = personaRepository.buildPromptFragment()
        val isProMode = personaRepository.getMode() == com.amaya.intelligence.data.repository.PersonaMode.PRO

        // FIX 4.5: Extract shared tools section to eliminate ~40 lines of duplication
        // between Pro mode and Simple mode prompt branches.
        val sharedToolsSection = """
            TOOLS — MEMORY & REMINDERS:
            - Use update_memory(content, target="daily") to log important events from this session
            - Use update_memory(content, target="long") to persist user preferences/facts permanently
            - Use create_reminder(title, message, datetime, conversation_id=$conversationId, session_mode=...) when user asks to be reminded — ALWAYS pass conversation_id so replies come back to this chat
            - session_mode="continue" (default): when reminder fires, AI reply is appended to THIS conversation (id=$conversationId). Best for reminders related to ongoing tasks.
            - session_mode="new": when reminder fires, a brand new conversation is created. Best for standalone/recurring reminders unrelated to current context.
            
            TOOLS — TASK PROGRESS (update_todo):
            - For any multi-step task, call update_todo at the START with merge=false to set your full plan.
            - Each item needs: id (int, 1-based), status ("pending"/"in_progress"/"completed"), content (imperative label), active_form (present-continuous label shown when collapsed, optional).
            - As you work, call update_todo with merge=true to update individual item statuses by id.
            - This shows the user a live progress bar above the chat input — keep it up to date.
            
            TOOLS — SUBAGENTS (invoke_subagents):
            - Use invoke_subagents when a task has INDEPENDENT sub-tasks that can run IN PARALLEL.
            - Perfect for: reading multiple folders at once, auditing different layers, generating multiple independent files.
            - Each subagent gets its own task description — include ALL context (file paths, project info, what to look for).
            - Subagents do NOT see conversation history — be explicit and self-contained in each task.
            - Max 4 subagents per call. Results returned as a combined summary.
            - Example: scan 4 different folders simultaneously, each subagent reads its folder and reports findings.
            - DO NOT use for tasks that depend on each other (A must finish before B starts).
            
            FALLBACK STRATEGY:
            If a native tool call fails, try using the run_shell tool as an alternative.
        """.trimIndent()

        val basePrompt = if (isProMode && personaFragment.isNotBlank()) {
            // Pro mode: MD files define identity/rules — base provides only environment facts
            """
            Current date: $dateStr | Time: $timeStr | Timezone: $tz
            
            ENVIRONMENT:
            - Platform: Android (mobile device)
            - Shell commands available via run_shell tool
            - Internal and external storage access
            
            $sharedToolsSection
            $personaFragment
            $workspaceContext
            """.trimIndent()
        } else {
            // Simple/no persona: full base prompt
            """
            You are Amaya, a versatile AI assistant that can help with any task,
            especially those related to the user's workspace. You can manage files,
            write code, draft documents, answer questions, and more.
            
            Current date: $dateStr | Time: $timeStr | Timezone: $tz
            
            ENVIRONMENT:
            - Platform: Android (mobile device)
            - Shell commands available via run_shell tool
            - Internal and external storage access
            
            GUIDELINES:
            1. Always explain what you're doing before using tools
            2. Use native file tools instead of shell commands when possible
            3. Create backups before modifying important files
            4. Ask for confirmation before destructive operations
            5. Keep responses concise but informative
            6. When writing code, follow the project's existing style
            
            $sharedToolsSection
            Always find a way to complete the task. Never give up on the first failure.
            $personaFragment
            $workspaceContext
            """.trimIndent()
        }

        return basePrompt
    }
    
    /**
     * Build tool definitions for AI.
     * Uses cached MCP tools — refresh happens automatically via settingsFlow watcher in init.
     */
    private fun buildToolDefinitions(): List<AiToolDefinition> {
        // FIX 4.3: Use shared toAiToolDefinition() extension (ToolExecutor.kt) — removes duplicate mapping
        val localTools = toolExecutor.getToolDefinitions()
            .map { it.toAiToolDefinition(truncateDesc = true) }
        // MCP tools come from external servers — truncate their descriptions too
        val mcpTools = mcpClientManager.getCachedToolDefinitions().map { tool ->
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

}

/**
 * Events emitted during AI chat.
 */
sealed class AgentEvent {
    data class TextDelta(val text: String) : AgentEvent()
    data class ToolCallStart(val toolCallId: String, val name: String, val arguments: Map<String, Any?>) : AgentEvent()
    data class ToolCallResult(val toolCallId: String, val toolName: String, val result: String, val isError: Boolean) : AgentEvent()
    data class Usage(val inputTokens: Int, val outputTokens: Int) : AgentEvent()
    data class Error(val message: String, val retryable: Boolean) : AgentEvent()
    data object NewIteration : AgentEvent()
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
