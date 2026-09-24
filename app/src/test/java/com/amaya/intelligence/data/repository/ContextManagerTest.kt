package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.ChatResponse
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.remote.api.ToolResultMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {
    @Test
    fun `only retryable stream failures without tool calls continue`() {
        assertTrue(canContinueStream(ChatResponse.Incomplete("EOF"), hasToolCalls = false))
        assertTrue(canContinueStream(ChatResponse.Error("Timeout", retryable = true), hasToolCalls = false))
        assertFalse(canContinueStream(ChatResponse.Error("Invalid chunk"), hasToolCalls = false))
        assertFalse(canContinueStream(ChatResponse.Incomplete("EOF"), hasToolCalls = true))
        assertTrue(shouldExecuteReceivedToolCalls(ChatResponse.Incomplete("EOF"), hasToolCalls = true))
        assertTrue(shouldExecuteReceivedToolCalls(ChatResponse.Error("Timeout", retryable = true), hasToolCalls = true))
        assertFalse(shouldExecuteReceivedToolCalls(ChatResponse.Error("Invalid chunk"), hasToolCalls = true))
    }

    @Test
    fun `response item message text is recovered when provider emits no text delta`() {
        val item = """{"type":"message","content":[{"type":"output_text","text":"final answer"}]}"""

        assertEquals("final answer", responseItemOutputText(listOf(item)))
    }

    @Test
    fun `response item extractor ignores reasoning and tool items`() {
        val reasoning = """{"type":"reasoning","summary":[]}"""
        val tool = """{"type":"function_call","name":"browser"}"""

        assertNull(responseItemOutputText(listOf(reasoning, tool)))
    }

    @Test
    fun `provider retry policy is three retries and transient HTTP only`() {
        assertEquals(3, MAX_STREAM_CONTINUATIONS)
        assertTrue(com.amaya.intelligence.data.remote.api.isRetryableHttpStatus(408))
        assertTrue(com.amaya.intelligence.data.remote.api.isRetryableHttpStatus(429))
        assertTrue(com.amaya.intelligence.data.remote.api.isRetryableHttpStatus(503))
        assertFalse(com.amaya.intelligence.data.remote.api.isRetryableHttpStatus(400))
        assertFalse(com.amaya.intelligence.data.remote.api.isRetryableHttpStatus(401))
    }

    @Test
    fun `stream continuation permits tools`() {
        assertFalse(STREAM_CONTINUATION_PROMPT.contains("Do not call tools"))
    }

    private val budgets = PromptBudgetManager()

    @Test
    fun `provider request requires a user query`() {
        assertTrue(hasProviderUserQuery(listOf(ChatMessage(MessageRole.USER, "question"))))
        assertFalse(hasProviderUserQuery(listOf(ChatMessage(MessageRole.USER, images = emptyList()))))
        assertFalse(hasProviderUserQuery(listOf(ChatMessage(MessageRole.ASSISTANT, "answer"))))
        assertFalse(hasProviderUserQuery(listOf(ChatMessage(MessageRole.TOOL, toolResult = ToolResultMessage("id", "result")))))
    }

    @Test
    fun `eviction transcript retains tool call and result`() {
        val transcript = evictionTranscript(
            listOf(
                ChatMessage(MessageRole.USER, "inspect page"),
                ChatMessage(MessageRole.ASSISTANT, toolCalls = listOf(com.amaya.intelligence.data.remote.api.ToolCallMessage("call", "browser", mapOf("action" to "get_dom")))),
                ChatMessage(MessageRole.TOOL, toolResult = ToolResultMessage("call", "page data"))
            ),
            maxChars = 1_000
        )

        assertTrue(transcript.contains("tool_call browser"))
        assertTrue(transcript.contains("tool_result call: page data"))
    }

    /**
     * The old transcript builder concatenated oldest-first and then took the head, so an
     * overflowing eviction discarded the newest activity — the opposite of what a
     * "where did execution stop" summary needs.
     */
    @Test
    fun `eviction transcript keeps the newest activity when it overflows`() {
        val evicted = (1..40).map { ChatMessage(MessageRole.ASSISTANT, "step-$it ".repeat(40)) }

        val transcript = evictionTranscript(evicted, maxChars = 1_200)

        assertTrue("newest step must survive", transcript.contains("step-40"))
        assertFalse("oldest step must be the one elided", transcript.contains("step-1 "))
        assertTrue(transcript.contains("older evicted activity elided"))
    }

    /**
     * The active turn used to be the only thing that survived: everything before the latest user
     * message was dropped regardless of budget. Full coverage lives in ContextPlanTest.
     */
    @Test
    fun `planning retains the earlier turns that still fit`() {
        val user = ChatMessage(MessageRole.USER, "latest")
        val toolCall = ChatMessage(MessageRole.ASSISTANT, toolCalls = listOf(com.amaya.intelligence.data.remote.api.ToolCallMessage("call", "browser", emptyMap())))
        val toolResult = ChatMessage(MessageRole.TOOL, toolResult = ToolResultMessage("call", "result"))
        val messages = listOf(ChatMessage(MessageRole.USER, "old"), ChatMessage(MessageRole.ASSISTANT, "old response"), user, toolCall, toolResult)

        val plan = planContextWindow(messages, maxTokens = 100_000)

        assertEquals(messages, plan.messages)
        assertTrue(plan.evicted.isEmpty())
    }

    @Test
    fun `oversized tool result truncates within its character budget`() {
        val truncated = truncateToolResultForContext("result ".repeat(2_000), 400)
        assertTrue(truncated.contains("[tool result truncated by context budget]"))
        assertTrue(truncated.length <= 400)
        assertEquals("short", truncateToolResultForContext("short", 400))
    }

    @Test
    fun `prompt budget reserves output tools and safety`() {
        val withTools = budgets.promptBudgetFor(32_768, 8_192, 4_000)
        val withoutTools = budgets.promptBudgetFor(32_768, 8_192, 0)
        assertTrue(withTools.maxSystemTokens < withoutTools.maxSystemTokens)
    }

    /**
     * Required sections are charged before optional ones. Previously `required` was computed and
     * then ignored, so operating rules could be evicted by whatever ranked ahead of them.
     */
    @Test
    fun `required sections survive a prompt budget that optional content would exhaust`() {
        val sections = listOf(
            PromptSection("operating_rules", "SYSTEM", 1000, ContextInclusionMode.ALWAYS, alwaysInclude = true),
            PromptSection("user_memory", "USER MEMORY", 690, ContextInclusionMode.FULL)
        )
        val items = listOf(
            ContextItem("bulk_memory", "user_memory", ContextSource.MEMORY, "Memory", "filler ".repeat(4_000), 690, mode = ContextInclusionMode.FULL, maxTokens = 900),
            ContextItem("rules", "operating_rules", ContextSource.OPERATING_RULES, "System", "Always follow the operating rules.", 1000, mode = ContextInclusionMode.ALWAYS, alwaysInclude = true)
        )

        val built = budgets.buildPrompt(sections, items, PromptBudget(maxSystemTokens = 300, maxItemTokens = 900))

        assertTrue(built.prompt.contains("Always follow the operating rules."))
        assertTrue(built.droppedItems.none { it.id == "rules" })
    }

    @Test
    fun `prompt items are truncated to a size the same estimator accepts`() {
        val sections = listOf(PromptSection("user_memory", "USER MEMORY", 690, ContextInclusionMode.FULL))
        val items = listOf(
            ContextItem("bulk", "user_memory", ContextSource.MEMORY, "Memory", "catatan panjang ".repeat(2_000), 690, mode = ContextInclusionMode.FULL, maxTokens = 120)
        )

        val built = budgets.buildPrompt(sections, items, PromptBudget(maxSystemTokens = 4_000, maxItemTokens = 900))

        assertTrue("item must not be dropped after truncation", built.droppedItems.isEmpty())
        assertTrue(built.prompt.contains("truncated by prompt budget"))
        assertTrue(budgets.estimateTokens(built.prompt) <= 4_000)
    }
}
