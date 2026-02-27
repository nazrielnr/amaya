package com.amaya.intelligence.data.remote.mcp

import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.ToolExecutor
import com.amaya.intelligence.tools.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolExecutor @Inject constructor(
    private val toolExecutor: ToolExecutor,
    private val mcpClientManager: McpClientManager
) {
    suspend fun execute(
        toolName: String,
        arguments: Map<String, Any?>,
        workspacePath: String?,
        onConfirmationRequired: suspend (ConfirmationRequest) -> Boolean
    ): ToolResult {
        return if (toolName.startsWith(McpClientManager.TOOL_PREFIX)) {
            mcpClientManager.callTool(toolName, arguments)
        } else {
            toolExecutor.execute(toolName, arguments, workspacePath, onConfirmationRequired)
        }
    }
}
