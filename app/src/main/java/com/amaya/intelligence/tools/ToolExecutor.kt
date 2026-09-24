package com.amaya.intelligence.tools

import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.RiskLevel
import com.amaya.intelligence.domain.security.ValidationResult
import com.amaya.intelligence.util.ToolDebugLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central executor for all AI tools.
 *
 * This class:
 * 1. Routes tool calls to the appropriate handler
 * 2. Applies security validation
 * 3. Handles confirmation flows
 * 4. Provides tool definitions for AI prompts
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val agentMemoryTool: AgentMemoryTool,
    private val askUserTool: AskUserTool,
    private val commandValidator: CommandValidator,
    private val terminalSettingsRepository: TerminalSettingsRepository,
    private val toolRegistry: AgentToolRegistry
) {

    /**
     * Execute a tool by name with the given arguments.
     *
     * @param toolName Name of the tool to execute
     * @param arguments Map of argument name to value
     * @param workspacePath Optional workspace path to use as default working directory
     * @param onConfirmationRequired Callback when user confirmation is needed
     * @return Result of the tool execution
     */
    suspend fun execute(
        toolName: String,
        arguments: Map<String, Any?>,
        workspacePath: String? = null,
        toolCallId: String? = null,
        onEvent: (suspend (Any) -> Unit)? = null,
        onConfirmationRequired: suspend (ConfirmationRequest) -> Boolean = { false },
        onClarificationRequired: suspend (ClarificationRequest) -> String? = { null },
        providerConnection: com.amaya.intelligence.data.remote.api.ProviderConnection? = null,
        selectedModelId: String? = null,
        readOnly: Boolean = false,
        conversationId: String? = null,
        ownerId: String? = null,
        agentId: Long? = null,
        assistantMode: AssistantMode = AssistantMode.PROJECT,
        agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null
    ): ToolResult {
        val startedAtNs = System.nanoTime()
        ToolDebugLog.start(toolCallId, toolName, arguments, conversationId, ownerId, agentId, assistantMode)
        suspend fun finish(result: ToolResult): ToolResult = result.also {
            ToolDebugLog.finish(toolCallId, toolName, it, startedAtNs)
        }
        if (!assistantModeAllowsCapability(toolName, assistantMode, agentCapabilityProfile)) {
            return finish(ToolResult.Error("Tool '$toolName' is unavailable in ${assistantMode.name.lowercase()} mode.", ErrorType.PERMISSION_ERROR))
        }
        val normalizedArguments = normalizeIntegerArguments(toolName, arguments)
        val capabilityCall = CapabilityToolMapper.map(toolName, normalizedArguments)
        val handlerName = if (toolName == "agent_memory") agentMemoryTool.name else capabilityCall?.handlerName ?: toolName
        val handlerArguments = if (toolName == "agent_memory") normalizedArguments else capabilityCall?.arguments ?: normalizedArguments
        val tool = toolRegistry.getTool(handlerName)
            ?: return finish(ToolResult.Error(
                "Unknown tool: $toolName. Available: ${getModelCallableTools().map { it.name }.joinToString()}",
                ErrorType.VALIDATION_ERROR
            ))
        if (tool.visibility != ToolVisibility.MODEL) {
            return finish(ToolResult.Error(
                "Tool '$toolName' is ${tool.visibility.name.lowercase()} and is not callable from the normal model tool loop.",
                ErrorType.PERMISSION_ERROR
            ))
        }

        val safeArguments = sanitizeModelArguments(handlerArguments).getOrElse { error ->
            return finish(ToolResult.Error(error.message.orEmpty(), ErrorType.VALIDATION_ERROR))
        }
        val resolvedArguments = WorkspacePathResolver.resolve(handlerName, safeArguments, workspacePath).getOrElse { error ->
            return finish(ToolResult.Error(error.message.orEmpty(), ErrorType.SECURITY_VIOLATION, recoverable = true))
        }
        val modelArguments = applyHostExecutionContext(handlerName, resolvedArguments, workspacePath)
        if (readOnly && !toolRegistry.isAllowedInReadOnlyMode(handlerName)) {
            return finish(ToolResult.Error("Tool '$toolName' is unavailable to read-only subagents.", ErrorType.PERMISSION_ERROR))
        }
        val callIdentity = toolCallId ?: return finish(ToolResult.Error(
            "Tool call '$toolName' is missing a call ID; approval cannot be bound safely.",
            ErrorType.VALIDATION_ERROR
        ))
        val executionContext = ToolExecutionContext(
            toolCallId = callIdentity,
            workspacePath = workspacePath,
            onEvent = onEvent,
            providerConnection = providerConnection,
            selectedModelId = selectedModelId,
            conversationId = conversationId,
            ownerId = ownerId,
            agentId = agentId,
            agentCapabilityProfile = agentCapabilityProfile,
            assistantMode = assistantMode,
            onConfirmationRequired = onConfirmationRequired,
            onClarificationRequired = onClarificationRequired,
            readOnly = readOnly
        )

        // ask_user is host-gated (a question, never an action): route it straight to the
        // context-aware tool, which suspends the turn until the user answers or dismisses it.
        if (handlerName == "ask_user") {
            return finish(askUserTool.execute(handlerArguments, executionContext))
        }

        // Pre-validate model-owned arguments only. The workspace root is passed so shell
        // commands are contained inside the active workspace (the AI never leaves it).
        val validation = commandValidator.validateToolCall(
            handlerName,
            modelArguments,
            terminalSettingsRepository.getSettings(),
            workspacePath = workspacePath
        )

        when (validation) {
            is ValidationResult.Denied -> {
                return finish(ToolResult.Error(
                    validation.reason,
                    ErrorType.SECURITY_VIOLATION
                ))
            }

            is ValidationResult.RequiresConfirmation -> {
                val confirmed = onConfirmationRequired(
                    ConfirmationRequest(
                        toolName = CapabilityToolMapper.displayName(toolName, arguments),
                        reason = validation.reason,
                        details = modelArguments.toString(),
                        riskLevel = validation.riskLevel,
                        toolCallId = callIdentity
                    )
                )

                if (!confirmed) {
                    return finish(ToolResult.Error(
                        "User declined: ${validation.reason}",
                        ErrorType.PERMISSION_ERROR
                    ))
                }
            }

            is ValidationResult.Allowed -> { /* proceed */ }
        }

        suspend fun run(context: ToolExecutionContext): ToolResult =
            if (tool is ContextAwareTool) tool.execute(modelArguments, context)
            else tool.execute(modelArguments)

        val approvedContext = executionContext.copy(
            confirmed = validation is ValidationResult.RequiresConfirmation
        )
        val result = try {
            run(approvedContext)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            ToolDebugLog.cancel(toolCallId, toolName, startedAtNs)
            throw cancelled
        } catch (error: Throwable) {
            ToolDebugLog.crash(toolCallId, toolName, error, startedAtNs)
            throw error
        }

        // Handle nested confirmation requests from tools.
        if (result is ToolResult.RequiresConfirmation) {
            val confirmed = onConfirmationRequired(
                ConfirmationRequest(
                    toolName = CapabilityToolMapper.displayName(toolName, arguments),
                    reason = result.reason,
                    details = result.details,
                    riskLevel = RiskLevel.MEDIUM,
                    toolCallId = callIdentity
                )
            )

            if (!confirmed) {
                return finish(ToolResult.Error(
                    "User declined: ${result.reason}",
                    ErrorType.PERMISSION_ERROR
                ))
            }

            return finish(run(approvedContext.copy(confirmed = true)))
        }

        return finish(result)
    }

    private fun normalizeIntegerArguments(toolName: String, arguments: Map<String, Any?>): Map<String, Any?> {
        val integerNames = getToolDefinitions(AssistantMode.AGENT).firstOrNull { it.name == toolName }
            ?.parameters
            ?.filter { it.type.equals("integer", ignoreCase = true) }
            ?.mapTo(mutableSetOf()) { it.name }
            .orEmpty()
        if (integerNames.isEmpty()) return arguments
        return arguments.mapValues { (name, value) ->
            if (name in integerNames) com.amaya.intelligence.data.repository.normalizeIntegerArgument(value) else value
        }
    }

    /**
     * Get all available tools.
     */
    fun getModelCallableTools(): List<Tool> = toolRegistry.getModelCallableTools()

    fun getReadOnlyToolDefinitions(): List<ToolDefinition> = toolRegistry.getReadOnlyToolDefinitions()

    /**
     * Get model-callable tool definitions for AI prompts (JSON Schema format).
     */
    fun getToolDefinitions(
        mode: AssistantMode = AssistantMode.PROJECT,
        agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null,
        delegationAgentIds: List<Long> = emptyList()
    ): List<ToolDefinition> = toolRegistry.getToolDefinitions(mode, agentCapabilityProfile, delegationAgentIds)
}

