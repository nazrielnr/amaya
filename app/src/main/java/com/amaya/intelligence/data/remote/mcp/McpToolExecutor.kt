package com.amaya.intelligence.data.remote.mcp

import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.impl.bridge.windows.tools.WindowsBridgeToolProvider
import com.amaya.intelligence.tools.ClarificationRequest
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.ToolExecutor
import com.amaya.intelligence.tools.ToolResult
import com.amaya.intelligence.tools.resolveBridgeToolWireName
import com.amaya.intelligence.tools.sanitizeModelArguments
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolExecutor @Inject constructor(
    private val toolExecutor: ToolExecutor,
    private val mcpClientManager: McpClientManager,
    private val windowsBridgeToolProvider: WindowsBridgeToolProvider,
    private val terminalSettingsRepository: TerminalSettingsRepository
) {
    suspend fun execute(
        toolName: String,
        arguments: Map<String, Any?>,
        workspacePath: String?,
        toolCallId: String? = null,
        onEvent: (suspend (Any) -> Unit)? = null,
        onConfirmationRequired: suspend (ConfirmationRequest) -> Boolean,
        onClarificationRequired: suspend (ClarificationRequest) -> String? = { null },
        providerConnection: com.amaya.intelligence.data.remote.api.ProviderConnection? = null,
        selectedModelId: String? = null,
        conversationId: String? = null,
        ownerId: String? = null,
        agentId: Long? = null,
        assistantMode: AssistantMode = AssistantMode.PROJECT,
        agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile? = null
    ): ToolResult {
        // Reverse-map sanitized bridge tool names (e.g. "screen_capture" → "screen.capture").
        // The model receives sanitized names (dots replaced with underscores) because OpenAI
        // rejects names that don't match ^[a-zA-Z0-9_-]+$. We restore the wire name here
        // before routing so the bridge registry lookup always uses the canonical dot form.
        val modelArguments = arguments.toMutableMap().apply {
            remove("cwd")
            remove("working_dir")
        }
        val safeArguments = sanitizeModelArguments(modelArguments).getOrElse { error ->
            return ToolResult.Error(error.message.orEmpty(), com.amaya.intelligence.tools.ErrorType.VALIDATION_ERROR)
        }
        val wireName = resolveBridgeToolWireName(toolName)

        // FIX 9: Use McpClientManager.TOOL_PREFIX constant — no hardcoded "mcp__" string here
        return when {
            wireName.startsWith(McpClientManager.TOOL_PREFIX) -> {
                val identity = toolCallId ?: return ToolResult.Error(
                    "MCP tool call is missing a call ID; approval cannot be bound safely.",
                    com.amaya.intelligence.tools.ErrorType.VALIDATION_ERROR
                )
                // Terminal policy treats MCP invocations as non-destructive by default (see
                // TerminalSettings.autoApproveNonDestructive), so an enabled auto-approve toggle
                // lets the model call external MCP tools without prompting. The exception is a
                // server that explicitly annotates a tool as destructive (destructiveHint): that
                // call always requires explicit confirmation — auto-approve is never blanket for
                // tools the server itself flags as destructive.
                val annotations = mcpClientManager.getToolAnnotations(wireName)
                val settings = terminalSettingsRepository.getSettings()
                val autoApprove = settings.autoApproveAll || settings.autoApproveNonDestructive
                if (!settings.autoApproveAll && mcpConfirmationRequired(autoApprove, annotations.destructiveHint)) {
                    val approved = onConfirmationRequired(
                        ConfirmationRequest(
                            toolName = wireName,
                            reason = if (annotations.destructiveHint) {
                                "External MCP server marked this tool as potentially destructive"
                            } else {
                                "External MCP servers are untrusted and may access or modify data"
                            },
                            details = safeArguments.toString(),
                            riskLevel = if (annotations.destructiveHint) {
                                com.amaya.intelligence.domain.security.RiskLevel.HIGH
                            } else {
                                com.amaya.intelligence.domain.security.RiskLevel.MEDIUM
                            },
                            toolCallId = identity
                        )
                    )
                    if (!approved) return ToolResult.Error(
                        "User declined external MCP tool call",
                        com.amaya.intelligence.tools.ErrorType.PERMISSION_ERROR
                    )
                }
                mcpClientManager.callTool(wireName, safeArguments)
            }
            windowsBridgeToolProvider.isBridgeTool(wireName) ->
                windowsBridgeToolProvider.executeBridgeTool(wireName, safeArguments)
            else ->
                toolExecutor.execute(
                    wireName,
                    safeArguments,
                    workspacePath,
                    toolCallId,
                    onEvent,
                    onConfirmationRequired,
                    onClarificationRequired,
                    providerConnection,
                    selectedModelId,
                    conversationId = conversationId,
                    ownerId = ownerId,
                    agentId = agentId,
                    assistantMode = assistantMode,
                    agentCapabilityProfile = agentCapabilityProfile
                )
        }
    }
}

/**
 * Whether an external MCP tool call needs explicit user confirmation. The terminal auto-approve
 * toggle ([TerminalSettings.autoApproveNonDestructive]) covers non-destructive MCP invocations,
 * but a server that explicitly annotates a tool as destructive (destructiveHint) always requires
 * confirmation — auto-approve is never blanket for tools the server itself flags as destructive.
 */
internal fun mcpConfirmationRequired(autoApproveNonDestructive: Boolean, destructiveHint: Boolean): Boolean =
    destructiveHint || !autoApproveNonDestructive
