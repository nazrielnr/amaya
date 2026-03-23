package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.tools.AntigravityToolMapper
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager

/**
 * Handles tool call events (start, result) from Antigravity.
 */
class ToolCallEventHandler(
    private val stateManager: StreamingStateManager,
    private val onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit
) {
    fun handleToolCallStart(event: RemoteEvent.ToolCallStart, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        
        val normalizedName = AntigravityToolMapper.mapToolName(event.name)
        val normalizedArgs = AntigravityToolMapper.mapToolArgs(event.name, event.arguments)
        val uiMeta = AntigravityToolMapper.getUiMetadata(event.name, event.arguments)
        
        val status = when (event.status.uppercase()) {
            "PENDING", "WAITING", "STANDBY" -> ToolStatus.PENDING
            "RUNNING" -> ToolStatus.RUNNING
            "SUCCESS" -> ToolStatus.SUCCESS
            "ERROR"   -> ToolStatus.ERROR
            else      -> ToolStatus.RUNNING
        }

        val toolExec = ToolExecution(
            toolCallId = event.toolCallId,
            name = normalizedName,
            arguments = normalizedArgs,
            status = status,
            metadata = mapOf(
                "source" to "remote",
                "animateOnMount" to "true"
            ),
            uiMetadata = uiMeta
        )
        
        onUiStateUpdate { state ->
            val finalizedMsgs = finalizeRunningThinkingOnLastAssistant(state.messages)
            val updatedMsgs = updateToolInMessages(finalizedMsgs, event.toolCallId) { tool ->
                tool.copy(
                    name = normalizedName,
                    arguments = normalizedArgs,
                    status = status,
                    metadata = tool.metadata + ("source" to "remote"),
                    uiMetadata = uiMeta
                )
            } ?: run {
                val msgs = ensureAssistantMessage(finalizedMsgs, force = false)
                updateLastAssistantMessage(msgs) { msg ->
                    msg.copy(
                        toolExecutions = msg.toolExecutions + toolExec,
                        isThinking = false,
                        steps = msg.steps + MessageStep.ToolCall(execution = toolExec)
                    )
                }
            }
            state.copy(messages = updatedMsgs)
        }
        return true
    }

    fun handleToolActivity(event: RemoteEvent.ToolActivity, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        if (!event.type.equals("terminal", ignoreCase = true)) return false
        val chunk = event.terminalData
        if (chunk.isBlank()) return true

        onUiStateUpdate { state ->
            val updated = appendTerminalChunkToLatestRunningShellTool(state.messages, chunk)
            state.copy(messages = updated)
        }
        return true
    }
    
    fun handleToolCallResult(event: RemoteEvent.ToolCallResult, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        val extractedResult = AntigravityToolMapper.extractToolResult(event.result)
        
        onUiStateUpdate { state ->
            val updatedMsgs = updateToolInMessages(state.messages, event.toolCallId) { tool ->
                val isPlaceholder = extractedResult.trim().equals("done", ignoreCase = true) ||
                    extractedResult.trim().equals("success", ignoreCase = true)
                val hasStreamedOutput = !tool.result.isNullOrBlank()

                tool.copy(
                    result = if (tool.name.equals("run_shell", ignoreCase = true) && hasStreamedOutput && isPlaceholder) {
                        tool.result
                    } else {
                        extractedResult
                    },
                    status = if (event.isError) ToolStatus.ERROR else ToolStatus.SUCCESS
                )
            } ?: state.messages
            state.copy(messages = updatedMsgs)
        }
        return true
    }
    
    private fun isForActiveConversation(eventConversationId: String?, currentConversationId: String?): Boolean {
        if (eventConversationId.isNullOrBlank()) return true
        if (currentConversationId.isNullOrBlank()) return true
        return eventConversationId == currentConversationId
    }
    
    private fun finalizeRunningThinkingOnLastAssistant(messages: List<UiMessage>): List<UiMessage> {
        val idx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (idx == -1) return messages
        
        val msg = messages[idx]
        if (!hasSyntheticThinkingTool(msg)) return messages
        
        val updatedTools = msg.toolExecutions.map { tool ->
            if ((tool.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                    tool.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)) &&
                tool.status == ToolStatus.RUNNING
            ) {
                tool.copy(status = ToolStatus.SUCCESS)
            } else {
                tool
            }
        }
        
        val updatedSteps = msg.steps.map { step ->
            if (step is MessageStep.ToolCall && 
                (step.execution.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                 step.execution.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)) &&
                step.execution.status == ToolStatus.RUNNING
            ) {
                step.copy(execution = step.execution.copy(status = ToolStatus.SUCCESS))
            } else step
        }
        return messages.toMutableList().apply { this[idx] = msg.copy(toolExecutions = updatedTools, steps = updatedSteps) }
    }
    
    private fun hasSyntheticThinkingTool(message: UiMessage): Boolean {
        return message.toolExecutions.any {
            it.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                it.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)
        }
    }
    
    private fun ensureAssistantMessage(messages: List<UiMessage>, force: Boolean): List<UiMessage> {
        val lastMsg = messages.lastOrNull()

        // Check for an existing empty assistant placeholder before the early-return,
        // otherwise this check would be dead code (role == ASSISTANT already handled above).
        val hasAssistantPlaceholder = lastMsg?.role == MessageRole.ASSISTANT &&
            lastMsg.content.isBlank() &&
            lastMsg.thinking.isNullOrBlank() &&
            lastMsg.steps.isEmpty()

        if (lastMsg?.role == MessageRole.ASSISTANT) return messages
        if (!force && hasAssistantPlaceholder) return messages

        return messages + UiMessage(role = MessageRole.ASSISTANT, content = "")
    }
    
    private fun updateLastAssistantMessage(
        messages: List<UiMessage>,
        update: (UiMessage) -> UiMessage
    ): List<UiMessage> {
        val idx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (idx == -1) return messages
        
        val updated = messages.toMutableList()
        updated[idx] = update(updated[idx])
        return updated
    }
    
    private fun updateToolInMessages(
        messages: List<UiMessage>,
        toolCallId: String,
        update: (ToolExecution) -> ToolExecution
    ): List<UiMessage>? {
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            val toolIdx = msg.toolExecutions.indexOfFirst { it.toolCallId == toolCallId }
            if (toolIdx != -1) {
                val updatedTools = msg.toolExecutions.toMutableList()
                val updatedTool = update(updatedTools[toolIdx])
                updatedTools[toolIdx] = updatedTool
                
                val updatedSteps = msg.steps.map { step ->
                    if (step is MessageStep.ToolCall && step.execution.toolCallId == toolCallId) {
                        step.copy(execution = updatedTool)
                    } else step
                }
                
                return messages.toMutableList().apply { this[i] = msg.copy(toolExecutions = updatedTools, steps = updatedSteps) }
            }
        }
        return null
    }

    private fun appendTerminalChunkToLatestRunningShellTool(messages: List<UiMessage>, chunk: String): List<UiMessage> {
        val sanitizedChunk = chunk
        // Walk backwards so we update the latest relevant tool execution.
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (msg.role != MessageRole.ASSISTANT) continue

            val toolIdx = msg.toolExecutions.indexOfLast {
                it.name.equals("run_shell", ignoreCase = true) &&
                    (it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING)
            }
            if (toolIdx == -1) continue

            val tools = msg.toolExecutions.toMutableList()
            val existing = tools[toolIdx].result ?: ""
            val merged = if (existing.isBlank()) sanitizedChunk else (existing + sanitizedChunk)
            val updatedTool = tools[toolIdx].copy(result = merged)
            tools[toolIdx] = updatedTool
            
            val updatedSteps = msg.steps.map { step ->
                if (step is MessageStep.ToolCall && step.execution.toolCallId == updatedTool.toolCallId) {
                    step.copy(execution = updatedTool)
                } else step
            }
            
            val updatedMsg = msg.copy(toolExecutions = tools, steps = updatedSteps)
            return messages.toMutableList().apply { this[i] = updatedMsg }
        }
        return messages
    }
}