internal fun exposeToolDefinition(definition: ToolDefinition, mode: AssistantMode): ToolDefinition {
    if (mode != AssistantMode.AGENT || definition.name != "memory") return definition
    return definition.copy(
        name = "agent_memory",
        description = "Manage memory private to the active agent. Update requires the version returned by list/search.",
        parameters = definition.parameters.map { parameter ->
            if (parameter.name == "operation") parameter.copy(
                description = "save, list, search, or update",
                enum = listOf("save", "list", "search", "update")
            ) else parameter
        }
    )
}

private val CHAT_CAPABILITIES = setOf("web_search", "memory", "skill", "ask_user", "update_todo")
private val PROJECT_DISABLED_CAPABILITIES = setOf("browser", "delegate_agent", "reminder", "create_reminder")

internal fun assistantModeAllowsCapability(
    name: String,
    mode: AssistantMode,
    agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null
): Boolean = when (mode) {
    AssistantMode.CHAT -> name in CHAT_CAPABILITIES
    AssistantMode.PROJECT -> name !in PROJECT_DISABLED_CAPABILITIES
    // ask_user is always host-safe (a question, never an action), so every mode may use it.
    AssistantMode.AGENT -> name == "agent_memory" || name == "ask_user" || agentCapabilityProfile?.allows(name) ?: true
}

