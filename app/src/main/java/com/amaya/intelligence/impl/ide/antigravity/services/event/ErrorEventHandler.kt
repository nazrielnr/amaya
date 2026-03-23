package com.amaya.intelligence.impl.ide.antigravity.services.event

import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.impl.ide.antigravity.client.*
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles error events and recovery logic for Antigravity.
 */
class ErrorEventHandler(
    private val scope: CoroutineScope,
    private val client: RemoteSessionClient,
    private val stateManager: StreamingStateManager,
    private val onUiStateUpdate: ((ChatUiState) -> ChatUiState) -> Unit
) {
    @Volatile private var lastRecoveryAtMs: Long = 0L
    
    fun handleError(event: RemoteEvent.Error, currentConversationId: String?): Boolean {
        onUiStateUpdate { state -> state.copy(error = event.message, isLoading = false, isStreaming = false) }
        stateManager.clearAll()
        scheduleRecoveryIfNeeded(event.message)
        return true
    }
    
    private fun scheduleRecoveryIfNeeded(message: String) {
        val lower = message.lowercase()
        val shouldRecover =
            lower.contains("socket hang up") ||
            lower.contains("aborted") ||
            lower.contains("too many stalls") ||
            lower.contains("connection unstable")

        if (!shouldRecover) return

        val now = System.currentTimeMillis()
        if (now - lastRecoveryAtMs < 3000L) return
        lastRecoveryAtMs = now
        val shouldResetSeq = lower.contains("too many stalls")

        scope.launch {
            delay(250)
            client.forceResync(resetSequence = shouldResetSeq)
        }
    }
}
