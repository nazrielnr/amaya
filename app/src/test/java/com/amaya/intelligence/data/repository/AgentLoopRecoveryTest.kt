package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.domain.models.editedAt
import com.amaya.intelligence.domain.models.isEdited
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentLoopRecoveryTest {

    @Test
    fun `test UiMessage isEdited property returns true when metadata indicates edited`() {
        val normalMsg = UiMessage(
            role = MessageRole.USER,
            content = "Original prompt"
        )
        assertFalse(normalMsg.isEdited)
        assertEquals(null, normalMsg.editedAt)

        val editedMsg = normalMsg.copy(
            content = "Updated prompt",
            metadata = mapOf(
                "isEdited" to "true",
                "editedAt" to "1720000000000"
            )
        )
        assertTrue(editedMsg.isEdited)
        assertEquals(1720000000000L, editedMsg.editedAt)
    }

    @Test
    fun `test edit message replaces in place and preserves preceding messages`() {
        val msg1 = UiMessage(id = "1", role = MessageRole.USER, content = "Hello")
        val msg2 = UiMessage(id = "2", role = MessageRole.ASSISTANT, content = "Hi there")
        val msg3 = UiMessage(id = "3", role = MessageRole.USER, content = "How is weather?")
        val msg4 = UiMessage(id = "4", role = MessageRole.ASSISTANT, content = "It is sunny")

        val messages = listOf(msg1, msg2, msg3, msg4)
        val targetIdx = messages.indexOfFirst { it.id == "3" }
        assertTrue(targetIdx >= 0)

        val updatedUserMsg = messages[targetIdx].copy(
            content = "How is weather in Tokyo?",
            metadata = messages[targetIdx].metadata + mapOf(
                "isEdited" to "true",
                "editedAt" to "1720000000000"
            )
        )
        val truncatedMessages = messages.take(targetIdx) + updatedUserMsg

        assertEquals(3, truncatedMessages.size)
        assertEquals("1", truncatedMessages[0].id)
        assertEquals("2", truncatedMessages[1].id)
        assertEquals("3", truncatedMessages[2].id)
        assertEquals("How is weather in Tokyo?", truncatedMessages[2].content)
        assertTrue(truncatedMessages[2].isEdited)
    }

    @Test
    fun `test tool execution deduplication signature generation`() {
        val toolName = "read_file"
        val arguments1 = """{"path":"test.txt"}"""
        val arguments2 = """{"path":"test.txt"}"""
        val arguments3 = """{"path":"other.txt"}"""

        val sig1 = "$toolName:${arguments1.hashCode()}"
        val sig2 = "$toolName:${arguments2.hashCode()}"
        val sig3 = "$toolName:${arguments3.hashCode()}"

        assertEquals(sig1, sig2)
        assertTrue(sig1 != sig3)
    }

    @Test
    fun `test sanitizeToolName strips formatting tokens and resolves correctly`() {
        val allowedTools = setOf("browser", "read_file", "delegate_agent", "bash")

        // Exact match
        assertEquals("browser", sanitizeToolName("browser", allowedTools))

        // Leaked channel tokens like Qwen/Hermes formatting <|channel|>commentary
        assertEquals("browser", sanitizeToolName("browser<|channel|>commentary", allowedTools))
        assertEquals("browser", sanitizeToolName("browser<|thought|>", allowedTools))
        assertEquals("delegate_agent", sanitizeToolName("delegate_agent[:json]", allowedTools))

        // Namespaced tool calls
        assertEquals("browser", sanitizeToolName("tools.browser", allowedTools))
        assertEquals("read_file", sanitizeToolName("functions:read_file", allowedTools))

        // Unrecognized tool stays as is for error reporting
        assertEquals("unknown_tool", sanitizeToolName("unknown_tool", allowedTools))
    }
}

