package com.amaya.intelligence.domain.ai

import com.amaya.intelligence.domain.models.*

import com.amaya.intelligence.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The unified contract for all AI interactions.
 * Whether it's Local AI or Remote IDE, the UI only talks to this.
 */
interface IntelligenceService {
    val uiState: StateFlow<ChatUiState>
    val conversations: StateFlow<List<ConversationEntity>>
    val allLocalConversations: StateFlow<List<ConversationEntity>> get() = MutableStateFlow(emptyList())
    val runningSessions: StateFlow<List<RunningSession>> get() = MutableStateFlow(emptyList())
    val completedSessions: SharedFlow<RunningSession> get() = MutableSharedFlow()

    // Actions
    fun sendMessage(content: String)
    fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {
        error("Image input is not supported by this intelligence service")
    }
    fun stopGeneration()
    fun clearConversation()
    fun loadConversation(id: String)
    fun deleteConversation(id: String)
    fun clearVisibleHistory(deleteContext: Boolean) {}
    fun compactConversation(focus: String = "") {}
    fun cancelCompactConversation() {}
    fun resync() {}
    fun refreshState() {}

    // Workspace & Projects
    val projectFiles: StateFlow<List<ProjectFileEntry>> get() = MutableStateFlow(emptyList())
    val projectPath: StateFlow<String> get() = MutableStateFlow("")
    val workspaces: StateFlow<List<RemoteWorkspace>> get() = MutableStateFlow(emptyList())
    fun getProjectFiles(path: String) {}

    fun selectModel(modelKey: String)
    fun setWorkspace(path: String?) {}
    fun setAssistantOwner(mode: AssistantMode, ownerId: String? = null, workspacePath: String? = null, agentId: Long? = null) {}
    fun clearError() {}
    suspend fun sendMessageToConversation(conversationId: Long, content: String): Boolean = false
    fun loadMoreConversations() {}
    fun hasMoreConversations(): Boolean = false

    fun refreshModels() {}

    // Remote-specific (will be no-op in local)
    fun respondToToolInteraction(executionId: String, confirmed: Boolean) {}
    fun connect(ip: String, port: Int) {}

    /**
     * A live ask_user question the model is waiting on (null when none). The UI shows it as a
     * dialog with a free-text input; answering resumes the suspended tool loop.
     */
    val pendingClarification: StateFlow<PendingClarification?> get() = MutableStateFlow(null)

    /** Resolve a pending ask_user question. A null [answer] dismisses it. */
    fun respondToClarification(executionId: String, answer: String?) {}

    /** Trim the trailing assistant turn and re-run the last user prompt with a fresh response. */
    fun regenerateLastResponse() {}
    /** Edit an existing user message, update persistence, truncate subsequent turns, and regenerate. */
    fun editMessage(messageId: String, newContent: String) {}
    /** Resend an existing user message without retyping. */
    fun resendMessage(messageId: String) {}
    fun setConversationMode(mode: ConversationMode) {}

    /** Set the global reasoning effort shown by the chat bulb. */
    fun setEffort(effort: com.amaya.intelligence.data.remote.api.ThinkingEffort) {}

    /**
     * Generic hook: pick a conversation mode by its provider-defined id.
     * Default implementation bridges legacy "planning"/"fast" to
     * [setConversationMode] so existing providers keep working without changes.
     */
    fun setConversationModeId(modeId: String) {
        when (modeId) {
            ConversationMode.PLANNING.wireValue -> setConversationMode(ConversationMode.PLANNING)
            ConversationMode.FAST.wireValue -> setConversationMode(ConversationMode.FAST)
        }
    }
}

/**
 * A model-initiated clarification question waiting for the user (drives the ask_user tool).
 */
data class PendingClarification(
    val toolCallId: String,
    val question: String,
    val options: List<String> = emptyList()
)

/**
 * Reasons for the UI to scroll.
 */
enum class ScrollReason {
    USER_MESSAGE,
    AI_DELTA,
    NEW_CONVERSATION,
    INITIAL_LOAD
}
