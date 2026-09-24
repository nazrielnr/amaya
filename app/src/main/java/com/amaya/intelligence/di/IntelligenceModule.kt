package com.amaya.intelligence.di

import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.ai.PendingClarification
import com.amaya.intelligence.domain.models.ConversationMode
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.di.ApplicationScope
import com.amaya.intelligence.impl.local.LocalIntelligenceService
import com.amaya.intelligence.impl.bridge.windows.services.WindowsBridgeIntelligenceService
import com.amaya.intelligence.impl.ide.antigravity.services.AntigravityIntelligenceService
import com.amaya.intelligence.impl.ide.opencode.services.OpencodeIntelligenceService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Named("local")
    @Singleton
    fun provideLocalService(service: LocalIntelligenceService): IntelligenceService = service

    @Provides
    @Named("windows_bridge")
    @Singleton
    fun provideWindowsBridgeService(service: WindowsBridgeIntelligenceService): IntelligenceService = service

    @Provides
    @Named("antigravity")
    @Singleton
    fun provideAntigravityService(service: AntigravityIntelligenceService): IntelligenceService = service

    @Provides
    @Named("opencode")
    @Singleton
    fun provideOpencodeService(service: OpencodeIntelligenceService): IntelligenceService = service

    /**
     * Provides the active IntelligenceService based on SessionManager.
     * Note: This provides a "Delegate" that resolves the service dynamically.
     */
    @Provides
    @Singleton
    fun provideActiveIntelligenceService(
        sessionManager: IntelligenceSessionManager,
        @ApplicationScope appScope: CoroutineScope,
        @Named("local") localService: IntelligenceService,
        @Named("windows_bridge") windowsBridgeService: IntelligenceService,
        @Named("antigravity") antigravityService: IntelligenceService,
        @Named("opencode") opencodeService: IntelligenceService
    ): IntelligenceService {
        return object : IntelligenceService {
            private val active: IntelligenceService
                get() = when (sessionManager.currentMode.value) {
                    IntelligenceSessionManager.SessionMode.LOCAL -> localService
                    IntelligenceSessionManager.SessionMode.WINDOWS_BRIDGE -> windowsBridgeService
                    IntelligenceSessionManager.SessionMode.OPENCODE -> opencodeService
                    else -> antigravityService
                }

            @OptIn(ExperimentalCoroutinesApi::class)
            private fun <T> switchedFlow(
                selector: (IntelligenceService) -> StateFlow<T>,
                initial: T
            ): StateFlow<T> {
                return sessionManager.currentMode
                    .flatMapLatest { mode ->
                        val service = when (mode) {
                            IntelligenceSessionManager.SessionMode.LOCAL -> localService
                            IntelligenceSessionManager.SessionMode.WINDOWS_BRIDGE -> windowsBridgeService
                            IntelligenceSessionManager.SessionMode.OPENCODE -> opencodeService
                            else -> antigravityService
                        }
                        selector(service)
                    }
                    .stateIn(appScope, SharingStarted.Eagerly, initial)
            }

            override val uiState = switchedFlow(
                selector = { it.uiState },
                initial = ChatUiState(sessionMode = sessionManager.currentMode.value)
            )
            override val pendingClarification: StateFlow<PendingClarification?> = switchedFlow(
                selector = { it.pendingClarification },
                initial = null
            )
            override val conversations = switchedFlow(
                selector = { it.conversations },
                initial = emptyList()
            )
            override val allLocalConversations = localService.allLocalConversations
            override val runningSessions = localService.runningSessions
            override val completedSessions = localService.completedSessions
            override val projectFiles: StateFlow<List<ProjectFileEntry>> = switchedFlow(
                selector = { it.projectFiles },
                initial = emptyList()
            )
            override val projectPath = switchedFlow(
                selector = { it.projectPath },
                initial = ""
            )
            override val workspaces: StateFlow<List<RemoteWorkspace>> = switchedFlow(
                selector = { it.workspaces },
                initial = emptyList()
            )

            override fun sendMessage(content: String) = active.sendMessage(content)
            override fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) =
                active.sendMessageWithImage(content, imageBase64, mimeType, fileName)
            override fun stopGeneration() = active.stopGeneration()
            override fun clearConversation() = active.clearConversation()
            override fun loadConversation(id: String) = active.loadConversation(id)
            override fun deleteConversation(id: String) = active.deleteConversation(id)
            override fun clearVisibleHistory(deleteContext: Boolean) = active.clearVisibleHistory(deleteContext)
            override fun compactConversation(focus: String) = active.compactConversation(focus)
            override fun cancelCompactConversation() = active.cancelCompactConversation()
            override fun getProjectFiles(path: String) = active.getProjectFiles(path)
            override fun selectModel(modelKey: String) = active.selectModel(modelKey)
            override fun setWorkspace(path: String?) = active.setWorkspace(path)
            override fun setAssistantOwner(mode: AssistantMode, ownerId: String?, workspacePath: String?, agentId: Long?) =
                active.setAssistantOwner(mode, ownerId, workspacePath, agentId)
            override fun clearError() = active.clearError()
            override suspend fun sendMessageToConversation(conversationId: Long, content: String) =
                localService.sendMessageToConversation(conversationId, content)
            override fun loadMoreConversations() = active.loadMoreConversations()
            override fun hasMoreConversations() = active.hasMoreConversations()
            override fun respondToToolInteraction(executionId: String, confirmed: Boolean) = active.respondToToolInteraction(executionId, confirmed)
            override fun respondToClarification(executionId: String, answer: String?) =
                active.respondToClarification(executionId, answer)
            override fun regenerateLastResponse() = active.regenerateLastResponse()
            override fun editMessage(messageId: String, newContent: String) = active.editMessage(messageId, newContent)
            override fun resendMessage(messageId: String) = active.resendMessage(messageId)

            override fun connect(ip: String, port: Int) = active.connect(ip, port)
            override fun resync() = active.resync()
            override fun refreshState() = active.refreshState()
            override fun setConversationMode(mode: ConversationMode) = active.setConversationMode(mode)
            override fun setConversationModeId(modeId: String) = active.setConversationModeId(modeId)
            override fun setEffort(effort: com.amaya.intelligence.data.remote.api.ThinkingEffort) = active.setEffort(effort)
            override fun refreshModels() = active.refreshModels()
        }
    }
}
