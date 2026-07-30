package com.amaya.intelligence.data.remote.api

import com.amaya.intelligence.data.remote.provider.openai.OpenAiStreamChoice
import com.amaya.intelligence.data.remote.provider.openai.OpenAiStreamChunk
import org.junit.Assert.assertEquals
import com.squareup.moshi.Moshi
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiStreamProtocolTest {
    @Test
    fun `dynamic tool schema uses parameterized Moshi map adapter`() {
        val moshi = Moshi.Builder().build()
        val json = """{"type":"object","properties":{"task":{"type":"string"}},"required":["task"]}"""

        val schema = moshi.parseJsonArgs(json).getOrThrow()

        assertEquals("object", schema["type"])
        assertEquals("string", (schema["properties"] as Map<*, *>)["task"].let { it as Map<*, *> }["type"])
    }

    @Test
    fun `text deltas coalesce without crossing thinking boundary`() {
        val coalescer = OpenAiDeltaCoalescer()

        assertNull(coalescer.append(ChatResponse.TextDelta("Hel")))
        assertNull(coalescer.append(ChatResponse.TextDelta("lo")))
        assertEquals(ChatResponse.TextDelta("Hello"), coalescer.append(ChatResponse.ThinkingDelta("plan")))
        assertEquals(ChatResponse.ThinkingDelta("plan"), coalescer.flush())
    }

    @Test
    fun `interleaved tool deltas remain correlated by index`() {
        val accumulator = OpenAiToolCallAccumulator()
        accumulator.append(0, "call_a", "read_file", "{\"path\":")
        accumulator.append(1, "call_b", "find_files", "{\"path\":\"/b\",")
        accumulator.append(0, null, null, "\"/a\"}")
        accumulator.append(1, null, null, "\"pattern\":\"*.kt\"}")

        assertEquals(
            listOf(
                CompletedOpenAiToolCall("call_a", "read_file", "{\"path\":\"/a\"}"),
                CompletedOpenAiToolCall("call_b", "find_files", "{\"path\":\"/b\",\"pattern\":\"*.kt\"}")
            ),
            accumulator.complete()
        )
    }

    @Test
    fun `metadata-free delta chunk parses`() {
        val chunk = Moshi.Builder().build()
            .adapter(OpenAiStreamChunk::class.java)
            .fromJson("""{"choices":[{"index":0,"delta":{"content":"Hi"},"finish_reason":null}]}""")

        assertNull(chunk?.id)
        assertEquals("Hi", chunk?.choices?.single()?.delta?.content)
    }

    @Test
    fun `usage chunk without choices parses`() {
        val chunk = Moshi.Builder().build()
            .adapter(OpenAiStreamChunk::class.java)
            .fromJson("""{"usage":{"prompt_tokens":12,"completion_tokens":4,"total_tokens":16}}""")

        assertEquals(emptyList<OpenAiStreamChoice>(), chunk?.choices)
        assertEquals(12, chunk?.usage?.promptTokens)
    }

    @Test
    fun `toolcall finish reason normalizes to completed tool calls`() {
        assertEquals("tool_calls", normalizeOpenAiFinishReason("toolcall"))
        assertEquals("tool_calls", normalizeOpenAiFinishReason(" tool_call "))
        assertEquals("stop", normalizeOpenAiFinishReason("STOP"))
        assertEquals(true, isOpenAiCompletedFinishReason("toolcall"))
        assertEquals(true, isOpenAiCompletedFinishReason("tool_calls"))
        assertEquals(false, isOpenAiCompletedFinishReason("length"))
    }

    @Test
    fun `missing index fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiToolCallAccumulator().append(null, "call", "read_file", "{}")
        }
    }

    @Test
    fun `EOF before terminal fails closed`() {
        assertEquals(true, OpenAiTerminalGuard().eofIsFailure())
    }

    @Test
    fun `completed terminal accepts EOF and rejects duplicates`() {
        val guard = OpenAiTerminalGuard()
        guard.complete()
        assertEquals(false, guard.eofIsFailure())
        assertThrows(IllegalArgumentException::class.java) { guard.fail() }
    }

    @Test
    fun `identity change fails closed`() {
        val accumulator = OpenAiToolCallAccumulator()
        accumulator.append(0, "call_a", "read_file", "{")
        assertThrows(IllegalArgumentException::class.java) {
            accumulator.append(0, "call_b", null, "}")
        }
    }
}
