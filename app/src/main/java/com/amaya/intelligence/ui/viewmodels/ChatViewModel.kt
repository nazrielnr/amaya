package com.amaya.intelligence.ui.viewmodels

// Remote imports removed - now using domain and implementation mappers

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.models.ConversationMode as DomainConversationMode
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.tools.TodoRepository
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.ConfirmationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val intelligenceService: IntelligenceService,
    private val sessionManager: IntelligenceSessionManager,
    private val cronJobRepository: CronJobRepository,
    private val todoRepository: TodoRepository
) : ViewModel() {

    /** Number of currently active reminders — shown as badge on session info button. */
    val activeReminderCount: StateFlow<Int> = cronJobRepository.activeJobCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Live todo items emitted by the AI via update_todo tool. */
    val todoItems: StateFlow<List<TodoItem>> = todoRepository.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<ChatUiState> = intelligenceService.uiState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    // Scroll events emitted from ViewModel — UI collects and scrolls only if at bottom.
    enum class ScrollReason { NEW_MESSAGE, NEW_TOOL }
    private val _scrollEvent = kotlinx.coroutines.flow.MutableSharedFlow<ScrollReason>(
        extraBufferCapacity = 8
    )
    val scrollEvent: kotlinx.coroutines.flow.SharedFlow<ScrollReason> = _scrollEvent
    
    // Conversations derived from service
    val conversations: StateFlow<List<com.amaya.intelligence.data.local.entity.ConversationEntity>> = intelligenceService.conversations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Workspace & Projects
    val projectFiles: StateFlow<List<ProjectFileEntry>> = intelligenceService.projectFiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projectPath: StateFlow<String> = intelligenceService.projectPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val workspaces: StateFlow<List<com.amaya.intelligence.domain.models.RemoteWorkspace>> = intelligenceService.workspaces
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private val _confirmationRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val confirmationRequest: StateFlow<ConfirmationRequest?> = _confirmationRequest.asStateFlow()
    
    // Continuation for local tool confirmations if handled at ViewModel level
    private val confirmationContinuation = java.util.concurrent.atomic.AtomicReference<((Boolean) -> Unit)?>(null)

    override fun onCleared() {
        super.onCleared()
        confirmationContinuation.getAndSet(null)?.invoke(false)
    }
    
    fun sendMessage(content: String) {
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.sendMessage(content)
    }
    
    fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {
        android.util.Log.d("ChatViewModel", "sendMessageWithImage: content=${content.take(50)}, mimeType=$mimeType, base64Len=${imageBase64.length}, fileName=$fileName")
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.sendMessageWithImage(content, imageBase64, mimeType, fileName)
    }
    
    fun respondToConfirmation(confirmed: Boolean) {
        // Delegate to service
        intelligenceService.respondToToolInteraction("", confirmed)
        // Also handle local continuation if any
        confirmationContinuation.getAndSet(null)?.invoke(confirmed)
        _confirmationRequest.value = null
    }
    
    fun stopGeneration() {
        intelligenceService.stopGeneration()
    }
    
    fun respondToToolInteraction(id: String, confirmed: Boolean) {
        intelligenceService.respondToToolInteraction(id, confirmed)
    }
    
    fun selectModel(modelId: String) {
        intelligenceService.setSelectedAgent(modelId)
    }
    
    fun clearConversation() {
        todoRepository.clear()
        intelligenceService.clearConversation()
    }
    
    fun loadConversation(conversationId: Long) {
        intelligenceService.loadConversation(conversationId.toString())
    }
    
    fun deleteConversation(conversationId: Long) {
        intelligenceService.deleteConversation(conversationId.toString())
    }
    
    fun switchMode(mode: com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode) {
        sessionManager.setMode(mode)
    }

    fun connect(ip: String, port: Int) {
        intelligenceService.connect(ip, port)
    }
    
    fun setWorkspace(path: String?) {
        intelligenceService.setWorkspace(path)
    }

    fun setSelectedAgent(agentId: String) {
        intelligenceService.setSelectedAgent(agentId)
    }

    fun clearError() {
        intelligenceService.clearError()
    }

    fun getProjectFiles(path: String) {
        intelligenceService.getProjectFiles(path)
    }
    
    fun loadMoreConversations() {
        intelligenceService.loadMoreConversations()
    }
    
    fun hasMoreConversations(): Boolean {
        return intelligenceService.hasMoreConversations()
    }

    fun resync() {
        intelligenceService.resync()
    }

    fun refreshState() {
        intelligenceService.refreshState()
    }

    fun setConversationMode(mode: DomainConversationMode) {
        intelligenceService.setConversationMode(mode)
    }

    fun refreshModels() {
        intelligenceService.refreshModels()
    }
}

// ── Re-export data classes from models/ for backward compatibility ────────────
// All code importing from com.amaya.intelligence.ui.chat will still work.
// These now point to the unified domain models.
typealias ChatUiState       = com.amaya.intelligence.domain.models.ChatUiState
typealias UiMessage         = com.amaya.intelligence.domain.models.UiMessage
typealias ToolExecution     = com.amaya.intelligence.domain.models.ToolExecution
typealias SubagentExecution = com.amaya.intelligence.domain.models.SubagentExecution
typealias ToolStatus        = com.amaya.intelligence.domain.models.ToolStatus
typealias ProjectFileEntry  = com.amaya.intelligence.domain.models.ProjectFileEntry
