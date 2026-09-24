package com.amaya.intelligence.data.repository

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SkillUsageLogRepositoryTest {
    private val root = Files.createTempDirectory("amaya-usage-log-test-").toFile()
    private val context = object : ContextWrapper(null) { override fun getFilesDir(): File = root }
    private val repository = FileSkillUsageLogRepository(context)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun `recordUsage buffers without touching disk until flush`() = run {
        val file = File(root, "skills/usage-log.jsonl")
        repository.recordUsage("git-patch", "session-1", outcome = true)
        repository.recordUsage("git-patch", "session-1", outcome = false)
        // Nothing written on record — batching is the whole point (scheme §1.4).
        assertFalse(file.exists())

        assertEquals(2, repository.flush())
        assertTrue(file.exists())
        assertEquals(2, repository.listRecent().size)
        // A second flush with an empty buffer writes nothing.
        assertEquals(0, repository.flush())
    }

    @Test
    fun `flush appends across sessions newest first`() = run {
        repository.recordUsage("skill-a", "s1", outcome = true)
        assertEquals(1, repository.flush())
        repository.recordUsage("skill-b", "s2", outcome = false, notes = "boom")
        assertEquals(1, repository.flush())

        val entries = repository.listRecent()
        assertEquals(2, entries.size)
        assertEquals("skill-b", entries[0].skillName)
        assertFalse(entries[0].outcome)
        assertEquals("boom", entries[0].notes)
        assertEquals("skill-a", entries[1].skillName)
    }

    @Test
    fun `clearBuffer drops unflushed entries`() = run {
        repository.recordUsage("skill-a", "s1", outcome = true)
        repository.clearBuffer()
        assertEquals(0, repository.flush())
        assertFalse(File(root, "skills/usage-log.jsonl").exists())
    }
}
