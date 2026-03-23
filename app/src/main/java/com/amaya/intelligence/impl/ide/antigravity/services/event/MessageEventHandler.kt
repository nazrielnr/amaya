package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.services.mapper.AntigravityMessageMapper
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager

/**
 * Handles message events (user message, new assistant message) from Antigravity.
 */
class MessageEventHandler(
    private val stateManager: StreamingStateManager,
    private val onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit
) {
    fun handleNewAssistantMessage(event: RemoteEvent.NewAssistantMessage, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        onUiStateUpdate { state ->
            state.copy(messages = startNewAssistantMessage(state.messages))
        }
        return true
    }
    
    fun handleUserMessage(event: RemoteEvent.UserMessage, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        onUiStateUpdate { state ->
            val normalizedIncoming = AntigravityMessageMapper.normalizeUserText(event.content)
            val hasRecentDuplicate = state.messages
                .takeLast(8)
                .any { it.role == MessageRole.USER && AntigravityMessageMapper.normalizeUserText(it.content) == normalizedIncoming }

            if (hasRecentDuplicate) {
                state.copy(isLoading = false)
            } else {
                val msgs = state.messages.toMutableList()
                val lastMsg = msgs.lastOrNull()
                val hasEmptyAssistantPlaceholder = lastMsg?.role == MessageRole.ASSISTANT &&
                    lastMsg.content.isBlank() &&
                    lastMsg.thinking.isNullOrBlank() &&
                    lastMsg.steps.isEmpty()

                val userMsg = UiMessage(
                    role = MessageRole.USER, 
                    content = event.content,
                    attachments = event.attachments
                )

                if (hasEmptyAssistantPlaceholder) {
                    msgs.add((msgs.size - 1).coerceAtLeast(0), userMsg)
                } else {
                    msgs.add(userMsg)
                }
                state.copy(messages = msgs, isLoading = false)
            }
        }
        return true
    }
    
    private fun isForActiveConversation(eventConversationId: String?, currentConversationId: String?): Boolean {
        if (eventConversationId.isNullOrBlank()) return true
        if (currentConversationId.isNullOrBlank()) return true
        return eventConversationId == currentConversationId
    }
    
    private fun startNewAssistantMessage(messages: List<UiMessage>): List<UiMessage> {
        val lastMsg = messages.lastOrNull()
        val hasAssistantPlaceholder = lastMsg?.role == MessageRole.ASSISTANT &&
            lastMsg.content.isBlank() &&
            lastMsg.thinking.isNullOrBlank() &&
            lastMsg.steps.isEmpty()

        stateManager.clearAll()

        if (hasAssistantPlaceholder) {
            val updated = messages.toMutableList()
            val idx = updated.indexOfLast { it.role == MessageRole.ASSISTANT }
            if (idx != -1) {
                updated[idx] = lastMsg.copy(isThinking = false)
            }
            return updated
        }

        return messages + UiMessage(role = MessageRole.ASSISTANT, content = "")
    }
}
