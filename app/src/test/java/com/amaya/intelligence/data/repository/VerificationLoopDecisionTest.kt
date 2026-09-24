package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.MessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the two pure decision points of the Scheme C verification loop in AiAgentLoop.chatImpl:
 * (1) whether a tool-using turn gets its single extra verification pass, and (2) whether a turn
 * that ended with no model text should surface the last tool result as the final answer.
 */
class VerificationLoopDecisionTest {

    // ── shouldRunVerificationPass ──────────────────────────────────────────────

    @Test
    fun `tool-using user turn gets one verification pass`() {
        assertTrue(
            shouldRunVerificationPass(
                verificationPasses = 0,
                maxPasses = 1,
                messageRole = MessageRole.USER,
                runtimeTarget = AgentRuntimeTarget.LOCAL,
                executedToolCalls = 2,
                failedToolAttempts = 0,
                hasPlanSteps = false
            )
        )
    }

    @Test
    fun `verification runs only once per turn`() {
        assertFalse(
            shouldRunVerificationPass(
                verificationPasses = 1,
                maxPasses = 1,
                messageRole = MessageRole.USER,
                runtimeTarget = AgentRuntimeTarget.LOCAL,
                executedToolCalls = 2,
                failedToolAttempts = 0,
                hasPlanSteps = false
            )
        )
    }

    @Test
    fun `plain Q&A without tools never verifies`() {
        assertFalse(
            shouldRunVerificationPass(
                verificationPasses = 0,
                maxPasses = 1,
                messageRole = MessageRole.USER,
                runtimeTarget = AgentRuntimeTarget.LOCAL,
                executedToolCalls = 0,
                failedToolAttempts = 0,
                hasPlanSteps = false
            )
        )
    }

    @Test
    fun `rejected tool attempts also trigger verification`() {
        assertTrue(
            shouldRunVerificationPass(
                verificationPasses = 0,
                maxPasses = 1,
                messageRole = MessageRole.USER,
                runtimeTarget = AgentRuntimeTarget.LOCAL,
                executedToolCalls = 0,
                failedToolAttempts = 2,
                hasPlanSteps = false
            )
        )
    }

    @Test
    fun `active plan triggers verification even before any tool ran`() {
        assertTrue(
            shouldRunVerificationPass(
                verificationPasses = 0,
                maxPasses = 1,
                messageRole = MessageRole.USER,
                runtimeTarget = AgentRuntimeTarget.LOCAL,
                executedToolCalls = 0,
                failedToolAttempts = 0,
                hasPlanSteps = true
            )
        )
    }

    @Test
    fun `bridge turns and internal continuations never verify`() {
        val bridge = shouldRunVerificationPass(
            verificationPasses = 0,
            maxPasses = 1,
            messageRole = MessageRole.USER,
            runtimeTarget = AgentRuntimeTarget.WINDOWS_BRIDGE,
            executedToolCalls = 3,
            failedToolAttempts = 0,
            hasPlanSteps = false
        )
        val internal = shouldRunVerificationPass(
            verificationPasses = 0,
            maxPasses = 1,
            messageRole = MessageRole.SYSTEM,
            runtimeTarget = AgentRuntimeTarget.LOCAL,
            executedToolCalls = 3,
            failedToolAttempts = 0,
            hasPlanSteps = false
        )
        assertFalse(bridge)
        assertFalse(internal)
    }

    // ── needsFinalAnswerFallback ───────────────────────────────────────────────

    @Test
    fun `tool-only turn with no text surfaces the last tool result`() {
        assertTrue(needsFinalAnswerFallback(hasAssistantText = false, executedToolCalls = 1, lastToolResult = "done"))
    }

    @Test
    fun `turn that already produced text keeps its answer`() {
        assertFalse(needsFinalAnswerFallback(hasAssistantText = true, executedToolCalls = 2, lastToolResult = "done"))
    }

    @Test
    fun `plain Q&A with no tools never falls back`() {
        assertFalse(needsFinalAnswerFallback(hasAssistantText = false, executedToolCalls = 0, lastToolResult = "done"))
    }

    @Test
    fun `blank last tool result never falls back`() {
        assertFalse(needsFinalAnswerFallback(hasAssistantText = false, executedToolCalls = 1, lastToolResult = "  "))
    }

    // ── End-to-end scenario: memory-style answer packed into a tool call ──────

    @Test
    fun `memory-packed answer flow ends with readable content`() {
        // User greets -> the model saved the acknowledgment to memory and emitted no text, so the
        // turn is tool-only (fallback needed) and verification would have been gated on.
        val verify = shouldRunVerificationPass(
            verificationPasses = 0,
            maxPasses = 1,
            messageRole = MessageRole.USER,
            runtimeTarget = AgentRuntimeTarget.LOCAL,
            executedToolCalls = 1,
            failedToolAttempts = 0,
            hasPlanSteps = false
        )
        assertTrue(verify)

        // After the (suppressed) verification reply, the turn still has no visible text, so the
        // last memory tool result is surfaced as the final answer.
        val fallback = needsFinalAnswerFallback(hasAssistantText = false, executedToolCalls = 1, lastToolResult = """{"id":"mem_1","content":"Halo! Ada yang bisa saya bantu hari ini?","version":1}""")
        assertTrue(fallback)
        assertEquals("Halo! Ada yang bisa saya bantu hari ini?", extractAnswerLikeText("""{"id":"mem_1","content":"Halo! Ada yang bisa saya bantu hari ini?","version":1}"""))
    }
}
