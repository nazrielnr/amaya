package com.amaya.intelligence.data.repository

import android.content.Context
import com.amaya.intelligence.domain.skills.SkillUsageEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batched skill usage log (scheme §1.4 `skill_usage_log`).
 *
 * Skill outcomes are buffered in memory while a session runs — they are never written one by one,
 * which would burn flash I/O on every tool call. At end-of-session housekeeping the whole buffer is
 * flushed as a single atomic batch append to `skills/usage-log.jsonl`, matching the same batching
 * pattern used by every other operation in the self-improving memory system.
 */
interface SkillUsageLogRepository {
    /**
     * Buffer a usage entry for the current session. Nothing touches disk until [flush] runs.
     * Thread-safe; safe to call from the hot agent loop.
     */
    suspend fun recordUsage(
        skillName: String,
        sessionId: String,
        outcome: Boolean,
        notes: String? = null
    )

    /**
     * Append every buffered entry as one batch write and clear the buffer. Returns the number of
     * entries flushed. Idempotent and non-fatal — a failure simply keeps the buffer for the next
     * flush attempt.
     */
    suspend fun flush(): Int

    /** Read the most recently flushed entries (newest first). Purely diagnostic/UI-facing. */
    suspend fun listRecent(limit: Int = 50): List<SkillUsageEntry>

    /** Drop any unflushed entries (e.g. when a session is abandoned). */
    suspend fun clearBuffer()
}

@Singleton
class FileSkillUsageLogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SkillUsageLogRepository {
    private val file: File get() = File(context.filesDir, "skills/usage-log.jsonl")
    private val lock = Any()
    private val buffer = mutableListOf<SkillUsageEntry>()

    override suspend fun recordUsage(
        skillName: String,
        sessionId: String,
        outcome: Boolean,
        notes: String?
    ) = withContext(Dispatchers.IO) {
        val name = skillName.trim()
        if (name.isBlank() || sessionId.isBlank()) return@withContext
        synchronized(lock) {
            buffer += SkillUsageEntry(
                skillName = name.take(120),
                sessionId = sessionId.take(64),
                outcome = outcome,
                notes = notes?.trim()?.take(200)?.takeIf(String::isNotEmpty)
            )
            // Bound the in-memory buffer so a pathological turn can't grow it unbounded.
            // (subList().clear() instead of removeRange: the latter isn't resolved on this stdlib.)
            if (buffer.size > MAX_BUFFER_ENTRIES) {
                val excess = buffer.size - MAX_BUFFER_ENTRIES
                buffer.subList(0, excess).clear()
            }
        }
    }

    override suspend fun flush(): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (buffer.isEmpty()) return@withContext 0
            val batch = buffer.toList()
            buffer.clear()
            runCatching {
                file.parentFile?.mkdirs()
                // One append call = one atomic batch write of the whole session's usage log.
                file.appendText(batch.joinToString("\n") { it.toJson().toString() } + "\n")
            }.onFailure {
                // Keep the batch for the next flush attempt instead of losing the observations.
                synchronized(lock) { buffer.addAll(0, batch) }
                return@withContext 0
            }
            batch.size
        }
    }

    override suspend fun listRecent(limit: Int): List<SkillUsageEntry> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@withContext emptyList()
            // The log is append-only, so the file order IS the chronological order. Reversing the
            // lines gives a deterministic newest-first view without relying on millisecond-precision
            // createdAt timestamps (two records in the same millisecond must not tie-break arbitrarily).
            file.readLines()
                .mapNotNull { line ->
                    runCatching { JSONObject(line).toSkillUsageEntry() }.getOrNull()
                }
                .asReversed()
                .take(limit.coerceIn(1, 500))
        }.getOrDefault(emptyList())
    }

    override suspend fun clearBuffer() {
        synchronized(lock) { buffer.clear() }
    }

    private fun SkillUsageEntry.toJson(): JSONObject = JSONObject()
        .put("skillName", skillName)
        .put("sessionId", sessionId)
        .put("outcome", outcome)
        .put("notes", notes)
        .put("createdAt", createdAt)

    private fun JSONObject.toSkillUsageEntry(): SkillUsageEntry = SkillUsageEntry(
        skillName = optString("skillName"),
        sessionId = optString("sessionId"),
        outcome = optBoolean("outcome", true),
        notes = if (has("notes") && !isNull("notes")) optString("notes") else null,
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    companion object {
        private const val MAX_BUFFER_ENTRIES = 512
    }
}
