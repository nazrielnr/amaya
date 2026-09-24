package com.amaya.intelligence.ui.viewmodels

// Remote imports removed - now using domain and implementation mappers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.models.ConversationMode as DomainConversationMode
import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.ProjectDao
import com.amaya.intelligence.data.local.dao.DelegationTaskDao
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.tools.TodoRepository
import com.amaya.intelligence.tools.TodoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Directories never worth walking when resolving an `@` mention. */
private val WORKSPACE_SCAN_EXCLUDES = setOf(".git", ".gradle", "build", "node_modules")

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val intelligenceService: IntelligenceService,
    private val sessionManager: IntelligenceSessionManager,
    private val agentDao: AgentDao,
    private val projectDao: ProjectDao,
    private val delegationTaskDao: DelegationTaskDao,
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
    /** Live `ask_user` question the model is waiting on; the chat screen shows it as a dialog. */
    val pendingClarification: StateFlow<com.amaya.intelligence.domain.ai.PendingClarification?> =
        intelligenceService.pendingClarification

    // Scroll events emitted from ViewModel — UI collects and scrolls only if at bottom.
    enum class ScrollReason { NEW_MESSAGE, NEW_TOOL }
    private val _scrollEvent = kotlinx.coroutines.flow.MutableSharedFlow<ScrollReason>(
        extraBufferCapacity = 8
    )
    val scrollEvent: kotlinx.coroutines.flow.SharedFlow<ScrollReason> = _scrollEvent

    // Conversations derived from service
    val conversations: StateFlow<List<com.amaya.intelligence.data.local.entity.ConversationEntity>> = intelligenceService.conversations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val projects = projectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agentGroups = agentDao.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAgents = agentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allLocalConversations = intelligenceService.allLocalConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runningSessions = intelligenceService.runningSessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeAgentMembers = uiState.flatMapLatest { state ->
        if (state.assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
            state.ownerId?.toLongOrNull()?.let(agentDao::observeByGroup) ?: flowOf(emptyList())
        } else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDelegationTasks = uiState.flatMapLatest { state ->
        if (state.assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
            state.ownerId?.toLongOrNull()?.let(delegationTaskDao::observeByGroup) ?: flowOf(emptyList())
        } else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownerLabel = uiState.mapLatest { state ->
        when (state.assistantMode) {
            com.amaya.intelligence.domain.models.AssistantMode.CHAT -> "Chat"
            com.amaya.intelligence.domain.models.AssistantMode.PROJECT ->
                state.ownerId?.toLongOrNull()?.let { projectDao.getById(it)?.name } ?: "Project"
            com.amaya.intelligence.domain.models.AssistantMode.AGENT ->
                state.agentId?.let { agentDao.getById(it)?.name } ?: "Agent"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Chat")

    val activeAgentGroupLabel = uiState.mapLatest { state ->
        if (state.assistantMode != com.amaya.intelligence.domain.models.AssistantMode.AGENT) return@mapLatest ""
        state.ownerId?.toLongOrNull()?.let { agentDao.getGroupById(it)?.name }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    // Workspace & Projects
    val projectFiles: StateFlow<List<ProjectFileEntry>> = intelligenceService.projectFiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projectPath: StateFlow<String> = intelligenceService.projectPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val workspaces: StateFlow<List<com.amaya.intelligence.domain.models.RemoteWorkspace>> = intelligenceService.workspaces
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun sendMessage(content: String) {
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.sendMessage(content)
    }

    fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.sendMessageWithImage(content, imageBase64, mimeType, fileName)
    }

    fun stopGeneration() {
        intelligenceService.stopGeneration()
    }

    fun respondToToolInteraction(id: String, confirmed: Boolean) {
        intelligenceService.respondToToolInteraction(id, confirmed)
    }

    fun respondToClarification(id: String, answer: String?) {
        intelligenceService.respondToClarification(id, answer)
    }

    fun regenerateLastResponse() {
        intelligenceService.regenerateLastResponse()
    }

    fun editMessage(messageId: String, newContent: String) {
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.editMessage(messageId, newContent)
    }

    fun resendMessage(messageId: String) {
        _scrollEvent.tryEmit(ScrollReason.NEW_MESSAGE)
        intelligenceService.resendMessage(messageId)
    }

    fun selectModel(modelKey: String) {
        intelligenceService.selectModel(modelKey)
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

    fun clearVisibleHistory(deleteContext: Boolean) {
        intelligenceService.clearVisibleHistory(deleteContext)
    }

    fun compactConversation(focus: String = "") {
        intelligenceService.compactConversation(focus)
    }

    fun cancelCompactConversation() {
        intelligenceService.cancelCompactConversation()
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

    fun setAssistantOwner(
        mode: com.amaya.intelligence.domain.models.AssistantMode,
        ownerId: String? = null,
        workspacePath: String? = null,
        agentId: Long? = null
    ) = selectChatTarget(mode, ownerId, workspacePath, agentId)

    fun selectChatTarget(
        mode: com.amaya.intelligence.domain.models.AssistantMode,
        ownerId: String? = null,
        workspacePath: String? = null,
        agentId: Long? = null,
        conversationId: Long? = null
    ) {
        todoRepository.clear()
        intelligenceService.setAssistantOwner(mode, ownerId, workspacePath, agentId)
        if (mode != com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
            conversationId?.let { intelligenceService.loadConversation(it.toString()) }
        }
    }



    fun clearError() {
        intelligenceService.clearError()
    }

    suspend fun searchWorkspaceFiles(query: String, limit: Int = 24): List<ProjectFileEntry> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        val delegated = projectFiles.value.filter {
            needle.isBlank() || it.name.contains(needle, ignoreCase = true) || it.path.contains(needle, ignoreCase = true)
        }.take(limit)
        if (delegated.isNotEmpty()) return@withContext delegated

        val root = uiState.value.workspacePath?.takeIf(String::isNotBlank)?.let { java.io.File(it) }
            ?.takeIf { it.isDirectory } ?: return@withContext emptyList()
        val matches = ArrayList<ProjectFileEntry>(limit)
        var visited = 0
        val walk = root.walkTopDown()
            .onEnter { it.name !in WORKSPACE_SCAN_EXCLUDES }
            .drop(1)
            .iterator()
        while (walk.hasNext()) {
            // walkTopDown never suspends, so a superseded search would otherwise keep walking the
            // whole tree on the IO pool while the user is still typing.
            if (++visited % 256 == 0) ensureActive()
            val file = walk.next()
            if (needle.isNotBlank() && !file.name.contains(needle, ignoreCase = true)) continue
            matches += ProjectFileEntry(
                name = file.name,
                path = file.relativeTo(root).path.replace(java.io.File.separatorChar, '/'),
                type = if (file.isDirectory) "directory" else "file",
                size = if (file.isFile) file.length() else 0L
            )
            if (matches.size >= limit) break
        }
        matches
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

    fun setConversationModeId(modeId: String) {
        intelligenceService.setConversationModeId(modeId)
    }

    fun setEffort(effort: com.amaya.intelligence.data.remote.api.ThinkingEffort) {
        intelligenceService.setEffort(effort)
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
