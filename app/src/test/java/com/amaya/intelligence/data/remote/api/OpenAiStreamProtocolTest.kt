package com.amaya.intelligence.data.remote.api

import com.amaya.intelligence.data.remote.provider.openai.OpenAiStreamChoice
import com.amaya.intelligence.data.remote.provider.openai.OpenAiStreamChunk
import org.junit.Assert.assertEquals
import com.squareup.moshi.Moshi
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.json.JSONObject
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
    fun `tool arguments serialize nested JSON objects`() {
        val json = Moshi.Builder().build().jsonArgs(mapOf(
            "payload" to JSONObject().put("label", "ok").put("nested", JSONObject().put("count", 2))
        ))

        val payload = JSONObject(json).getJSONObject("payload")
        assertEquals("ok", payload.getString("label"))
        assertEquals(2, payload.getJSONObject("nested").getInt("count"))
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
        val chunk = Moshi.Builder()
            .build()
            .adapter(OpenAiStreamChunk::class.java)
            .fromJson("""{"choices":[{"index":0,"delta":{"content":"Hi"},"finish_reason":null}]}""")

        assertNull(chunk?.id)
        assertEquals("Hi", chunk?.choices?.single()?.delta?.content)
    }

    @Test
    fun `usage chunk without choices parses`() {
        val chunk = Moshi.Builder()
            .build()
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
    fun `id change at a reused index starts a fresh call instead of failing`() {
        val accumulator = OpenAiToolCallAccumulator()
        accumulator.append(0, "call_a", "read_file", "{\"path\":")
        // Vendor re-issues index 0 with a different id — treat as a new call, never throw.
        accumulator.append(0, "call_b", null, "\"other\"}")

        assertEquals(
            listOf(CompletedOpenAiToolCall("call_b", "", "\"other\"}")),
            accumulator.complete()
        )
    }

    @Test
    fun `name change at the same index adopts the latest name without failing`() {
        val accumulator = OpenAiToolCallAccumulator()
        accumulator.append(0, "call_a", "read_file", "{\"path\":")
        // Same id, updated name — the arguments keep accumulating, the newest name wins.
        accumulator.append(0, null, "write_file", "\"other\"}")

        assertEquals(
            listOf(CompletedOpenAiToolCall("call_a", "write_file", "{\"path\":\"other\"}")),
            accumulator.complete()
        )
    }

    @Test
    fun `partial call at completion does not throw`() {
        val accumulator = OpenAiToolCallAccumulator()
        accumulator.append(0, null, null, "{}")

        // Blank id/name surface to the agent loop, which rejects the call and feeds the model
        // recoverable feedback instead of terminating the stream with a parse error.
        assertEquals(
            listOf(CompletedOpenAiToolCall("", "", "{}")),
            accumulator.complete()
        )
    }
}
