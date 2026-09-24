package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.ChatResponse
import com.amaya.intelligence.data.remote.api.ConfiguredModel
import com.amaya.intelligence.data.remote.api.ProviderConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

/**
 * Categorization of errors encountered during AI streaming and tool loops.
 */
enum class ErrorCategory {
    /** Empty response or inactivity timeout -> retry with exponential backoff + jitter */
    EMPTY_OR_TIMEOUT,
    /** Rate limit (429, quota exhausted) -> wait longer before retry */
    RATE_LIMIT,
    /** Invalid API key or auth credentials -> stop immediately, prompt user */
    AUTH_ERROR,
    /** Model error (5xx, overloaded, context/model unavailable) -> trigger fallback */
    MODEL_ERROR,
    /** Other unclassified errors */
    OTHER
}

/**
 * Classifies an error into actionable categories according to recovery strategies.
 */
fun classifyError(
    message: String,
    code: String? = null,
    httpStatusCode: Int? = null,
    isEmptyResponse: Boolean = false
): ErrorCategory {
    if (isEmptyResponse) return ErrorCategory.EMPTY_OR_TIMEOUT
    val lowerMsg = message.lowercase()
    val lowerCode = code?.lowercase().orEmpty()

    // 1. Auth error (API key salah, invalid credentials, unauthorized)
    if (code == "AUTH_ERROR" ||
        code == "401" ||
        httpStatusCode == 401 ||
        lowerCode == "invalid_api_key" ||
        lowerCode == "unauthorized" ||
        lowerMsg.contains("api key is missing") ||
        lowerMsg.contains("api key is invalid") ||
        lowerMsg.contains("invalid api key") ||
        lowerMsg.contains("unauthorized") ||
        lowerMsg.contains("authentication failed") ||
        lowerMsg.contains("sign out and sign in again") ||
        lowerMsg.contains("account id was not found")
    ) {
        return ErrorCategory.AUTH_ERROR
    }

    // 2. Rate limit (429, rate_limit_exceeded, quota exceeded)
    if (code == "429" ||
        httpStatusCode == 429 ||
        lowerCode == "rate_limit_exceeded" ||
        lowerCode == "resource_exhausted" ||
        lowerMsg.contains("rate limit") ||
        lowerMsg.contains("too many requests") ||
        lowerMsg.contains("quota exceeded")
    ) {
        return ErrorCategory.RATE_LIMIT
    }

    // 3. Kosong / timeout
    if (code == "TIMEOUT" ||
        lowerCode == "timeout" ||
        lowerMsg.contains("timed out") ||
        lowerMsg.contains("timeout") ||
        lowerMsg.contains("empty response") ||
        lowerMsg.contains("without a final response") ||
        lowerMsg.contains("ended without a terminal event")
    ) {
        return ErrorCategory.EMPTY_OR_TIMEOUT
    }

    // 4. Model error (500, 502, 503, 504, server error, overloaded, model not found/unavailable)
    if (code in setOf("500", "502", "503", "504") ||
        (httpStatusCode != null && httpStatusCode in setOf(500, 502, 503, 504)) ||
        lowerCode in setOf("server_error", "internal_error", "model_not_found", "model_unavailable") ||
        lowerMsg.contains("overloaded") ||
        lowerMsg.contains("internal server error") ||
        lowerMsg.contains("bad gateway") ||
        lowerMsg.contains("service unavailable") ||
        lowerMsg.contains("model not found") ||
        lowerMsg.contains("model unavailable")
    ) {
        return ErrorCategory.MODEL_ERROR
    }

    return ErrorCategory.OTHER
}

/** Default stream inactivity timeout (60 seconds) without any delta chunk. */
const val DEFAULT_STREAM_INACTIVITY_TIMEOUT_MS = 60_000L

/**
 * Wraps a [Flow] of [ChatResponse] with an inactivity timer.
 * If no chunk is received within [timeoutMs], cancels collection and emits a retryable [ChatResponse.Error].
 */
