package com.amaya.intelligence.impl.ide.antigravity.services

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteSessionClient
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteEvent
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteAttachment
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteFileEntry as ClientRemoteFileEntry
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteWorkspace as ClientRemoteWorkspace
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.impl.ide.antigravity.services.streaming.StreamingStateManager
import com.amaya.intelligence.impl.ide.antigravity.services.event.AntigravityEventHandler
import com.amaya.intelligence.di.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Antigravity implementation of IntelligenceService.
 * Wraps RemoteSessionClient and delegates event handling to AntigravityEventHandler.
 */
@Singleton
class AntigravityIntelligenceService @Inject constructor(
    private val client: RemoteSessionClient,
    @ApplicationScope private val scope: CoroutineScope
) : IntelligenceService {

    private val _uiState = MutableStateFlow(ChatUiState(
        sessionMode = IntelligenceSessionManager.SessionMode.ANTIGRAVITY
    ))
    override val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    override val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _projectFiles = MutableStateFlow<List<ProjectFileEntry>>(emptyList())
    override val projectFiles: StateFlow<List<ProjectFileEntry>> = _projectFiles.asStateFlow()

    private val _projectPath = MutableStateFlow("")
    override val projectPath: StateFlow<String> = _projectPath.asStateFlow()

    private val _workspaces = MutableStateFlow<List<RemoteWorkspace>>(emptyList())
    override val workspaces: StateFlow<List<RemoteWorkspace>> = _workspaces.asStateFlow()

    private val stateManager = StreamingStateManager()
    private var hasBootstrappedRemoteWorkspace = false
    
    private val eventHandler = AntigravityEventHandler(
        scope = scope,
        client = client,
        stateManager = stateManager,
        onUiStateUpdate = { update -> _uiState.update(update) },
        onConversationsUpdate = { entities -> _conversations.value = entities },
        onProjectFilesUpdate = { files, path -> 
            _projectFiles.value = files
            _projectPath.value = path
        },
        onWorkspacesUpdate = { workspaces -> _workspaces.value = workspaces }
    )

    init {
        scope.launch {
            client.events.collect { event ->
                eventHandler.handleEvent(event, _uiState.value.conversationId)
            }
        }
        // Monitor connection state
        scope.launch {
            client.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == ConnectionState.CONNECTED) {
                    if (!hasBootstrappedRemoteWorkspace) {
                        hasBootstrappedRemoteWorkspace = true
                        client.getWorkspaces()
                        _uiState.value.workspacePath?.takeIf { it.isNotBlank() }?.let { client.getProjectFiles(it) }
                    }
                    resync()
                }
            }
        }
    }

    override fun sendMessage(content: String) {
        val activeId = _uiState.value.conversationId
        val mode = _uiState.value.conversationMode.wireValue
        client.sendMessage(content, activeId, mode)
        
        // Optimistic update
        val userMsg = UiMessage(
            role = MessageRole.USER,
            content = content
        )
        _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }
    }

    override fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {
        android.util.Log.d("AntigravityIntelligenceService", "sendMessageWithImage: content=${content.take(50)}, mimeType=$mimeType, base64Len=${imageBase64.length}, fileName=$fileName")
        val activeId = _uiState.value.conversationId
        val mode = _uiState.value.conversationMode.wireValue
        val attachment = RemoteAttachment(mimeType, imageBase64, fileName)
        client.sendMessage(content, activeId, mode, listOf(attachment))
        
        // Optimistic update with image attachment
        val userMsg = UiMessage(
            role = MessageRole.USER,
            content = content,
            attachments = listOf(MessageAttachment(mimeType, imageBase64, fileName))
        )
        _uiState.update { it.copy(messages = it.messages + userMsg, isLoading = true) }
    }

    override fun stopGeneration() {
        client.stopGeneration()
    }

    override fun clearConversation() {
        client.newChat()
    }

    override fun loadConversation(id: String) {
        val resolvedId = eventHandler.resolveConversationId(id)
        _uiState.update { it.copy(conversationId = resolvedId, isLoading = true, messages = emptyList()) }
        client.loadConversation(resolvedId)
    }

    override fun deleteConversation(id: String) {
        // Antigravity might not support deletion via client yet, or needs mapping
    }

    override fun setSelectedAgent(agentId: String) {
        client.selectModel(agentId)
    }

    override fun getProjectFiles(path: String) {
        client.getProjectFiles(path)
    }

    override fun respondToToolInteraction(executionId: String, confirmed: Boolean) {
        val conversationId = _uiState.value.conversationId
        client.respondToToolInteraction(
            toolCallId = executionId,
            accepted = confirmed,
            conversationId = conversationId
        )
    }

    override fun setWorkspace(path: String?) {
        _uiState.update { it.copy(workspacePath = path) }
        path?.let { client.getProjectFiles(it) }
    }

    override fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun connect(ip: String, port: Int) {
        client.connect(ip, port)
    }

    override fun resync() {
        client.forceResync(resetSequence = true)
    }

    override fun refreshState() {
        client.refreshState()
    }

    override fun setConversationMode(mode: ConversationMode) {
        _uiState.update { it.copy(conversationMode = mode) }
        client.setConversationMode(mode)
    }

    override fun refreshModels() {
        client.getModels()
    }

    override fun loadMoreConversations() {
        // Implementation for pagination if needed
    }

    override fun hasMoreConversations(): Boolean {
        return false
    }
}

// Extension to map remote models to domain
private fun ClientRemoteFileEntry.toProjectFileEntry(): ProjectFileEntry {
    return ProjectFileEntry(
        name = name,
        path = path,
        type = type,
        size = size
    )
}

private fun ClientRemoteWorkspace.toDomainWorkspace(): RemoteWorkspace {
    return RemoteWorkspace(
        name = name,
        path = path,
        isCurrent = isCurrent
    )
}
