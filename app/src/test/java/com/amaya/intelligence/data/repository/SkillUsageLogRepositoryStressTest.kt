package com.amaya.intelligence.data.repository

import android.content.ContextWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Stress coverage for the buffered batch usage log (scheme §1.4). The repo is deliberately written
 * so that per-session outcomes are only flushed once; these tests hammer that contract: concurrent
 * producers from real threads must never lose or duplicate entries, a large batch must round-trip
 * through a fresh repository instance, and a corrupt line must not break the append log.
 */
class SkillUsageLogRepositoryStressTest {
    private val root = Files.createTempDirectory("amaya-usage-stress-").toFile()
    private val context = object : ContextWrapper(null) { override fun getFilesDir(): File = root }
    private val repository = FileSkillUsageLogRepository(context)
    private val file: File get() = File(root, "skills/usage-log.jsonl")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `concurrent producers lose no entries and flush as one batch`() = runBlocking {
        // Total stays under the repository's MAX_BUFFER_ENTRIES (512) so the "never lose an entry"
        // contract is what is being asserted — the cap is a separate pathological-turn safety bound.
        val writers = 8
        val perWriter = 48
        (0 until writers).map { writer ->
            async(Dispatchers.Default) {
                repeat(perWriter) { i ->
                    repository.recordUsage("skill-$writer", "session-stress", outcome = i % 3 != 0)
                }
            }
        }.awaitAll()

        val flushed = repository.flush()
        assertEquals(writers * perWriter, flushed)
        assertTrue(file.exists())
        // Every flushed entry is exactly one JSONL line.
        assertEquals(writers * perWriter, file.readLines().size)
        assertEquals(writers * perWriter, repository.listRecent(limit = 10_000).size)
        // An empty buffer flushes nothing.
        assertEquals(0, repository.flush())
    }

    @Test
    fun `large batch round trips through a fresh repository instance`() = runBlocking {
        // Under the 512-entry in-memory cap so every recorded entry is flushed and round-trips.
        val total = 400
        repeat(total) { i ->
            repository.recordUsage("skill-$i", "session-$i", outcome = true, notes = "note $i")
        }
        assertEquals(total, repository.flush())

        val reopened = FileSkillUsageLogRepository(context)
        val entries = reopened.listRecent(limit = 10_000)
        assertEquals(total, entries.size)
        assertEquals("skill-0", entries.last().skillName)
        assertEquals("skill-${total - 1}", entries.first().skillName)
        assertEquals("note ${total - 1}", entries.first().notes)
    }

    @Test
    fun `corrupt lines are skipped and do not break later flushes`() = runBlocking {
        repository.recordUsage("good", "s1", outcome = true)
        assertEquals(1, repository.flush())

        // A torn write / manual edit corrupts one line.
        file.appendText("{definitely-not-json}\n")

        assertEquals(1, repository.listRecent().size) // corrupt line skipped, valid entry kept

        repository.recordUsage("good-2", "s2", outcome = false)
        assertEquals(1, repository.flush())
        assertEquals(2, repository.listRecent(limit = 10).size)
    }
}