fun Flow<ChatResponse>.withInactivityTimeout(
    timeoutMs: Long = DEFAULT_STREAM_INACTIVITY_TIMEOUT_MS,
    timeoutMessage: String = "Request timed out: no data received for ${timeoutMs / 1000}s"
): Flow<ChatResponse> = channelFlow {
    var lastActivity = System.currentTimeMillis()
    val checkInterval = minOf(1_000L, maxOf(10L, timeoutMs / 4))
    val watchdog = launch {
        while (isActive) {
            delay(checkInterval)
            val elapsed = System.currentTimeMillis() - lastActivity
            if (elapsed >= timeoutMs) {
                send(ChatResponse.Error(message = timeoutMessage, code = "TIMEOUT", retryable = true))
                close()
                break
            }
        }
    }
    try {
        collect { item ->
            lastActivity = System.currentTimeMillis()
            send(item)
        }
    } catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) {
        // Channel closed by watchdog on timeout; normal termination of timed-out flow
    } finally {
        watchdog.cancel()
    }
}

/**
 * Calculates exponential backoff with random jitter to avoid thundering herd problem.
 * Backoff progression: 1s -> 2s -> 4s (+ jitter).
 */
fun calculateBackoffWithJitter(
    continuationAttempt: Int,
    baseBackoffMax: Long = 4_000L,
    jitterRange: LongRange = 100L..500L
): Long {
    val exponentialBase = 1_000L shl (continuationAttempt - 1).coerceAtLeast(0)
    val cappedBase = minOf(exponentialBase, baseBackoffMax)
    val jitter = Random.nextLong(jitterRange.first, jitterRange.last + 1)
    return cappedBase + jitter
}

/**
 * Calculates a longer backoff delay specifically for Rate Limit (429) errors.
 * Base progression: 5s -> 7s -> 9s (+ jitter).
 */
fun calculateRateLimitBackoff(
    continuationAttempt: Int,
    jitterRange: LongRange = 500L..1500L
): Long {
    val base = 5_000L + (continuationAttempt - 1).coerceAtLeast(0) * 2_000L
    val cappedBase = minOf(base, 10_000L)
    val jitter = Random.nextLong(jitterRange.first, jitterRange.last + 1)
    return cappedBase + jitter
}

/**
 * Searches for a suitable fallback model from the configured connections:
 * 1. Checks current connection for another enabled model.
 * 2. Checks other connections with valid API key for an enabled model.
 */
fun findFallbackCandidate(
    currentConnectionId: String,
    currentModelId: String,
    requiresImages: Boolean = false,
    requiresTools: Boolean = false,
    connections: List<ProviderConnection>,
    hasApiKey: (String) -> Boolean
): Pair<ProviderConnection, ConfiguredModel>? {
    // 1. Same connection, another enabled model
    val currentConn = connections.firstOrNull { it.id == currentConnectionId }
    if (currentConn != null) {
        val sameConnCandidate = currentConn.visibleModels.firstOrNull {
            it.enabled && it.id != currentModelId &&
                (!requiresImages || it.supportsImages) &&
                (!requiresTools || it.supportsTools)
        }
        if (sameConnCandidate != null) {
            return currentConn to sameConnCandidate
        }
    }

    // 2. Different connection that has a valid API key (or provider without key)
    for (conn in connections) {
        if (conn.id == currentConnectionId) continue
        if (!hasApiKey(conn.id)) continue
        val candidate = conn.visibleModels.firstOrNull {
            it.enabled &&
                (!requiresImages || it.supportsImages) &&
                (!requiresTools || it.supportsTools)
        }
        if (candidate != null) {
            return conn to candidate
        }
    }
    return null
}

/**
 * Builds a deterministic call signature for tool name and arguments.
 */
fun buildToolCallSignature(toolName: String, arguments: Map<String, Any?>): String {
    return "$toolName:${JSONObject(arguments)}"
}

/**
 * Evaluates whether the frequency of identical tool calls across a turn exceeds the threshold.
 */
fun shouldCircuitBreakFrequency(frequency: Int): Boolean = frequency >= 3
