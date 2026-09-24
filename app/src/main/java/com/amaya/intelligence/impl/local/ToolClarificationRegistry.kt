package com.amaya.intelligence.impl.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Suspends an in-flight `ask_user` tool call until the user answers (free text) or dismisses it.
 * Mirrors [ToolConfirmationRegistry] but resolves with a String instead of a Boolean.
 */
internal class ToolClarificationRegistry {
    private data class Pending(val turnId: Long, val answer: CompletableDeferred<String?>)

    private val lock = Any()
    private val pending = mutableMapOf<String, Pending>()

    suspend fun await(callId: String, turnId: Long, onRegistered: () -> Unit): String? {
        if (callId.isBlank()) return null
        val entry = Pending(turnId, CompletableDeferred())
        synchronized(lock) {
            if (pending.putIfAbsent(callId, entry) != null) return null
        }
        return try {
            onRegistered()
            entry.answer.await()
        } finally {
            synchronized(lock) { pending.remove(callId, entry) }
        }
    }

    fun resolve(callId: String, turnId: Long, answer: String?, onResolved: () -> Unit): Boolean =
        synchronized(lock) {
            val entry = pending[callId] ?: return@synchronized false
            if (entry.turnId != turnId || !entry.answer.isActive) return@synchronized false
            pending.remove(callId, entry)
            onResolved()
            entry.answer.complete(answer)
        }

    fun cancel(turnId: Long) {
        synchronized(lock) {
            val cancellation = CancellationException("Tool clarification cancelled")
            pending.entries.removeIf { (_, value) ->
                if (value.turnId == turnId) {
                    value.answer.cancel(cancellation)
                    true
                } else false
            }
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            val cancellation = CancellationException("Tool clarification cancelled")
            pending.values.forEach { it.answer.cancel(cancellation) }
            pending.clear()
        }
    }

    internal fun size(): Int = synchronized(lock) { pending.size }
}
