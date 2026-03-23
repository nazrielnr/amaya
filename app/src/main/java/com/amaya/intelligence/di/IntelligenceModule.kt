package com.amaya.intelligence.di

import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.models.ConversationMode
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.di.ApplicationScope
import com.amaya.intelligence.impl.local.LocalIntelligenceService
import com.amaya.intelligence.impl.ide.antigravity.services.AntigravityIntelligenceService
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
    @Named("antigravity")
    @Singleton
    fun provideAntigravityService(service: AntigravityIntelligenceService): IntelligenceService = service

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
        @Named("antigravity") antigravityService: IntelligenceService
    ): IntelligenceService {
        return object : IntelligenceService {
            private val active: IntelligenceService
                get() = if (sessionManager.currentMode.value == IntelligenceSessionManager.SessionMode.LOCAL) {
                    localService
                } else {
                    antigravityService
                }

            @OptIn(ExperimentalCoroutinesApi::class)
            private fun <T> switchedFlow(
                selector: (IntelligenceService) -> StateFlow<T>,
                initial: T
            ): StateFlow<T> {
                return sessionManager.currentMode
                    .flatMapLatest { mode ->
                        val service = if (mode == IntelligenceSessionManager.SessionMode.LOCAL) {
                            localService
                        } else {
                            antigravityService
                        }
                        selector(service)
                    }
                    .stateIn(appScope, SharingStarted.Eagerly, initial)
            }

            override val uiState = switchedFlow(
                selector = { it.uiState },
                initial = ChatUiState(sessionMode = sessionManager.currentMode.value)
            )
            override val conversations = switchedFlow(
                selector = { it.conversations },
                initial = emptyList()
            )
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
            override fun getProjectFiles(path: String) = active.getProjectFiles(path)
            override fun setSelectedAgent(agentId: String) = active.setSelectedAgent(agentId)
            override fun setWorkspace(path: String?) = active.setWorkspace(path)
            override fun clearError() = active.clearError()
            override fun loadMoreConversations() = active.loadMoreConversations()
            override fun hasMoreConversations() = active.hasMoreConversations()
            override fun respondToToolInteraction(executionId: String, confirmed: Boolean) = active.respondToToolInteraction(executionId, confirmed)

            override fun connect(ip: String, port: Int) = active.connect(ip, port)
            override fun resync() = active.resync()
            override fun refreshState() = active.refreshState()
            override fun setConversationMode(mode: ConversationMode) = active.setConversationMode(mode)
            override fun refreshModels() = active.refreshModels()
        }
    }
}
