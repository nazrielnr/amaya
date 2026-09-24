package com.amaya.intelligence.impl.local


import com.amaya.intelligence.data.remote.api.MessageRole

import com.amaya.intelligence.data.repository.AgentEvent

import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.util.LocalStreamPerfLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import com.amaya.intelligence.util.StreamDebugLog


internal suspend fun LocalIntelligenceService.startTurn(
        content: String,
        images: List<com.amaya.intelligence.data.remote.api.ChatImage>,
        initialState: ChatUiState,
        projectVisible: Boolean,
        preexistingUserMessage: Boolean = false,
        internalContinuation: Boolean = false
    ): Boolean {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank() && images.isEmpty()) return false
        val selectedEpoch = targetEpoch.get()
        val activeConversation = initialState.conversationId?.toLongOrNull()
        val turnId = nextTurnId.incrementAndGet()
        if (activeConversation != null) {
            activeTurns[activeConversation]?.let { running ->
                if (projectVisible) {
                    running.pendingMessage = LocalIntelligenceService.PendingMessage(content, images)
                    stoppingConversations.add(activeConversation)
                    pendingToolConfirmations.cancel(running.turnId)
                    running.job?.cancel()
                    _uiState.update { it.copy(error = null) }
                }
                return false
            }
            if (startingConversations.putIfAbsent(activeConversation, turnId) != null) {
                if (projectVisible) _uiState.update { it.copy(error = "This session is already streaming") }
                return false
            }
        } else if (!startingNewTurnId.compareAndSet(0L, turnId)) {
            if (projectVisible) _uiState.update { it.copy(error = "This session is already streaming") }
            return false
        }
        val userMsg = UiMessage(
            role = if (internalContinuation) MessageRole.SYSTEM else MessageRole.USER,
            content = trimmedContent,
            attachments = images.map { MessageAttachment(it.mediaType, it.base64, it.fileName) },
            metadata = if (internalContinuation) mapOf("internalContinuation" to "true") else emptyMap()
        )
        val turnState = initialState.copy(
            messages = if (preexistingUserMessage || internalContinuation) initialState.messages else initialState.messages + userMsg,
            contextMessages = if (preexistingUserMessage || internalContinuation) initialState.contextMessages else initialState.contextMessages + userMsg,
            isLoading = true,
            isStreaming = true,
            error = null
        )
        if (projectVisible) _uiState.value = turnState
        val isNewConversation = activeConversation == null
        val conversationId = try {
            conversationStartMutex.withLock {
                // A submitted prompt owns its captured target even if the user navigates before Room returns.
                persistConversationStart(turnState, activeConversation)
            }
        } catch (error: Exception) {
            if (projectVisible && targetEpoch.get() == selectedEpoch) {
                _uiState.update { it.copy(error = "Could not save this conversation: ${error.message.orEmpty()}", isLoading = false, isStreaming = false) }
            }
            null
        }
        val ownsStart = activeConversation?.let { startingConversations[it] == turnId }
            ?: (startingNewTurnId.get() == turnId)
        if (conversationId == null || !ownsStart) {
            activeConversation?.let { startingConversations.remove(it, turnId) }
                ?: startingNewTurnId.compareAndSet(turnId, 0L)
            return false
        }
        val runtimeState = turnState.copy(conversationId = conversationId.toString())
        if (projectVisible && targetEpoch.get() == selectedEpoch) {
            currentConversationId = conversationId
            if (runtimeState.assistantMode == AssistantMode.AGENT && runtimeState.agentId != null) {
                browserSessionManager.selectConversation("conversation:$conversationId", runtimeState.agentId)
                browserSessionManager.onAssistantStreamingChanged(true)
            }
            _uiState.value = runtimeState
        }
        val notificationIdentity = notificationIdentity(runtimeState)
        StreamDebugLog.event(conversationId, turnId, "TURN_START", "mode=${runtimeState.assistantMode} agent=${runtimeState.agentId ?: ""} promptChars=${trimmedContent.length}")
        val turn = LocalIntelligenceService.LocalTurn(
            turnId,
            conversationId,
            trimmedContent,
            isNewConversation,
            runtimeState,
            notificationIdentity.title,
            notificationIdentity.sender,
            notificationIdentity.threadKey
        )
        LocalStreamPerfLog.startTurn(
            messageChars = trimmedContent.length,
            historyMessages = runtimeState.contextMessages.size,
            model = runtimeState.selectedModel
        )
        val job = scope.launch(start = CoroutineStart.LAZY) {
                var completed = false
                var failed = false
                try {
                    aiRepository.chat(
                        message = trimmedContent,
                        userImages = images,
                        conversationHistory = (if (internalContinuation) runtimeState.contextMessages else runtimeState.contextMessages.dropLast(1))
                            .flatMap { it.toChatMessages() },
                        workspacePath = runtimeState.workspacePath,
                        assistantMode = runtimeState.assistantMode,
                        ownerId = runtimeState.ownerId,
                        agentId = runtimeState.agentId,
                        connectionId = runtimeState.activeModelKey.takeIf { it.startsWith("model|") }?.split('|', limit = 3)?.getOrNull(1),
                        conversationId = conversationId,
                        selectedModel = runtimeState.selectedModel,
                        effort = runtimeState.effort,
                        onConfirmation = { request -> awaitInlineToolConfirmation(request, turnId) },
                        onClarification = { request -> awaitInlineToolClarification(request, turnId) },
                        // Delegation completions never interrupt an active provider/tool loop. They
                        // are materialized together only by the hidden continuation.
                        pendingConversationEvents = { if (internalContinuation) drainQueuedConversationEvents(conversationId) else emptyList() },
                        messageRole = if (internalContinuation) MessageRole.SYSTEM else MessageRole.USER,
                        onConversationEventsInjected = { events ->
                            val injected = events.filter { it.conversationEvent() != null }
                            if (injected.isNotEmpty()) {
                                turn.state = turn.state.copy(
                                    messages = turn.state.messages + injected,
                                    contextMessages = turn.state.contextMessages + injected
                                )
                                persistTurn(turn)
                                acknowledgeConversationEvents(conversationId, injected)
                                publishTurn(turn, turn.lastStatus, turn.lastDetail, urgent = true)
                            }
                        }
                    ).collect { event ->
                        completed = completed || event is AgentEvent.Done
                        failed = failed || event is AgentEvent.Error || event is AgentEvent.Incomplete
                        StreamDebugLog.event(conversationId, turnId, "EVENT", event::class.simpleName.orEmpty())
                        handleTurnEvent(turn, event)
                    }
                    if (isNewConversation && completed) launchTitleGeneration(trimmedContent, conversationId)
                    if (!completed && !failed) {
                        failed = true
                        handleTurnEvent(turn, AgentEvent.Incomplete("Provider stream ended unexpectedly", retryable = true))
                    }
                } catch (cancelled: CancellationException) {
                    handleTurnEvent(turn, AgentEvent.Incomplete("Stopped", retryable = true))
                    // Stop is an intentional terminal state, not a retryable provider failure.
                    throw cancelled
                } catch (error: Exception) {
                    failed = true
                    handleTurnEvent(turn, AgentEvent.Error(error.message.orEmpty().ifBlank { "AI session failed" }, retryable = true))
                } finally {
                    withContext(NonCancellable) {
                        turn.state = turn.state.copy(isLoading = false, isStreaming = false, isAutoCompacting = false)
                        try {
                            persistTurn(turn)
                            StreamDebugLog.event(conversationId, turnId, "PERSISTED", "messages=${turn.state.messages.size} context=${turn.state.contextMessages.size}")
                        } finally {
                            StreamDebugLog.event(conversationId, turnId, "TURN_FINALLY", "completed=$completed failed=$failed")
                            activeTurns.remove(conversationId, turn)
                            turnsById.remove(turn.turnId, turn)
                            removeRunningSession(conversationId)
                            val pendingMessage = if (stoppingConversations.remove(conversationId)) turn.pendingMessage else null
                            val isVisible = currentConversationId == conversationId
                            if (isVisible) {
                                browserSessionManager.onAssistantStreamingChanged(false)
                                // Carry the trimmed model context back into the UI state: the next
                                // turn seeds itself from here, and without this it would write the
                                // untrimmed list straight back over what persistTurn just stored.
                                _uiState.update {
                                    it.copy(
                                        contextMessages = turn.state.contextMessages,
                                        isLoading = false,
                                        isStreaming = false,
                                        isAutoCompacting = false
                                    )
                                }
                            }
                            if (completed && hasQueuedConversationEvents(conversationId)) {
                                scheduleContinuationAfterConversationEvent(conversationId)
                            } else pendingMessage?.let { pending ->
                                scope.launch {
                                    startTurn(
                                        content = pending.content,
                                        images = pending.images,
                                        initialState = turn.state,
                                        projectVisible = isVisible
                                    )
                                }
                            }
                        }
                    }
                }
            }
        turn.job = job
        activeTurns[conversationId] = turn
        activeConversation?.let { startingConversations.remove(it, turnId) }
            ?: startingNewTurnId.compareAndSet(turnId, 0L)
        turnsById[turnId] = turn
        com.amaya.intelligence.service.AiSessionNotificationService.start(appContext)
        publishTurn(turn, "Streaming", "Waiting for response")
        job.start()
        return true
    }

