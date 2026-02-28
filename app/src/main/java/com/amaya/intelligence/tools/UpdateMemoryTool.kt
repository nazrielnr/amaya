package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.data.repository.PersonaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI tool for writing to MEMORY.md or the daily memory log (YYYY-MM-DD.md).
 *
 * Two modes:
 *  - target="daily"  → appends a timestamped bullet to today's memory/YYYY-MM-DD.md
 *  - target="long"   → appends/updates a section in MEMORY.md (long-term memory)
 */
@Singleton
class UpdateMemoryTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personaRepository: PersonaRepository
) : Tool {

    override val name = "update_memory"

    override val description = """
        Persist important information for future sessions.
        
        Use this when:
        - The user shares something important (name, preference, ongoing goal)
        - Something meaningful happened in this session (task completed, reminder set)
        - The user explicitly asks you to remember something
        
        Arguments:
        - content (string, required): What to remember. Write as a clear, self-contained sentence.
        - target (string, optional): "daily" (default) writes to today's daily log,
          "long" writes to MEMORY.md for long-term memory.
        - section (string, optional): Section heading in MEMORY.md to write under.
          Defaults to "Important Facts". Only used when target="long".
    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val content = arguments["content"] as? String
                ?: return@withContext ToolResult.Error(
                    "Missing required: content",
                    ErrorType.VALIDATION_ERROR
                )
            val target = (arguments["target"] as? String)?.lowercase() ?: "daily"
            val section = (arguments["section"] as? String) ?: "Important Facts"

            return@withContext when (target) {
                "long" -> writeLongTermMemory(content, section)
                else   -> writeDailyLog(content)
            }
        }

    private fun writeDailyLog(content: String): ToolResult {
        return try {
            personaRepository.appendDailyLog(content)
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            ToolResult.Success(
                output = "✓ Noted in today's memory log ($today.md): \"$content\"",
                metadata = mapOf("target" to "daily", "date" to today)
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to write daily log: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }

    companion object {
        // FIX 8: Cap MEMORY.md at 512KB to prevent unbounded file growth.
        // At ~500 bytes per entry, this allows ~1000 long-term memories.
        private const val MAX_MEMORY_SIZE_BYTES = 512 * 1024
    }

    private fun writeLongTermMemory(content: String, section: String): ToolResult {
        return try {
            val current = personaRepository.readFile("MEMORY.md")
            // FIX 8: Refuse write if file already exceeds size cap
            if (current.length > MAX_MEMORY_SIZE_BYTES) {
                return ToolResult.Error(
                    "MEMORY.md has reached the size limit (512KB). " +
                    "Please ask the user to review and trim old entries before adding more.",
                    ErrorType.VALIDATION_ERROR
                )
            }
            val sectionHeader = "## $section"
            val entry = "- $content"

            val updated = if (current.contains(sectionHeader)) {
                // Append to existing section
                current.replace(
                    sectionHeader,
                    "$sectionHeader\n$entry"
                )
            } else {
                // Append new section at end
                "$current\n\n$sectionHeader\n$entry"
            }

            personaRepository.writeFile("MEMORY.md", updated)

            ToolResult.Success(
                output = "✓ Saved to long-term memory (MEMORY.md) under \"$section\": \"$content\"",
                metadata = mapOf("target" to "long", "section" to section)
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to update MEMORY.md: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}
