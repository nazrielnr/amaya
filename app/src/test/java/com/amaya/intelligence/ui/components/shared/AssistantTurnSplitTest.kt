package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.MessageStep
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.domain.models.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTurnSplitTest {

    @Test
    fun `plain text message has no work steps and surfaces text as answer`() {
        val message = UiMessage(
            id = "1",
            role = MessageRole.ASSISTANT,
            content = "Hello world",
            steps = listOf(
                MessageStep.Text(content = "Hello world")
            )
        )
        val split = splitAssistantTurn(message)
        assertFalse(split.wrapInSummary)
        assertTrue(split.workSteps.isEmpty())
        assertEquals(1, split.answerSteps.size)
        assertEquals("Hello world", split.answerSteps.first().content)
    }

    @Test
    fun `turn with action tool and trailing answer keeps tool in work card and text below`() {
        val message = UiMessage(
            id = "2",
            role = MessageRole.ASSISTANT,
            content = "Task finished successfully.",
            steps = listOf(
                MessageStep.Thinking(text = "Analyzing..."),
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_1", name = "run_command", arguments = emptyMap())
                ),
                MessageStep.Text(content = "Task finished successfully.")
            )
        )
        val split = splitAssistantTurn(message)
        assertTrue(split.wrapInSummary)
        assertEquals(2, split.workSteps.size)
        assertTrue(split.workSteps[0] is MessageStep.Thinking)
        assertTrue(split.workSteps[1] is MessageStep.ToolCall)
        assertEquals(1, split.answerSteps.size)
        assertEquals("Task finished successfully.", split.answerSteps.first().content)
    }

    @Test
    fun `turn with trailing housekeeping tool does not bury substantive final output in work card`() {
        val substantiveOutput = "Here is the full solution with detailed explanation of changes."
        val message = UiMessage(
            id = "3",
            role = MessageRole.ASSISTANT,
            content = "$substantiveOutput\nSaved memory.",
            steps = listOf(
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_1", name = "edit_file", arguments = emptyMap())
                ),
                MessageStep.Text(content = substantiveOutput),
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_2", name = "memory_manage", arguments = emptyMap())
                ),
                MessageStep.Text(content = "Saved memory.")
            )
        )
        val split = splitAssistantTurn(message)
        assertTrue(split.wrapInSummary)
        // Work steps contain the action tool and the housekeeping tool
        assertEquals(2, split.workSteps.size)
        assertEquals("edit_file", (split.workSteps[0] as MessageStep.ToolCall).execution.name)
        assertEquals("memory_manage", (split.workSteps[1] as MessageStep.ToolCall).execution.name)

        // Answer steps include all post-work text steps, preserving the substantive output outside the work card
        assertEquals(2, split.answerSteps.size)
        assertEquals(substantiveOutput, split.answerSteps[0].content)
        assertEquals("Saved memory.", split.answerSteps[1].content)
    }

    @Test
    fun `intermediate thought text before action tool is kept in work card`() {
        val message = UiMessage(
            id = "4",
            role = MessageRole.ASSISTANT,
            content = "Let me check.\nEverything passes.",
            steps = listOf(
                MessageStep.Text(content = "Let me check."),
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_1", name = "run_command", arguments = emptyMap())
                ),
                MessageStep.Text(content = "Everything passes.")
            )
        )
        val split = splitAssistantTurn(message)
        assertTrue(split.wrapInSummary)
        assertEquals(2, split.workSteps.size)
        assertEquals("Let me check.", (split.workSteps[0] as MessageStep.Text).content)
        assertEquals("run_command", (split.workSteps[1] as MessageStep.ToolCall).execution.name)

        assertEquals(1, split.answerSteps.size)
        assertEquals("Everything passes.", split.answerSteps.first().content)
    }

    @Test
    fun `turn with trailing agent_memory housekeeping tool preserves substantive answer and does not bury it in work card`() {
        val substantiveOutput = "I have reviewed and updated the code as requested."
        val message = UiMessage(
            id = "5",
            role = MessageRole.ASSISTANT,
            content = substantiveOutput,
            steps = listOf(
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_1", name = "write_file", arguments = emptyMap())
                ),
                MessageStep.Text(content = substantiveOutput),
                MessageStep.ToolCall(
                    execution = ToolExecution(toolCallId = "call_2", name = "agent_memory", arguments = mapOf("operation" to "save"))
                )
            )
        )
        val split = splitAssistantTurn(message)
        assertTrue(split.wrapInSummary)
        // Work steps contain write_file and agent_memory
        assertEquals(2, split.workSteps.size)
        assertEquals("write_file", (split.workSteps[0] as MessageStep.ToolCall).execution.name)
        assertEquals("agent_memory", (split.workSteps[1] as MessageStep.ToolCall).execution.name)

        // Substantive answer remains in answerSteps, not buried in work card
        assertEquals(1, split.answerSteps.size)
        assertEquals(substantiveOutput, split.answerSteps.first().content)
    }
}

