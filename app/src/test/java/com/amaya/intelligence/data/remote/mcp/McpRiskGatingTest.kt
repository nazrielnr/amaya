package com.amaya.intelligence.data.remote.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the pure MCP confirmation gate: the terminal auto-approve toggle
 * (autoApproveNonDestructive) covers non-destructive MCP invocations, but a server that
 * explicitly annotates a tool as destructive (destructiveHint) always requires confirmation —
 * auto-approve is never blanket for tools the server itself flags as destructive.
 */
class McpRiskGatingTest {

    @Test
    fun `auto-approve on skips confirmation for a non-destructive tool`() {
        assertFalse(mcpConfirmationRequired(autoApproveNonDestructive = true, destructiveHint = false))
    }

    @Test
    fun `destructive-annotated tool always requires confirmation even with auto-approve on`() {
        assertTrue(mcpConfirmationRequired(autoApproveNonDestructive = true, destructiveHint = true))
    }

    @Test
    fun `auto-approve off requires confirmation for any tool`() {
        assertTrue(mcpConfirmationRequired(autoApproveNonDestructive = false, destructiveHint = false))
        assertTrue(mcpConfirmationRequired(autoApproveNonDestructive = false, destructiveHint = true))
    }

    @Test
    fun `full matrix matches the spec`() {
        val matrix = mapOf(
            (true to false) to false,  // auto-approve on, non-destructive -> no prompt
            (true to true) to true,    // auto-approve on, destructive   -> always prompt
            (false to false) to true,  // auto-approve off, non-destructive -> prompt
            (false to true) to true    // auto-approve off, destructive  -> prompt
        )
        matrix.forEach { (input, expected) ->
            val (autoApprove, destructive) = input
            assertEquals(
                expected,
                mcpConfirmationRequired(autoApprove, destructive),
                "autoApprove=$autoApprove destructive=$destructive"
            )
        }
    }
}
