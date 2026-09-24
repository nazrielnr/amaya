package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.ChatResponse
import com.amaya.intelligence.data.remote.api.ConfiguredModel
import com.amaya.intelligence.data.remote.api.ProviderConnection
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiAgentResilienceTest {

    // 1. Empty Response Detection
    @Test
    fun testEmptyResponseClassification() {
        val category = classifyError("Empty response", isEmptyResponse = true)
        assertEquals(ErrorCategory.EMPTY_OR_TIMEOUT, category)
    }

    // 2. Inactivity Timeout
    @Test
    fun testInactivityTimeoutEmitsTimeoutErrorWhenFlowStalls() = runBlocking {
        val stalledFlow = flow<ChatResponse> {
            emit(ChatResponse.TextDelta("First chunk"))
            kotlinx.coroutines.delay(250) // exceed 100ms test timeout
            emit(ChatResponse.TextDelta("Delayed chunk"))
        }

        val results = stalledFlow.withInactivityTimeout(
            timeoutMs = 100L,
            timeoutMessage = "Request timed out after 100ms"
        ).toList()

        assertTrue(results.isNotEmpty())
        assertEquals("First chunk", (results[0] as ChatResponse.TextDelta).text)
        val lastEvent = results.last()
        assertTrue("Expected ChatResponse.Error on timeout, got $lastEvent", lastEvent is ChatResponse.Error)
        val errorEvent = lastEvent as ChatResponse.Error
        assertEquals("TIMEOUT", errorEvent.code)
        assertTrue(errorEvent.retryable)
        assertTrue(errorEvent.message.contains("timed out"))
    }

    @Test
    fun testInactivityTimeoutPassesThroughActiveFlow() = runBlocking {
        val activeFlow = flow {
            emit(ChatResponse.TextDelta("A"))
            kotlinx.coroutines.delay(20)
            emit(ChatResponse.TextDelta("B"))
            kotlinx.coroutines.delay(20)
            emit(ChatResponse.Done())
        }

        val results = activeFlow.withInactivityTimeout(timeoutMs = 500L).toList()
        assertEquals(3, results.size)
        assertTrue(results[0] is ChatResponse.TextDelta)
        assertTrue(results[1] is ChatResponse.TextDelta)
        assertTrue(results[2] is ChatResponse.Done)
    }

    // 3. Retry + Exponential Backoff with Jitter
    @Test
    fun testBackoffProgressionAndJitter() {
        val attempt1 = calculateBackoffWithJitter(1, baseBackoffMax = 4000L, jitterRange = 100L..500L)
        val attempt2 = calculateBackoffWithJitter(2, baseBackoffMax = 4000L, jitterRange = 100L..500L)
        val attempt3 = calculateBackoffWithJitter(3, baseBackoffMax = 4000L, jitterRange = 100L..500L)

        // Attempt 1: 1000 + (100..500)
        assertTrue("Attempt 1 out of range: $attempt1", attempt1 in 1100L..1500L)
        // Attempt 2: 2000 + (100..500)
        assertTrue("Attempt 2 out of range: $attempt2", attempt2 in 2100L..2500L)
        // Attempt 3: 4000 + (100..500)
        assertTrue("Attempt 3 out of range: $attempt3", attempt3 in 4100L..4500L)

        // Check jitter variance across multiple samples
        val samples = (1..10).map { calculateBackoffWithJitter(1, jitterRange = 100L..500L) }.toSet()
        assertTrue("Jitter should produce varying backoff values", samples.size > 1)
    }

    // 4. Fallback Model / Provider
    @Test
    fun testFallbackToAnotherModelInSameConnection() {
        val modelA = ConfiguredModel(id = "model-a", displayName = "Model A", enabled = true, supportsTools = true)
        val modelB = ConfiguredModel(id = "model-b", displayName = "Model B", enabled = true, supportsTools = true)
        val connection = ProviderConnection(
            id = "conn-1",
            providerId = "provider-1",
            name = "Conn 1",
            visibleModels = listOf(modelA, modelB)
        )

        val candidate = findFallbackCandidate(
            currentConnectionId = "conn-1",
            currentModelId = "model-a",
            requiresImages = false,
            requiresTools = true,
            connections = listOf(connection),
            hasApiKey = { true }
        )

        assertNotNull(candidate)
        assertEquals("model-b", candidate!!.second.id)
        assertEquals("conn-1", candidate.first.id)
    }

    @Test
    fun testFallbackToDifferentConnectionWhenSameConnectionHasNoAlternatives() {
        val modelA = ConfiguredModel(id = "model-a", displayName = "Model A", enabled = true, supportsTools = true)
        val conn1 = ProviderConnection(
            id = "conn-1",
            providerId = "provider-1",
            name = "Conn 1",
            visibleModels = listOf(modelA)
        )
        val modelBackup = ConfiguredModel(id = "model-backup", displayName = "Backup Model", enabled = true, supportsTools = true)
        val conn2 = ProviderConnection(
            id = "conn-2",
            providerId = "provider-2",
            name = "Conn 2",
            visibleModels = listOf(modelBackup)
        )

        val candidate = findFallbackCandidate(
            currentConnectionId = "conn-1",
            currentModelId = "model-a",
            requiresImages = false,
            requiresTools = true,
            connections = listOf(conn1, conn2),
            hasApiKey = { id -> id == "conn-2" }
        )

        assertNotNull(candidate)
        assertEquals("conn-2", candidate!!.first.id)
        assertEquals("model-backup", candidate.second.id)
    }

    @Test
    fun testFallbackFiltersOutModelsWithoutRequiredCapabilities() {
        val modelA = ConfiguredModel(id = "model-a", displayName = "Model A", enabled = true, supportsImages = false)
        val modelNoImg = ConfiguredModel(id = "model-b", displayName = "Model B", enabled = true, supportsImages = false)
        val modelWithImg = ConfiguredModel(id = "model-c", displayName = "Model C", enabled = true, supportsImages = true)
        val conn = ProviderConnection(
            id = "conn-1",
            providerId = "provider-1",
            name = "Conn 1",
            visibleModels = listOf(modelA, modelNoImg, modelWithImg)
        )

        val candidate = findFallbackCandidate(
            currentConnectionId = "conn-1",
            currentModelId = "model-a",
            requiresImages = true,
            requiresTools = false,
            connections = listOf(conn),
            hasApiKey = { true }
        )

        assertNotNull(candidate)
        assertEquals("model-c", candidate!!.second.id)
    }

    // 5. Error Classification
    @Test
    fun testErrorClassificationCategories() {
        // Auth errors
        assertEquals(ErrorCategory.AUTH_ERROR, classifyError("Invalid API key provided", code = "401"))
        assertEquals(ErrorCategory.AUTH_ERROR, classifyError("API key is missing", code = "AUTH_ERROR"))
        assertEquals(ErrorCategory.AUTH_ERROR, classifyError("Unauthorized request"))

        // Rate limit
        assertEquals(ErrorCategory.RATE_LIMIT, classifyError("Too many requests", code = "429"))
        assertEquals(ErrorCategory.RATE_LIMIT, classifyError("Resource exhausted: quota exceeded"))

        // Timeout / empty
        assertEquals(ErrorCategory.EMPTY_OR_TIMEOUT, classifyError("Request timed out", code = "TIMEOUT"))
        assertEquals(ErrorCategory.EMPTY_OR_TIMEOUT, classifyError("Empty response", isEmptyResponse = true))

        // Model error
        assertEquals(ErrorCategory.MODEL_ERROR, classifyError("Internal server error", code = "500"))
        assertEquals(ErrorCategory.MODEL_ERROR, classifyError("Service unavailable", code = "503"))
        assertEquals(ErrorCategory.MODEL_ERROR, classifyError("The model is overloaded. Please try again later."))
    }

    @Test
    fun testRateLimitBackoffIsLongerThanStandardBackoff() {
        val rateLimitDelay = calculateRateLimitBackoff(1)
        val normalDelay = calculateBackoffWithJitter(1)
        assertTrue("Rate limit delay ($rateLimitDelay) should be significantly longer than normal delay ($normalDelay)", rateLimitDelay > normalDelay)
        assertTrue("Rate limit delay should be at least 5000ms", rateLimitDelay >= 5000L)
    }

    // 6. Loop Detection
    @Test
    fun testToolCallSignatureAndCircuitBreakerThresholds() {
        val sig1 = buildToolCallSignature("read_file", mapOf("path" to "file.txt"))
        val sig2 = buildToolCallSignature("read_file", mapOf("path" to "file.txt"))
        val sig3 = buildToolCallSignature("read_file", mapOf("path" to "other.txt"))

        assertEquals(sig1, sig2)
        assertNotEquals(sig1, sig3)

        assertFalse(shouldCircuitBreakConsecutiveCalls(1))
        assertFalse(shouldCircuitBreakConsecutiveCalls(2))
        assertTrue(shouldCircuitBreakConsecutiveCalls(3))
        assertTrue(shouldCircuitBreakConsecutiveCalls(4))

        assertFalse(shouldCircuitBreakFrequency(1))
        assertFalse(shouldCircuitBreakFrequency(2))
        assertTrue(shouldCircuitBreakFrequency(3))
    }
}
