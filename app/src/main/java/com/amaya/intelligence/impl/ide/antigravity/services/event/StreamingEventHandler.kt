package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.services.mapper.AntigravityMessageMapper
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager

/**
 * Handles streaming events (text delta, AI thinking, stream done) from Antigravity.
 */
class StreamingEventHandler(
    private val stateManager: StreamingStateManager,
    private val onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit
) {
    fun handleTextDelta(event: RemoteEvent.TextDelta, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        
        onUiStateUpdate { state ->
            val lastAssistant = state.messages.lastOrNull()
            if (lastAssistant?.role == MessageRole.ASSISTANT &&
                lastAssistant.content.isBlank() &&
                hasSyntheticThinkingTool(lastAssistant)
            ) {
                val updatedMsgs = finalizeRunningThinkingOnLastAssistant(state.messages)
                val withNewBubble = ensureAssistantMessage(updatedMsgs, force = true)
                val textContent = if (event.stepIndex != null) event.text else stateManager.mergeStreamingSegment(event.text)
                return@onUiStateUpdate state.copy(
                    messages = updateLastAssistantContent(withNewBubble, textContent, null, false, null, event.stepIndex)
                )
            }
            
            val msgs = ensureAssistantMessage(state.messages, force = false)
            stateManager.setPhase(StreamingStateManager.StreamPhase.TEXT)
            val textContent = if (event.stepIndex != null) event.text else stateManager.mergeStreamingSegment(event.text)
            state.copy(messages = updateLastAssistantContent(msgs, textContent, null, false, null, event.stepIndex))
        }
        return true
    }
    
    fun handleAiThinking(event: RemoteEvent.AiThinking, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        
        stateManager.setPhase(StreamingStateManager.StreamPhase.TOOL)
        
        if (event.stepIndex.isNotBlank() && event.stepIndex != stateManager.currentStepIndex) {
            stateManager.clearThinking()
            stateManager.setStepIndex(event.stepIndex)
        }

        if (event.text.isNotBlank()) {
            stateManager.setThinking(event.text)
        }
        
        onUiStateUpdate { state ->
            state.copy(messages = upsertThinkingToolOnLastAssistant(
                state.messages, 
                stateManager.currentThinking, 
                event.isRunning, 
                stateManager.currentStepIndex ?: ""
            ))
        }
        return true
    }
    
    fun handleStreamDone(event: RemoteEvent.StreamDone, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        onUiStateUpdate { state ->
            val finalizedMsgs = finalizeRunningThinkingOnLastAssistant(state.messages)
            state.copy(isStreaming = false, isLoading = false, messages = finalizedMsgs)
        }
        stateManager.clearAll()
        return true
    }
    
    private fun isForActiveConversation(eventConversationId: String?, currentConversationId: String?): Boolean {
        if (eventConversationId.isNullOrBlank()) return true
        if (currentConversationId.isNullOrBlank()) return true
        return eventConversationId == currentConversationId
    }
    
    private fun hasSyntheticThinkingTool(message: UiMessage): Boolean {
        return message.toolExecutions.any {
            it.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                it.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)
        }
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
    
    private fun ensureAssistantMessage(messages: List<UiMessage>, force: Boolean): List<UiMessage> {
        val lastMsg = messages.lastOrNull()
        if (lastMsg?.role == MessageRole.ASSISTANT) return messages
        
        val hasAssistantPlaceholder = lastMsg?.role == MessageRole.ASSISTANT &&
            lastMsg.content.isBlank() &&
            lastMsg.thinking.isNullOrBlank() &&
            lastMsg.steps.isEmpty()

        if (!force && hasAssistantPlaceholder) return messages
        
        return messages + UiMessage(role = MessageRole.ASSISTANT, content = "")
    }
    
    private fun updateLastAssistantContent(
        messages: List<UiMessage>,
        content: String,
        thinking: String?,
        isThinking: Boolean,
        thinkingStartedAt: Long?,
        stepIndex: String? = null
    ): List<UiMessage> {
        val idx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (idx == -1) return messages
        
        val updated = messages.toMutableList()
        val msg = updated[idx]

        val updatedSteps = msg.steps.toMutableList()
        val targetId = if (stepIndex != null) "text-$stepIndex" else "text-legacy"
        val lastTextIdx = if (stepIndex != null) {
            updatedSteps.indexOfLast { it is MessageStep.Text && it.id == targetId }
        } else {
            updatedSteps.indexOfLast { it is MessageStep.Text }
        }

        if (lastTextIdx >= 0) {
            val lastText = updatedSteps[lastTextIdx] as MessageStep.Text
            updatedSteps[lastTextIdx] = lastText.copy(content = content)
        } else {
            // Retrieve default UUID logic but override if we have explicit id
            val newText = MessageStep.Text(content = content).let { if (stepIndex != null) it.copy(id = targetId) else it }
            updatedSteps.add(newText)
        }

        updated[idx] = msg.copy(
            content = content,
            thinking = thinking,
            isThinking = isThinking,
            thinkingStartedAt = thinkingStartedAt,
            steps = updatedSteps
        )
        return updated
    }
    
    private fun upsertThinkingToolOnLastAssistant(
        messages: List<UiMessage>,
        thinkingText: String,
        isRunning: Boolean,
        stepIndex: String
    ): List<UiMessage> {
        val sanitized = thinkingText.trim()
        if (sanitized.isEmpty()) return messages

        val searchId = if (stepIndex.isNotBlank()) "thinking-$stepIndex" else null

        // Search backwards for the most appropriate thinking tool to update in-place
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (msg.role != MessageRole.ASSISTANT) continue

            val existingIdx = msg.toolExecutions.indexOfLast {
                (searchId != null && it.toolCallId == searchId) ||
                    (searchId == null && it.metadata[StreamingStateManager.THINKING_TOOL_META_KEY] == "true" && it.status == ToolStatus.RUNNING)
            }

            if (existingIdx >= 0) {
                val updatedTools = msg.toolExecutions.toMutableList()
                val thoughtTitle = AntigravityMessageMapper.extractThoughtTitle(sanitized)
                val updatedMeta = updatedTools[existingIdx].metadata.toMutableMap()
                if (thoughtTitle != null) {
                    updatedMeta["thoughtTitle"] = thoughtTitle
                }
                
                val updatedUiMeta = updatedTools[existingIdx].uiMetadata?.copy(
                    label = thoughtTitle ?: updatedTools[existingIdx].uiMetadata?.label ?: "Thinking"
                )
                
                val updatedTool = updatedTools[existingIdx].copy(
                    result = sanitized,
                    status = if (isRunning) ToolStatus.RUNNING else ToolStatus.SUCCESS,
                    metadata = updatedMeta,
                    uiMetadata = updatedUiMeta
                )
                updatedTools[existingIdx] = updatedTool
                
                val updatedSteps = msg.steps.map { step ->
                    if (step is MessageStep.ToolCall && step.execution.toolCallId == updatedTool.toolCallId) {
                        step.copy(execution = updatedTool)
                    } else step
                }
                
                return messages.toMutableList().apply { this[i] = msg.copy(toolExecutions = updatedTools, steps = updatedSteps) }
            }
        }

        // Not found, add to last assistant message
        val lastAssistantIdx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (lastAssistantIdx >= 0) {
            val msg = messages[lastAssistantIdx]
            val updatedTools = msg.toolExecutions.toMutableList()
            val thinkingToolId = searchId ?: "thinking:${msg.id}:${System.currentTimeMillis()}"
            
            val thoughtTitle = AntigravityMessageMapper.extractThoughtTitle(sanitized)
            val meta = mutableMapOf(
                AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY to "true",
                "source" to "remote"
            )
            if (thoughtTitle != null) {
                meta["thoughtTitle"] = thoughtTitle
            }

            val newTool = ToolExecution(
                toolCallId = thinkingToolId,
                name = StreamingStateManager.THINKING_TOOL_NAME,
                arguments = mapOf("source" to "ai_thinking_stream"),
                result = sanitized,
                status = if (isRunning) ToolStatus.RUNNING else ToolStatus.SUCCESS,
                metadata = meta,
                uiMetadata = ToolUiMetadata(
                    category = ToolCategory.TASK_MANAGEMENT,
                    label = thoughtTitle ?: "Thinking",
                    actionIcon = ToolInfoIcon.BRAIN,
                    targetIcon = ToolInfoIcon.GENERATE,
                    badges = listOf("THINKING")
                )
            )
            updatedTools.add(newTool)
            val updatedSteps = msg.steps + MessageStep.ToolCall(execution = newTool)
            
            return messages.toMutableList().apply { this[lastAssistantIdx] = msg.copy(toolExecutions = updatedTools, steps = updatedSteps) }
        }

        return messages
    }
}
