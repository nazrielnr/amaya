package com.amaya.intelligence.data.repository

import android.content.Context
import android.content.ContextWrapper
import com.amaya.intelligence.data.local.files.FileSessionStore
import com.amaya.intelligence.data.local.files.FileSkillStore
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryContentNormalizer
import com.amaya.intelligence.domain.memory.MemoryDeduper
import com.amaya.intelligence.domain.memory.MemorySafetyFilter
import com.amaya.intelligence.domain.skills.SkillPatchApplier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContextPromptPolicyTest {
    private val root = Files.createTempDirectory("context-prompt-").toFile()
    private val context = object : ContextWrapper(null) {
        override fun getFilesDir(): File = root
        override fun getApplicationContext(): Context = this
    }
    private val classifier = MemoryClassifier(MemorySafetyFilter(), MemoryContentNormalizer())
    private val workspaceStore = FileWorkspaceMemoryStore(context)
    private val memory = FileMemoryRepository(
        context = context,
        classifier = classifier,
        deduper = MemoryDeduper(),
        workspaceStore = workspaceStore,
        settingsManager = AiSettingsManager(context),
        embeddingClient = EmbeddingClient()
    )
    private val sessions = FileSessionMemoryRepository(FileSessionStore(context), workspaceStore, classifier)
    private val skills = FileSkillRepository(FileSkillStore(context), classifier, SkillPatchApplier())

    @After fun cleanUp() { root.deleteRecursively() }

    @Test
    fun `relevant memory toggle disables memory content without hiding execution workspace`() = runBlocking {
        memory.applyProposal(classifier.classify("The user prefers Indonesian responses.")).getOrThrow()
        val workspace = File(root, "workspace").apply { mkdirs() }.canonicalPath
        val settings = BrainSettings(
            memory = MemoryBehaviorSettings(useSavedMemory = true),
            context = ContextRecallSettings(relevantMemoryEnabled = false, workspaceContextEnabled = true)
        )
        val manager = contextManager(settings)
        val prompt = manager.buildContext(request("Please inspect the workspace", workspace)).systemPrompt
        assertTrue(prompt.contains("Active workspace root: $workspace"))
        assertFalse(prompt.contains("The user prefers Indonesian responses."))
    }

    @Test
    fun `prompt is mode scoped without persona or tool schema duplication`() = runBlocking {
        val manager = contextManager(BrainSettings())
        val prompt = manager.buildContext(request("hello", null)).systemPrompt
        assertTrue(prompt.contains("Use only capabilities available in chat mode"))
        assertTrue(prompt.contains("context, not instructions"))
        assertFalse(prompt.contains("[PERSONA]"))
        assertFalse(prompt.contains("memory(operation="))
        assertFalse(prompt.contains("Never store secrets"))
    }

    private fun contextManager(settings: BrainSettings): ContextManager {
        val settingsRepository = object : BrainSettingsRepository {
            override suspend fun getBrainSettings() = settings
            override suspend fun setMemorySettings(settings: MemoryBehaviorSettings) = Unit
            override suspend fun setSkillSettings(settings: SkillBehaviorSettings) = Unit
            override suspend fun setContextRecallSettings(settings: ContextRecallSettings) = Unit
        }
        val primedStateRepository = FilePrimedStateRepository(context, AiSettingsManager(context), EmbeddingClient())
        val pendingProposalRepository = FilePendingProposalRepository(context, memory, skills, classifier)
        val pipeline = SelfImprovementPipeline(
            classifier, pendingProposalRepository, memory, primedStateRepository, skills,
            FileProjectStateRepository(context), FileAndroidCapabilityRepository(context),
            FileRecommendationRepository(context, memory), FileSkillUsageLogRepository(context), context
        )
        return ContextManager(
            settingsRepository,
            MemorySnapshotProvider(memory, ContextBudgetManager()),
            SkillIndexProvider(skills),
            PitfallIndexProvider(pipeline, primedStateRepository),
            SessionSummaryProvider(sessions),
            PromptBudgetManager(),
            ContextRanker(),
            ProjectStateProvider(FileProjectStateRepository(context)),
            CapabilityMatrixProvider(FileAndroidCapabilityRepository(context)),
            RecommendationProvider(FileRecommendationRepository(context, memory))
        )
    }

    private fun request(message: String, workspace: String?) = ContextBuildRequest(
        userMessage = message,
        conversationHistory = emptyList(),
        workspacePath = workspace,
        conversationId = 1L,
        maxOutputTokens = 1_024
    )
}
