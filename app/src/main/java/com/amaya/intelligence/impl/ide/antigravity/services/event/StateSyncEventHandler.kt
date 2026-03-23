package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.services.mapper.AntigravityMessageMapper
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager

/**
 * Handles state synchronization events from Antigravity.
 */
class StateSyncEventHandler(
    private val stateManager: StreamingStateManager,
    private val onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit
) {
    fun handleStateSync(event: RemoteEvent.StateSync, currentConversationId: String?) {
        val serverConversationId = event.conversationId
        
        if (!isForActiveConversation(serverConversationId, currentConversationId)) return
        
        if (!serverConversationId.isNullOrBlank() && serverConversationId != currentConversationId) {
            stateManager.clearAll()
        }

        val messages = AntigravityMessageMapper.mapRemoteMessages(event.messages, event.isStreaming)
        onUiStateUpdate { state ->
            var finalMessages = if (state.isStreaming && event.isStreaming && messages.isNotEmpty() && state.messages.isNotEmpty()) {
                val lastLocal = state.messages.last()
                val lastIncoming = messages.lastOrNull()
                val canOverlayLocalAssistant = shouldOverlayLocalAssistant(lastLocal, lastIncoming)

                if (canOverlayLocalAssistant) {
                    val trailingAssistantCount = messages.takeLastWhile { it.role == MessageRole.ASSISTANT }.size
                    messages.dropLast(trailingAssistantCount) + lastLocal
                } else messages
            } else messages

            // Keep optimistic user prompt visible while streaming until server echo catches up.
            if (event.isStreaming && state.messages.isNotEmpty()) {
                val localTrailingUsers = state.messages
                    .takeLastWhile { it.role == MessageRole.USER && it.content.isNotBlank() }

                if (localTrailingUsers.isNotEmpty()) {
                    val incomingUsers = finalMessages
                        .asReversed()
                        .filter { it.role == MessageRole.USER }
                        .take(12)
                        .map { AntigravityMessageMapper.normalizeUserText(it.content) }
                        .toSet()

                    val missingUsers = localTrailingUsers.filter {
                        val normalized = AntigravityMessageMapper.normalizeUserText(it.content)
                        normalized.isNotBlank() && normalized !in incomingUsers
                    }

                    if (missingUsers.isNotEmpty()) {
                        val lastAssistant = finalMessages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
                        finalMessages = if (lastAssistant != null) {
                            finalMessages.dropLast(1) + missingUsers + lastAssistant
                        } else {
                            finalMessages + missingUsers
                        }
                    }
                }
            }

            // If the server's state sync messages don't include attachments (common for media),
            // preserve local attachments so image previews don't disappear when idle.
            finalMessages = mergeMissingAttachmentsFromLocal(state.messages, finalMessages)
            finalMessages = preserveLocalAttachmentMessages(state.messages, finalMessages)

            state.copy(
                conversationId = serverConversationId ?: state.conversationId,
                messages = finalMessages,
                isLoading = event.isLoading,
                isStreaming = event.isStreaming,
                selectedModel = event.currentModel.ifBlank { state.selectedModel },
                error = null,
                serverIp = event.serverIp ?: state.serverIp
            )
        }
        if (!event.isStreaming) {
            stateManager.clearAll()
        }
    }
    
    fun handleConversationLoaded(event: RemoteEvent.ConversationLoaded) {
        val messages = AntigravityMessageMapper.mapRemoteMessages(event.messages, isStreaming = false)
        onUiStateUpdate { state ->
            val isSameConversation = state.conversationId == event.conversationId
            var merged = if (isSameConversation) {
                var temp = mergeMissingAttachmentsFromLocal(state.messages, messages)
                temp = preserveLocalUserMessages(state.messages, temp)
                preserveLocalAttachmentMessages(state.messages, temp)
            } else {
                messages
            }

            state.copy(
                conversationId = event.conversationId,
                messages = merged,
                isLoading = false,
                isStreaming = false,
                serverIp = event.serverIp ?: state.serverIp
            )
        }
        stateManager.clearAll()
    }
    
    fun handleStateUpdate(event: RemoteEvent.StateUpdate, currentConversationId: String?): Boolean {
        if (!isForActiveConversation(event.conversationId, currentConversationId)) return false
        onUiStateUpdate { it.copy(
            isLoading = event.isLoading,
            isStreaming = event.isStreaming,
            serverIp = event.serverIp ?: it.serverIp
        ) }
        if (!event.isStreaming) {
            stateManager.clearAll()
        }
        return true
    }
    
    private fun isForActiveConversation(eventConversationId: String?, currentConversationId: String?): Boolean {
        if (eventConversationId.isNullOrBlank()) return true
        if (currentConversationId.isNullOrBlank()) return true
        return eventConversationId == currentConversationId
    }
    
    private fun shouldOverlayLocalAssistant(localLast: UiMessage, incomingLast: UiMessage?): Boolean {
        if (localLast.role != MessageRole.ASSISTANT) return false

        val localHasText = localLast.content.isNotBlank()
        val incomingHasText = incomingLast?.role == MessageRole.ASSISTANT && incomingLast.content.isNotBlank()
        if (localHasText && (!incomingHasText || localLast.content.length > (incomingLast?.content?.length ?: 0))) {
            return true
        }

        val localThinkingRunning = isRunningSyntheticThinking(localLast)
        val incomingThinkingRunning = isRunningSyntheticThinking(incomingLast)
        if (localThinkingRunning) {
            val localThinking = syntheticThinkingText(localLast)
            val incomingThinking = syntheticThinkingText(incomingLast)
            if (!incomingThinkingRunning || localThinking.length > incomingThinking.length) {
                return true
            }
        }

        return false
    }

    private fun mergeMissingAttachmentsFromLocal(
        local: List<UiMessage>,
        incoming: List<UiMessage>
    ): List<UiMessage> {
        if (incoming.isEmpty() || local.isEmpty()) return incoming

        // Map latest local USER message attachments by normalized content
        val localByNormalized = LinkedHashMap<String, UiMessage>()
        local.asReversed().forEach { msg ->
            if (msg.role != MessageRole.USER) return@forEach
            if (msg.attachments.isEmpty()) return@forEach
            val key = AntigravityMessageMapper.normalizeUserText(msg.content)
            if (key.isBlank()) return@forEach
            if (!localByNormalized.containsKey(key)) {
                localByNormalized[key] = msg
            }
        }

        if (localByNormalized.isEmpty()) return incoming

        return incoming.map { msg ->
            if (msg.role != MessageRole.USER) return@map msg
            if (msg.attachments.isNotEmpty()) return@map msg
            val key = AntigravityMessageMapper.normalizeUserText(msg.content)
            val localMatch = localByNormalized[key]
            if (localMatch != null) msg.copy(attachments = localMatch.attachments) else msg
        }
    }

    private fun preserveLocalAttachmentMessages(
        local: List<UiMessage>,
        incoming: List<UiMessage>
    ): List<UiMessage> {
        if (local.isEmpty()) return incoming

        // Build a set of normalized incoming user contents for quick membership checks.
        val incomingUserKeys = incoming
            .filter { it.role == MessageRole.USER }
            .map { AntigravityMessageMapper.normalizeUserText(it.content) }
            .filter { it.isNotBlank() }
            .toSet()

        // Keep local user messages with attachments that are not present in incoming.
        val missingAttachmentUsers = local
            .takeLast(20)
            .filter { it.role == MessageRole.USER && it.attachments.isNotEmpty() }
            .filter {
                val key = AntigravityMessageMapper.normalizeUserText(it.content)
                key.isNotBlank() && key !in incomingUserKeys
            }

        if (missingAttachmentUsers.isEmpty()) return incoming

        val lastAssistant = incoming.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
        return if (lastAssistant != null) {
            incoming.dropLast(1) + missingAttachmentUsers + lastAssistant
        } else {
            incoming + missingAttachmentUsers
        }
    }

    private fun preserveLocalUserMessages(
        local: List<UiMessage>,
        incoming: List<UiMessage>
    ): List<UiMessage> {
        if (local.isEmpty()) return incoming

        val localUsers = local
            .takeLastWhile { it.role == MessageRole.USER && it.content.isNotBlank() }

        if (localUsers.isEmpty()) return incoming

        val incomingUserKeys = incoming
            .filter { it.role == MessageRole.USER }
            .map { AntigravityMessageMapper.normalizeUserText(it.content) }
            .filter { it.isNotBlank() }
            .toSet()

        val missingUsers = localUsers.filter {
            val normalized = AntigravityMessageMapper.normalizeUserText(it.content)
            normalized.isNotBlank() && normalized !in incomingUserKeys
        }

        if (missingUsers.isEmpty()) return incoming

        val lastAssistant = incoming.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
        return if (lastAssistant != null) {
            incoming.dropLast(1) + missingUsers + lastAssistant
        } else {
            incoming + missingUsers
        }
    }
    
    private fun isRunningSyntheticThinking(message: UiMessage?): Boolean {
        if (message == null) return false
        return message.steps.any { step ->
            step is MessageStep.ToolCall &&
            (step.execution.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                step.execution.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)) &&
            step.execution.status == ToolStatus.RUNNING
        }
    }

    private fun syntheticThinkingText(message: UiMessage?): String {
        if (message == null) return ""
        return message.steps.filterIsInstance<MessageStep.ToolCall>().firstOrNull { step ->
            step.execution.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                step.execution.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true)
        }?.execution?.result?.trim().orEmpty()
    }
}
