package com.amaya.intelligence.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the final-answer fallback helper: when a tool-using turn ends with no model text
 * (the model packed its reply into a tool call, e.g. saving it to memory), the last tool
 * result is surfaced as the visible answer. The memory-style result is a JSON document whose
 * `content` field carries the substance the user should read.
 */
class ToolResultFallbackTest {

    @Test
    fun `memory-style json result surfaces its content field`() {
        val result = """{"id":"mem_1","content":"— Greeting \"halo jai\" was acknowledged with reply \"Halo! Ada yang bisa saya bantu hari ini?\"","type":"user_profile","version":3}"""

        assertEquals(
            "— Greeting \"halo jai\" was acknowledged with reply \"Halo! Ada yang bisa saya bantu hari ini?\"",
            extractAnswerLikeText(result)
        )
    }

    @Test
    fun `json result without content field stays verbatim`() {
        val result = """{"id":"mem_1","version":3}"""

        assertEquals(result, extractAnswerLikeText(result))
    }

    @Test
    fun `plain text result passes through unchanged`() {
        assertEquals("Search complete: 3 results", extractAnswerLikeText("Search complete: 3 results"))
    }

    @Test
    fun `unparseable brace text passes through unchanged`() {
        val result = "{ not valid json content }"
        assertEquals(result, extractAnswerLikeText(result))
    }

    @Test
    fun `json result with blank content falls back to the raw document`() {
        val result = """{"id":"mem_1","content":"  "}"""
        assertEquals(result, extractAnswerLikeText(result))
    }
}
