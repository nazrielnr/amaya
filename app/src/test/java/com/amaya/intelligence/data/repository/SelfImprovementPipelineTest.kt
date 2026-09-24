package com.amaya.intelligence.data.repository

import android.content.ContextWrapper
import com.amaya.intelligence.data.local.files.FileSkillStore
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryContentNormalizer
import com.amaya.intelligence.domain.memory.MemoryDeduper
import com.amaya.intelligence.domain.memory.MemorySafetyFilter
import com.amaya.intelligence.domain.skills.SkillPatchApplier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SelfImprovementPipelineTest {
    private val root = Files.createTempDirectory("self-improvement-").toFile()
    private val context = object : ContextWrapper(null) { override fun getFilesDir(): File = root }
    private val classifier = MemoryClassifier(MemorySafetyFilter(), MemoryContentNormalizer())
    private val pending = RecordingPendingRepository()
    private val memoryRepository = FileMemoryRepository(
        context = context,
        classifier = classifier,
        deduper = MemoryDeduper(),
        workspaceStore = FileWorkspaceMemoryStore(context),
        settingsManager = AiSettingsManager(context),
        embeddingClient = EmbeddingClient()
    )
    private val pipeline = SelfImprovementPipeline(
        classifier = classifier,
        pendingProposalRepository = pending,
        memoryRepository = memoryRepository,
        primedStateRepository = FilePrimedStateRepository(context, AiSettingsManager(context), EmbeddingClient()),
        skillRepository = FileSkillRepository(FileSkillStore(context), classifier, SkillPatchApplier()),
        projectStateRepository = FileProjectStateRepository(context),
        androidCapabilityRepository = FileAndroidCapabilityRepository(context),
        recommendationRepository = FileRecommendationRepository(context, memoryRepository),
        skillUsageLogRepository = FileSkillUsageLogRepository(context),
        context = context
    )

    @After fun cleanUp() { root.deleteRecursively() }

    @Test
    fun `ordinary user messages never create hidden about you proposals`() = kotlinx.coroutines.runBlocking {
        // Note: "Remember that I prefer …" deliberately matches a USER durable-fact marker and
        // SHOULD propose a preference fact (approval-gated). A truly ordinary message must not.
        val result = pipeline.analyzeAndImprove(
            interaction("one", successful = true).copy(userMessages = listOf("Let's move on to the next task."), toolCalls = emptyList())
        )
        assertTrue(result.skillProposals.isEmpty())
        assertTrue(pending.proposals.isEmpty())
    }

    @Test
    fun `failed build turn suggests an evidence-gated build-fix recommendation`() = kotlinx.coroutines.runBlocking {
        val workspace = File(root, "workspace").apply { mkdirs() }.canonicalPath
        pipeline.analyzeAndImprove(
            interaction("one", successful = false).copy(
                workspacePath = workspace,
                toolResults = listOf("FAILURE: Build failed with an exception.")
            )
        )
        val recommendations = FileRecommendationRepository(context, memoryRepository).list(workspacePath = workspace)
        val buildFix = recommendations.firstOrNull { it.title == "Fix the failed build" }
        assertTrue("expected a build-fix recommendation", buildFix != null)
        assertTrue(buildFix!!.verificationRule.contains("build successful"))
    }

    @Test
    fun `one successful workflow creates no skill proposal`() {
        assertTrue(pipeline.extractSkillCandidates(interaction("one", successful = true)).isEmpty())
    }

    @Test
    fun `same successful workflow across sessions creates reviewed proposal`() {
        assertTrue(pipeline.extractSkillCandidates(interaction("one", successful = true)).isEmpty())
        val proposals = pipeline.extractSkillCandidates(interaction("two", successful = true))
        assertEquals(1, proposals.size)
        assertEquals(listOf("one", "two"), proposals.single().sourceSessionIds)
    }

    @Test
    fun `repeated failures then recovery creates patch only for viewed skill`() {
        assertTrue(pipeline.extractSkillCandidates(interaction("fail-1", false, "skill:name=android-build")).isEmpty())
        // Two identical failures now trigger the approval-gated failure lesson (scheme §2), not a patch.
        assertEquals(1, pipeline.extractSkillCandidates(interaction("fail-2", false, "skill:name=android-build")).size)
        val proposals = pipeline.extractSkillCandidates(interaction("recovered", true, "skill:name=android-build"))
        assertEquals(1, proposals.size)
        assertEquals("android-build", proposals.single().target)
    }

    private fun interaction(session: String, successful: Boolean, skillCall: String? = null) = CompletedInteractionContext(
        sessionId = session,
        userMessages = listOf("Fix Android build"),
        assistantMessages = listOf("Completed"),
        toolCalls = listOfNotNull(skillCall, "read_file:path=build.gradle", "workspace_change:path=build.gradle"),
        toolResults = if (successful) listOf("completed") else listOf("failed"),
        timestamp = System.currentTimeMillis(),
        successful = successful
    )

    private class RecordingPendingRepository : PendingProposalRepository {
        val proposals = mutableListOf<com.amaya.intelligence.domain.memory.PendingProposal>()
        override suspend fun addProposal(proposal: com.amaya.intelligence.domain.memory.PendingProposal): Result<Unit> {
            proposals += proposal
            return Result.success(Unit)
        }
        override suspend fun listPending(limit: Int) = emptyList<com.amaya.intelligence.domain.memory.PendingProposal>()
        override suspend fun approve(id: String) = Result.success(Unit)
        override suspend fun reject(id: String) = Result.success(Unit)
        override suspend fun applyApproved(id: String) = Result.success(Unit)
        override suspend fun applyApprovedWithResult(id: String): Result<ProposalApplyResult> = Result.failure(UnsupportedOperationException())
        override suspend fun applyAllApproved() = Result.success(0)
        override suspend fun applyAllApprovedWithResults() = Result.success(emptyList<ProposalApplyResult>())
        override suspend fun expireOldProposals(maxAgeDays: Int) = Result.success(0)
    }
}
