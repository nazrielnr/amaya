package com.amaya.intelligence.data.repository

import android.content.ContextWrapper
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryContentNormalizer
import com.amaya.intelligence.domain.memory.MemoryDeduper
import com.amaya.intelligence.domain.memory.MemoryProposal
import com.amaya.intelligence.domain.memory.MemorySafetyFilter
import com.amaya.intelligence.domain.memory.MemoryScope
import com.amaya.intelligence.domain.memory.MemoryType
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationPriority
import com.amaya.intelligence.domain.memory.RecommendationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecommendationRepositoryTest {
    private val root = Files.createTempDirectory("amaya-recommendation-test-").toFile()
    private val testContext = object : ContextWrapper(null) { override fun getFilesDir(): File = root }
    private val classifier = MemoryClassifier(MemorySafetyFilter(), MemoryContentNormalizer())
    private val memoryRepository = FileMemoryRepository(
        context = testContext,
        classifier = classifier,
        deduper = MemoryDeduper(),
        workspaceStore = FileWorkspaceMemoryStore(testContext),
        settingsManager = AiSettingsManager(testContext),
        embeddingClient = EmbeddingClient()
    )
    private val repository = FileRecommendationRepository(testContext, memoryRepository)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    // Receiver lambda: inside `run { }` the implicit receiver is the runBlocking scope, so
    // CoroutineScope extensions like async() resolve without an explicit receiver.
    private fun run(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    @Test
    fun `suggest creates suggested recommendation and dedupes active titles`() = run {
        val id = repository.suggest("/ws", "Support arm64-v8a", rationale = "Release target", priority = RecommendationPriority.HIGH)
            .getOrThrow()
        val record = repository.get(id)!!
        assertEquals(RecommendationStatus.SUGGESTED, record.status)
        assertEquals(RecommendationPriority.HIGH, record.priority)
        assertTrue(record.rationale.contains("Release"))

        assertTrue(repository.suggest("/ws", "  support arm64-v8a  ").isFailure)
        // Same title under a different workspace is a different recommendation.
        assertTrue(repository.suggest("/other", "Support arm64-v8a").isSuccess)
    }

    @Test
    fun `lifecycle transitions are guarded`() = run {
        val id = repository.suggest("/ws", "Migrate to SQLite").getOrThrow()

        // Can't skip stages.
        assertTrue(repository.transition(id, RecommendationStatus.IN_PROGRESS).isFailure)
        assertTrue(repository.transition(id, RecommendationStatus.VERIFIED).isFailure)
        assertTrue(repository.transition(id, RecommendationStatus.COMPLETED).isFailure)

        assertTrue(repository.transition(id, RecommendationStatus.ACCEPTED).isSuccess)
        assertTrue(repository.transition(id, RecommendationStatus.IN_PROGRESS).isSuccess)
        assertTrue(repository.transition(id, RecommendationStatus.VERIFIED).isSuccess)
        assertTrue(repository.transition(id, RecommendationStatus.COMPLETED).isSuccess)

        // Completed is terminal history.
        assertTrue(repository.transition(id, RecommendationStatus.ARCHIVED).isFailure)
        assertEquals(RecommendationStatus.COMPLETED, repository.get(id)!!.status)
    }

    @Test
    fun `archive drops from any active stage`() = run {
        val id = repository.suggest("/ws", "Support arm64-v8a").getOrThrow()
        assertTrue(repository.archive(id).isSuccess)
        assertEquals(RecommendationStatus.ARCHIVED, repository.get(id)!!.status)
        assertTrue(repository.list(workspacePath = "/ws", statuses = Recommendation.ACTIVE_STATUSES).isEmpty())
    }

    @Test
    fun `verify links evidence back to related memories`() = run {
        val workspace = File(root, "ws-evidence").apply { mkdirs() }.canonicalPath
        val proposal = MemoryProposal(
            type = MemoryType.WORKSPACE_FACT,
            action = MemoryAction.ADD,
            scope = MemoryScope.WORKSPACE,
            title = "armv7a needs dependency X",
            content = "The armv7a build requires dependency X version Y.",
            reason = "test",
            confidence = 0.9,
            workspacePath = workspace
        )
        memoryRepository.applyProposal(proposal).getOrThrow()
        val memoryId = proposal.id

        val id = repository.suggest(
            workspace,
            "Fix armv7a build",
            verificationRule = "armv7a, build successful",
            relatedMemoryIds = listOf(memoryId)
        ).getOrThrow()
        assertTrue(repository.transition(id, RecommendationStatus.ACCEPTED).isSuccess)
        assertTrue(repository.verify(id, "armv7a build successful now").isSuccess)

        val record = memoryRepository.listMemoryRecords(workspacePath = workspace).first { it.id == memoryId }
        assertTrue(
            "Expected provenance evidence linked to memory, got: ${record.evidence}",
            record.evidence.any { it.contains("Fix armv7a build") && it.contains("armv7a build successful") }
        )
    }

    @Test
    fun `verify requires the verification rule to match the evidence`() = run {
        val id = repository.suggest(
            "/ws",
            "Support arm64-v8a",
            verificationRule = "arm64-v8a, build successful"
        ).getOrThrow()

        // Verification is only possible after acceptance.
        assertTrue(repository.verify(id, "arm64-v8a is in the manifest; build successful").isFailure)

        assertTrue(repository.transition(id, RecommendationStatus.ACCEPTED).isSuccess)

        // Partial evidence does not satisfy the rule.
        assertTrue(repository.verify(id, "arm64-v8a added to ndk abiFilters").isFailure)
        // Full evidence satisfies the rule and appends provenance.
        val verified = repository.verify(id, "arm64-v8a added; build successful (assembleDebug)").getOrThrow()
        assertEquals(RecommendationStatus.VERIFIED, verified.status)
        assertEquals(1, verified.evidence.size)
        assertEquals(verified.id, repository.get(id)!!.id)
    }

    @Test
    fun `blank rule accepts any non-blank evidence`() = run {
        val id = repository.suggest("/ws", "Resolve: widget flickers").getOrThrow()
        assertTrue(repository.transition(id, RecommendationStatus.IN_PROGRESS).isFailure)
        assertTrue(repository.transition(id, RecommendationStatus.ACCEPTED).isSuccess)
        assertTrue(repository.transition(id, RecommendationStatus.IN_PROGRESS).isSuccess)
        val verified = repository.verify(id, "Reproduced and fixed the flicker").getOrThrow()
        assertEquals(RecommendationStatus.VERIFIED, verified.status)
        assertTrue(repository.verify(id, "   ").isFailure)
    }

    @Test
    fun `completed is a user claim while verified is evidence-gated`() = run {
        val userClaimed = repository.suggest("/ws", "Add dark mode toggle").getOrThrow()
        assertTrue(repository.transition(userClaimed, RecommendationStatus.ACCEPTED).isSuccess)
        val completed = repository.transition(userClaimed, RecommendationStatus.COMPLETED).getOrThrow()
        assertEquals(RecommendationStatus.COMPLETED, completed.status)
        assertFalse(repository.get(userClaimed)!!.evidence.isNotEmpty())
    }

    @Test
    fun `renderForContext lists only active recommendations`() = run {
        val active = repository.suggest("/ws", "Support arm64-v8a", rationale = "Release target").getOrThrow()
        val done = repository.suggest("/ws", "Clean up dead code").getOrThrow()
        // The guarded lifecycle (canTransition) requires ACCEPTED before COMPLETED.
        assertTrue(repository.transition(done, RecommendationStatus.ACCEPTED).isSuccess)
        assertTrue(repository.transition(done, RecommendationStatus.COMPLETED).isSuccess)

        val rendered = repository.renderForContext("/ws")
        assertTrue(rendered.contains("Support arm64-v8a"))
        assertTrue(rendered.contains("Release target"))
        assertFalse(rendered.contains("Clean up dead code"))

        // Completed/archived recommendations stop appearing.
        assertTrue(repository.archive(active).isSuccess)
        assertTrue(repository.renderForContext("/ws").isBlank())
    }

    @Test
    fun `suggest is persisted across repository instances`() = run {
        repository.suggest("/ws", "Persist across restarts").getOrThrow()
        val reopened = FileRecommendationRepository(testContext, memoryRepository)
        val record = reopened.list(workspacePath = "/ws").single()
        assertEquals("Persist across restarts", record.title)
        assertTrue(File(root, "memory/recommendations.jsonl").exists())
    }

    @Test
    fun `concurrent suggests all persist without loss`() = run {
        val total = 40
        val results = (0 until total).map { index ->
            async(Dispatchers.Default) { repository.suggest("/ws", "Concurrent task $index") }
        }.awaitAll()
        // Every concurrent write succeeded — the file lock + atomic rewrite never drops a record.
        assertTrue("Expected all suggests to succeed, got failures: ${results.count { it.isFailure }}", results.all { it.isSuccess })
        assertEquals(total, repository.list(workspacePath = "/ws", limit = 200).size)
    }

    @Test
    fun `concurrent verify has exactly one winner`() = run {
        val id = repository.suggest(
            "/ws",
            "Ship arm64 release",
            verificationRule = "build successful"
        ).getOrThrow()
        assertTrue(repository.transition(id, RecommendationStatus.ACCEPTED).isSuccess)

        val attempts = (0 until 20).map {
            async(Dispatchers.Default) { repository.verify(id, "build successful — release APK produced") }
        }.awaitAll()
        // VERIFIED is terminal for verification: exactly one transition wins, the rest are rejected.
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(RecommendationStatus.VERIFIED, repository.get(id)!!.status)
    }
}
