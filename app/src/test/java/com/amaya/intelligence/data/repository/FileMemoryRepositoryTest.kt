package com.amaya.intelligence.data.repository

import android.content.ContextWrapper
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryContentNormalizer
import com.amaya.intelligence.domain.memory.MemoryDeduper
import com.amaya.intelligence.domain.memory.MemoryScope
import com.amaya.intelligence.domain.memory.MemorySafetyFilter
import com.amaya.intelligence.domain.memory.MemoryStatus
import com.amaya.intelligence.domain.memory.MemoryType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileMemoryRepositoryTest {
    private val root = Files.createTempDirectory("amaya-memory-test-").toFile()
    private val testContext = object : ContextWrapper(null) { override fun getFilesDir(): File = root }
    private val classifier = MemoryClassifier(MemorySafetyFilter(), MemoryContentNormalizer())
    private val repository = FileMemoryRepository(
        context = testContext,
        classifier = classifier,
        deduper = MemoryDeduper(),
        workspaceStore = FileWorkspaceMemoryStore(testContext),
        settingsManager = AiSettingsManager(testContext),
        embeddingClient = EmbeddingClient()
    )

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `new fact supersedes old identity and stale update fails`() = runBlocking {
        repository.applyProposal(proposal("The user prefers Indonesian responses.")).getOrThrow()
        val first = repository.listMemoryRecords(type = MemoryType.USER_PROFILE).single()

        repository.applyProposal(proposal("The user prefers English responses.")).getOrThrow()
        val current = repository.listMemoryRecords(type = MemoryType.USER_PROFILE).single()
        assertEquals(first.id, current.id)
        assertEquals(first.version + 1, current.version)
        assertEquals("The user prefers English for responses.", current.content)
        assertEquals(MemoryStatus.ACTIVE, current.status)
        val persisted = File(root, "memory/records.jsonl").readLines().map(::JSONObject)
        assertEquals(1, persisted.count { it.optString("status") == "ACTIVE" })
        assertTrue(persisted.any { it.optString("status") == "SUPERSEDED" })

        assertTrue(repository.updateMemoryById(current.id, "The user prefers Indonesian responses.", first.version).isFailure)
        assertEquals("The user prefers English for responses.", repository.listMemoryRecords(type = MemoryType.USER_PROFILE).single().content)
    }

    @Test
    fun `manual deletion hides memory from context`() = runBlocking {
        repository.applyProposal(proposal("The user prefers Indonesian responses.")).getOrThrow()
        val record = repository.listMemoryRecords(MemoryType.USER_PROFILE).single()

        repository.deleteMemoryById(record.id, record.version).getOrThrow()

        assertTrue(repository.listMemoryRecords(MemoryType.USER_PROFILE).isEmpty())
        assertFalse(repository.readUserProfile().contains("Indonesian"))
    }

    @Test
    fun `same millisecond update keeps newest active revision`() = runBlocking {
        val fixedTime = 1234L
        val first = proposal("The user prefers Indonesian responses.").copy(createdAt = fixedTime)
        val second = proposal("The user prefers English responses.").copy(createdAt = fixedTime)
        repository.applyProposal(first).getOrThrow()
        repository.applyProposal(second).getOrThrow()
        assertEquals("The user prefers English for responses.", repository.listMemoryRecords(MemoryType.USER_PROFILE).single().content)
    }

    @Test
    fun `workspace records are isolated and null workspace returns none`() = runBlocking {
        val workspaceA = File(root, "workspace-a").apply { mkdirs() }.canonicalPath
        val workspaceB = File(root, "workspace-b").apply { mkdirs() }.canonicalPath
        repository.applyProposal(proposal("The project uses Gradle.", workspaceA)).getOrThrow()
        repository.applyProposal(proposal("The project uses Maven.", workspaceB)).getOrThrow()

        assertEquals(listOf("The project uses Gradle."), repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = workspaceA).map { it.content })
        assertEquals(listOf("The project uses Maven."), repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = workspaceB).map { it.content })
        assertTrue(repository.listMemoryRecords(MemoryType.WORKSPACE_FACT).isEmpty())
        assertFalse(repository.readWorkspaceFacts(null).contains("Gradle"))
        assertFalse(repository.readWorkspaceFacts(null).contains("Maven"))
    }

    @Test
    fun `workspace root can be explicitly remapped`() = runBlocking {
        val oldRoot = File(root, "old-workspace").apply { mkdirs() }.canonicalPath
        repository.applyProposal(proposal("The project uses Gradle.", oldRoot)).getOrThrow()
        val binding = repository.listWorkspaceBindings().single()
        assertEquals(binding.id, repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = oldRoot).single().workspaceId)
        File(oldRoot).deleteRecursively()
        val newRoot = File(root, "moved-workspace").apply { mkdirs() }.canonicalPath

        repository.remapWorkspace(binding.id, newRoot).getOrThrow()

        assertTrue(repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = oldRoot).isEmpty())
        assertEquals("The project uses Gradle.", repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = newRoot).single().content)
    }

    @Test
    fun `legacy markdown imports once and daily files are removed`() = runBlocking {
        val memoryDir = File(root, "memory").apply { mkdirs() }
        File(memoryDir, "USER.md").writeText("# User\n\n- The user prefers concise responses.\n")
        File(memoryDir, "MEMORY.md").writeText("# Memory\n\n- Random catch-all note.\n- The user prefers English responses.\n")
        File(memoryDir, "2026-01-01.md").writeText("# Daily Notes\n\n- legacy\n")

        val records = repository.listMemoryRecords(MemoryType.USER_PROFILE)
        repository.listMemoryRecords(MemoryType.USER_PROFILE)

        assertEquals(2, records.size)
        assertEquals(2, File(memoryDir, "records.jsonl").readLines().size)
        assertFalse(records.any { it.content == "Random catch-all note." })
        assertTrue(!File(memoryDir, "2026-01-01.md").exists())
        assertTrue(File(memoryDir, ".structured-memory-v1").exists())
    }

    @Test
    fun `legacy project markdown imports into explicit unmapped workspace`() = runBlocking {
        val memoryDir = File(root, "memory").apply { mkdirs() }
        File(memoryDir, "PROJECT.md").writeText("# Project\n\n- The project uses Gradle.\n")
        val binding = repository.listWorkspaceBindings().single()
        assertFalse(binding.rootExists)
        assertEquals(1, binding.recordCount)
        assertTrue(repository.listMemoryRecords(MemoryType.WORKSPACE_FACT).isEmpty())
        assertEquals("The project uses Gradle.", repository.listMemoryRecords(MemoryType.WORKSPACE_FACT, workspacePath = binding.root).single().content)
    }

    @Test
    fun `compaction keeps at most five revisions`() = runBlocking {
        repeat(7) { index ->
            repository.applyProposal(proposal("The user prefers response style $index.")).getOrThrow()
        }
        repository.compactStoredMemory().getOrThrow()
        val revisions = File(root, "memory/records.jsonl").readLines().map(::JSONObject)
        assertEquals(5, revisions.size)
        assertEquals(1, revisions.count { it.optString("status") == "ACTIVE" })
    }

    @Test
    fun `removed important memory is purged after prior migration`() = runBlocking {
        val memoryDir = File(root, "memory").apply { mkdirs() }
        File(memoryDir, ".structured-memory-v1").writeText("completed")
        File(memoryDir, "records.jsonl").writeText(JSONObject()
            .put("id", "legacy")
            .put("type", "LONG_TERM_MEMORY")
            .put("status", "ACTIVE")
            .put("content", "catch all")
            .toString() + "\n")
        assertTrue(repository.listMemoryRecords().isEmpty())
        assertTrue(File(memoryDir, "records.jsonl").readText().isBlank())
    }

    @Test
    fun `structured records survive repository restart`() = runBlocking {
        repository.applyProposal(proposal("The user prefers concise responses.")).getOrThrow()
        val restarted = FileMemoryRepository(
            context = testContext,
            classifier = classifier,
            deduper = MemoryDeduper(),
            workspaceStore = FileWorkspaceMemoryStore(testContext),
            settingsManager = AiSettingsManager(testContext),
            embeddingClient = EmbeddingClient()
        )
        assertEquals("The user prefers concise responses.", restarted.listMemoryRecords(MemoryType.USER_PROFILE).single().content)
    }

    private fun proposal(content: String, workspacePath: String? = null) = classifier.classify(
        content = content,
        requestedType = if (workspacePath == null) MemoryType.USER_PROFILE else MemoryType.WORKSPACE_FACT,
        requestedAction = MemoryAction.ADD,
        requestedScope = if (workspacePath == null) MemoryScope.USER else MemoryScope.WORKSPACE,
        requestedTitle = if (workspacePath == null) "Response language" else "Build system",
        workspacePath = workspacePath,
        sourceConversationId = "test-session"
    )
}
