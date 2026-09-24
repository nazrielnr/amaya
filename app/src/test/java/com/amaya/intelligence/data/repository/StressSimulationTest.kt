package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.MessageRole
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

/**
 * Stress simulation over the pure decision points of the agent tool loop that this project has
 * hardened across the session (Scheme C verification pass, final-answer fallback, memory-side
 * effect rules, tool-loop rejection bounds, MCP auto-approve gating). Instead of sampling single
 * cases, it exhaustively sweeps the decision matrices and runs thousands of randomized turns
 * through the REAL production functions and constants, asserting the loop invariants:
 *
 *  - verification can run at most MAX_VERIFICATION_PASSES per turn, and only for LOCAL user
 *    turns that used tools / had rejected attempts / carry an active plan;
 *  - a turn that errors from too many rejected calls (>= MAX_FAILED_TOOL_ATTEMPTS) never
 *    surfaces a final-answer fallback (chatImpl returns before the fallback block);
 *  - the fallback fires exactly when the turn is tool-only with no visible text and a result;
 *  - extractAnswerLikeText never throws and never returns blank for non-blank input.
 */
class StressSimulationTest {

    // ── 1) Exhaustive decision-matrix sweeps ──────────────────────────────────

    @Test
    fun `verification gate full matrix respects every condition`() {
        for (passes in 0..MAX_VERIFICATION_PASSES + 1) {
            for (role in enumValues<MessageRole>()) {
                for (target in enumValues<AgentRuntimeTarget>()) {
                    for (tools in 0..3) {
                        for (failed in 0..3) {
                            for (plan in listOf(false, true)) {
                                val expected = passes < MAX_VERIFICATION_PASSES &&
                                    role == MessageRole.USER &&
                                    target == AgentRuntimeTarget.LOCAL &&
                                    (tools > 0 || failed > 0 || plan)
                                assertEquals(
                                    expected,
                                    shouldRunVerificationPass(
                                        verificationPasses = passes,
                                        maxPasses = MAX_VERIFICATION_PASSES,
                                        messageRole = role,
                                        runtimeTarget = target,
                                        executedToolCalls = tools,
                                        failedToolAttempts = failed,
                                        hasPlanSteps = plan
                                    ),
                                    "passes=$passes role=$role target=$target tools=$tools failed=$failed plan=$plan"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `fallback gate full matrix`() {
        for (hasText in listOf(false, true)) {
            for (tools in 0..2) {
                for (result in listOf(null, "  ", "done")) {
                    val expected = !hasText && tools > 0 && !result.isNullOrBlank()
                    assertEquals(
                        expected,
                        needsFinalAnswerFallback(hasAssistantText = hasText, executedToolCalls = tools, lastToolResult = result),
                        "hasText=$hasText tools=$tools result=$result"
                    )
                }
            }
        }
    }

    // ── 2) Answer-extraction fuzz ─────────────────────────────────────────────

    @Test
    fun `answer extraction fuzz never throws and preserves content`() {
        val inputs = buildList {
            // Structured memory-style documents (the common real case).
            add("""{"id":"mem_1","content":"Halo! Ada yang bisa saya bantu hari ini?","version":1}""")
            add("""{"id":"mem_2","content":"Project uses Kotlin and Compose.","version":3}""")
            add("""{"id":"mem_3","content":"","version":1}""")
            add("""{"id":"mem_4","version":2}""")
            // content at the wrong type -> must fall back to raw text.
            add("""{"id":"m","content":12345}""")
            add("""{"id":"m","content":["a","b"]}""")
            add("""{"id":"m","content":{"nested":true}}""")
            add("""{"id":"m","content":null}""")
            // Malformed / near-JSON text.
            add("{")
            add("}")
            add("""{"id":"m","content":"unterminated""")
            add("""{ not valid json }""")
            add("")
            add("   ")
            add("plain answer text")
            add("— Greeting \"halo jai\" was acknowledged with reply \"Halo!\"")
            // Deeply nested + escaped content.
            add(JSONObject().put("content", "line1\nline2\t\"quoted\" \\ slash/unicode \u00e9\u4f60\u597d").toString())
            add(JSONObject().put("outer", JSONObject().put("content", "deep")).toString())
            // Large-ish payloads.
            add(JSONObject().put("content", "x".repeat(10_000)).toString())
            // Generated fuzz cases (inside the builder so `add` resolves to the MutableList receiver).
            repeat(300) { i ->
                add(
                    when (i % 4) {
                        0 -> JSONObject().apply {
                            put("id", "mem_$i")
                            put("content", "random content $i \u00e9\u4f60")
                            put("version", i)
                        }.toString()
                        1 -> "Some plain text result $i"
                        2 -> "{truncated json $i"
                        else -> ""
                    }
                )
            }
        }
        inputs.forEach { input ->
            val output = extractAnswerLikeText(input)
            // Never throws and never shrinks a non-blank input into blank.
            assertTrue(output.isNotBlank() || input.isBlank(), "blank output for: ${input.take(60)}")
            // Content extraction is exact for valid documents with a non-blank string content.
            runCatching { JSONObject(input.trim()) }.getOrNull()?.let { obj ->
                (obj.opt("content") as? String)?.takeIf { it.isNotBlank() }?.let { content ->
                    assertEquals(content, output, "content mismatch for: ${input.take(80)}")
                }
            }
        }
    }

    // ── 3) Randomized end-to-end turn simulation ──────────────────────────────

    private class TurnSimulator(
        val role: MessageRole,
        val target: AgentRuntimeTarget
    ) {
        var executedToolCalls = 0
        var failedAttempts = 0
        var verificationPasses = 0
        var hadPlan = false
        var terminalError = false
        val completedAssistantTexts = mutableListOf<String>()
        var lastToolResult: String? = null
        private var suppressNextText = false

        /** Mirrors one chatImpl request: text/tool-call/rejection outcomes decide the next state. */
        fun providerReply(text: String?, toolCalls: Int, rejected: Int, resultContent: String?) {
            if (terminalError) return
            if (!suppressNextText && !text.isNullOrBlank()) completedAssistantTexts.add(text)
            suppressNextText = false

            if (rejected > 0) {
                failedAttempts += rejected
                if (failedAttempts >= MAX_FAILED_TOOL_ATTEMPTS) {
                    // chatImpl sends a terminal Error and stops before any fallback.
                    terminalError = true
                    return
                }
                if (toolCalls == 0) return // rejection-only: feedback appended, loop continues
            }
            if (toolCalls > 0) {
                executedToolCalls += toolCalls
                resultContent?.let { lastToolResult = it }
                // tool results appended; no stop decision this iteration
            } else {
                // no tools -> stop decision with the verification gate
                if (shouldRunVerificationPass(
                        verificationPasses = verificationPasses,
                        maxPasses = MAX_VERIFICATION_PASSES,
                        messageRole = role,
                        runtimeTarget = target,
                        executedToolCalls = executedToolCalls,
                        failedToolAttempts = failedAttempts,
                        hasPlanSteps = hadPlan
                    )
                ) {
                    verificationPasses++
                    suppressNextText = true // the verification reply is host-internal
                }
            }
        }

        fun finalAnswer(): String? {
            if (terminalError) return null
            // Mirrors the fixed call site: hasAssistantText = completedAssistantTexts.isNotEmpty().
            if (!needsFinalAnswerFallback(completedAssistantTexts.isNotEmpty(), executedToolCalls, lastToolResult)) return null
            return extractAnswerLikeText(lastToolResult!!)
        }
    }

    @Test
    fun `randomized turns preserve loop invariants`() {
        val random = Random(42)
        var fallbackFired = 0
        var verificationFired = 0
        var terminalErrors = 0
        repeat(2_000) { turn ->
            val role = if (random.nextBoolean()) MessageRole.USER else MessageRole.SYSTEM
            val target = if (random.nextBoolean()) AgentRuntimeTarget.LOCAL else AgentRuntimeTarget.WINDOWS_BRIDGE
            val sim = TurnSimulator(role, target)
            sim.hadPlan = random.nextBoolean()
            val requests = random.nextInt(1, 6)
            repeat(requests) { i ->
                if (sim.terminalError) return@repeat
                val toolCalls = when (random.nextInt(3)) {
                    0 -> 0
                    1 -> 1 + random.nextInt(2)
                    else -> 1 + random.nextInt(2)
                }
                val rejected = if (random.nextInt(4) == 0) 1 + random.nextInt(2) else 0
                val text = if (random.nextInt(3) == 0) "visible answer $turn.$i" else null
                val result = if (toolCalls > 0 && random.nextBoolean()) {
                    JSONObject().put("id", "m").put("content", "substance $turn.$i").toString()
                } else null
                sim.providerReply(text, toolCalls, rejected, result)
            }
            // Invariants
            assertTrue(sim.verificationPasses <= MAX_VERIFICATION_PASSES, "verification exceeds cap on turn $turn")
            if (sim.role != MessageRole.USER || sim.target != AgentRuntimeTarget.LOCAL) {
                assertEquals(0, sim.verificationPasses, "verification leaked to non-LOCAL-user turn $turn")
            } else if (sim.executedToolCalls == 0 && sim.failedAttempts == 0 && !sim.hadPlan) {
                assertEquals(0, sim.verificationPasses, "verification leaked to plain Q&A turn $turn")
            }
            if (sim.terminalError) {
                terminalErrors++
                assertEquals(null, sim.finalAnswer(), "terminal-error turn must not surface fallback (turn $turn)")
            } else {
                sim.finalAnswer()?.let { answer ->
                    fallbackFired++
                    assertTrue(answer.isNotBlank())
                }
            }
            if (sim.verificationPasses > 0) verificationFired++
        }
        // The simulation must actually exercise all the interesting branches.
        assertTrue(verificationFired > 0, "simulation never triggered a verification pass")
        assertTrue(fallbackFired > 0, "simulation never triggered the final-answer fallback")
        assertTrue(terminalErrors > 0, "simulation never hit the rejection cap")
    }

    // ── 4) Deterministic scenario replay of the reported bug ──────────────────

    @Test
    fun `replay memory-packed greeting bug - fallback surfaces the real answer`() {
        val sim = TurnSimulator(MessageRole.USER, AgentRuntimeTarget.LOCAL)
        // 1) model saves the acknowledgment to memory and emits no text
        sim.providerReply(
            text = null,
            toolCalls = 1,
            rejected = 0,
            resultContent = """{"id":"mem_1","content":"— Greeting \"halo jai\" was acknowledged with reply \"Halo! Ada yang bisa saya bantu hari ini?\"","version":1}"""
        )
        // 2) model stops with no text -> the verification gate fires (host double-checks the turn)
        sim.providerReply(text = null, toolCalls = 0, rejected = 0, resultContent = null)
        // 3) the verification reply arrives and must be suppressed (not counted as visible text)
        sim.providerReply(text = "VERIFIED — memory saved", toolCalls = 0, rejected = 0, resultContent = null)
        assertEquals(0, sim.completedAssistantTexts.size)
        assertEquals(1, sim.verificationPasses)
        val answer = sim.finalAnswer()
        assertEquals("— Greeting \"halo jai\" was acknowledged with reply \"Halo! Ada yang bisa saya bantu hari ini?\"", answer)
    }

    @Test
    fun `replay normal text answer - no verification leak and no fallback`() {
        val sim = TurnSimulator(MessageRole.USER, AgentRuntimeTarget.LOCAL)
        sim.providerReply(text = "Halo! Ada yang bisa saya bantu hari ini?", toolCalls = 1, rejected = 0, resultContent = "ok")
        sim.providerReply(text = null, toolCalls = 0, rejected = 0, resultContent = null) // stop -> verification gate
        sim.providerReply(text = "VERIFIED — complete", toolCalls = 0, rejected = 0, resultContent = null) // suppressed
        assertEquals(1, sim.verificationPasses)
        assertEquals(listOf("Halo! Ada yang bisa saya bantu hari ini?"), sim.completedAssistantTexts)
        assertEquals(null, sim.finalAnswer()) // real text exists -> no fallback, no tool-result tail
    }

    @Test
    fun `replay rejection cap - turn errors without fallback`() {
        val sim = TurnSimulator(MessageRole.USER, AgentRuntimeTarget.LOCAL)
        sim.providerReply(text = null, toolCalls = 0, rejected = 2, resultContent = null)
        assertFalse(sim.terminalError) // 2 < MAX_FAILED_TOOL_ATTEMPTS
        sim.providerReply(text = null, toolCalls = 0, rejected = 2, resultContent = null)
        assertTrue(sim.terminalError)  // 4 >= 3 -> stop
        assertEquals(null, sim.finalAnswer())
    }

    @Test
    fun `plain Q&A turn never verifies and never falls back`() {
        val sim = TurnSimulator(MessageRole.USER, AgentRuntimeTarget.LOCAL)
        sim.providerReply(text = "Sure, here is the summary.", toolCalls = 0, rejected = 0, resultContent = null)
        assertEquals(0, sim.verificationPasses)
        assertEquals(null, sim.finalAnswer())
    }
}