/**
 * Request for user confirmation.
 */
internal fun isAllowedInReadOnlyMode(handlerName: String): Boolean =
    handlerName in setOf("read_file", "list_files", "find_files", "web_search", "session_search", "skill_view")

data class ConfirmationRequest(
    val toolName: String,
    val reason: String,
    val details: String,
    val riskLevel: RiskLevel,
    val toolCallId: String? = null
)

/**
 * Host-gated clarification: the model asks the user a question mid-turn and waits for a free-text
 * answer. Non-destructive — never triggers the approval flow. A null answer means the user
 * dismissed the question; the model must proceed with its best assumption or state what is missing.
 */
data class ClarificationRequest(
    val toolCallId: String,
    val question: String,
    val options: List<String> = emptyList()
)

/**
 * Tool definition for AI prompts.
 */
internal fun applyHostExecutionContext(
    handlerName: String,
    arguments: Map<String, Any?>,
    workspacePath: String?
): MutableMap<String, Any?> = arguments.toMutableMap().apply {
    remove("cwd")
    remove("working_dir")
    if (handlerName == "run_shell" && !workspacePath.isNullOrBlank()) put("working_dir", workspacePath)
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
)

/**
 * Parameter definition for a tool.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    // FIX 1.7: Removed `default` field — no AI provider serializes it in tool definitions.
    // It was misleading dead data. Re-add only if a provider explicitly supports it.
    val enum: List<String>? = null,
    val items: String? = null  // For array types: item type (e.g., "string")
)

// FIX 4.3: Shared extension to convert ToolDefinition → AiToolDefinition.
// Eliminates identical mapping code duplicated in AiRepository.buildToolDefinitions()
// and SubagentRunner.runInternal(). Single source of truth for this conversion.
//
// FIX: OpenAI rejects tool names that don't match ^[a-zA-Z0-9_-]+$.
// Bridge tool names use dots (e.g. "screen.capture", "mouse.click") which are invalid.
// sanitizeBridgeToolName() replaces '.' with '__' before sending to the API.
// desanitizeBridgeToolName() reverses this when routing the model's response back.
fun sanitizeBridgeToolName(name: String): String = name.replace('.', '_').replace('-', '_')
fun desanitizeBridgeToolName(name: String): String = name // wire name is already the canonical form

// Build a reverse lookup: sanitized → original wire name.
// Used by McpToolExecutor to map the model's response back to the bridge wire name.
private val bridgeToolSanitizedToWire: Map<String, String> by lazy {
    com.amaya.intelligence.impl.bridge.windows.tools.WindowsBridgeToolDefinitions.all
        .associate { sanitizeBridgeToolName(it.name) to it.name }
}

/** Reverse a sanitized bridge tool name back to its wire name, or return [sanitized] unchanged. */
fun resolveBridgeToolWireName(sanitized: String): String =
    bridgeToolSanitizedToWire[sanitized] ?: sanitized

internal const val MAX_TOOL_DESCRIPTION_CHARS = 1023

internal fun truncateToolDescription(description: String): String =
    if (description.length > MAX_TOOL_DESCRIPTION_CHARS) description.take(MAX_TOOL_DESCRIPTION_CHARS) + "…" else description

fun ToolDefinition.toAiToolDefinition(truncateDesc: Boolean = false): com.amaya.intelligence.data.remote.api.AiToolDefinition {
    fun String.maybeTruncate() = if (truncateDesc) truncateToolDescription(this) else this
    // Sanitize the name: OpenAI only accepts ^[a-zA-Z0-9_-]+$ — dots are not allowed.
    // Bridge tool names (e.g. "screen.capture") are sanitized here and reversed in McpToolExecutor.
    val safeName = sanitizeBridgeToolName(name)
    return com.amaya.intelligence.data.remote.api.AiToolDefinition(
        name = safeName,
        description = description.maybeTruncate(),
        parameters = com.amaya.intelligence.data.remote.api.AiToolParameters(
            type = "object",
            properties = parameters.associate { param ->
                param.name to com.amaya.intelligence.data.remote.api.AiToolProperty(
                    type = param.type,
                    description = param.description.maybeTruncate(),
                    enum = param.enum,
                    items = param.items?.let { com.amaya.intelligence.data.remote.api.AiToolPropertyItems(it) }
                )
            },
            required = parameters.filter { it.required }.map { it.name },
            additionalProperties = false
        ),
        strict = false
    )
}
