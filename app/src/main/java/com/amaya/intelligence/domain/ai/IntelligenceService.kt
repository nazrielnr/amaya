package com.amaya.intelligence.domain.ai

import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.data.local.db.entity.ConversationEntity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The unified contract for all AI interactions.
 * Whether it's Local AI or Remote IDE, the UI only talks to this.
 */
interface IntelligenceService {
    val uiState: StateFlow<ChatUiState>
    val conversations: StateFlow<List<ConversationEntity>>
    
    // Actions
    fun sendMessage(content: String)
    fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {}
    fun stopGeneration()
    fun clearConversation()
    fun loadConversation(id: String)
    fun deleteConversation(id: String)
    fun resync() {}
    fun refreshState() {}
    
    // Workspace & Projects
    val projectFiles: StateFlow<List<ProjectFileEntry>> get() = MutableStateFlow(emptyList())
    val projectPath: StateFlow<String> get() = MutableStateFlow("")
    val workspaces: StateFlow<List<RemoteWorkspace>> get() = MutableStateFlow(emptyList())
    fun getProjectFiles(path: String) {}

    fun setSelectedAgent(agentId: String)
    fun setWorkspace(path: String?) {}
    fun clearError() {}
    fun loadMoreConversations() {}
    fun hasMoreConversations(): Boolean = false
    
    fun refreshModels() {}
    
    // Remote-specific (will be no-op in local)
    fun respondToToolInteraction(executionId: String, confirmed: Boolean) {}
    fun connect(ip: String, port: Int) {}
    fun setConversationMode(mode: ConversationMode) {}
}

/**
 * Reasons for the UI to scroll.
 */
enum class ScrollReason {
    USER_MESSAGE,
    AI_DELTA,
    NEW_CONVERSATION,
    INITIAL_LOAD
}
