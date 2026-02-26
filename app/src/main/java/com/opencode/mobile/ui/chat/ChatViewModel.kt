package com.opencode.mobile.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.local.db.dao.ConversationDao
import com.opencode.mobile.data.local.db.entity.ConversationEntity
import com.opencode.mobile.data.remote.api.AgentConfig
import com.opencode.mobile.data.remote.api.AiSettingsManager
import com.opencode.mobile.data.remote.api.ChatMessage
import com.opencode.mobile.data.remote.api.MessageRole
import com.opencode.mobile.data.repository.AgentEvent
import com.opencode.mobile.data.repository.AiRepository
import com.opencode.mobile.data.repository.ProviderInfo
import com.opencode.mobile.tools.ConfirmationRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val conversationDao: ConversationDao,
    private val aiSettingsManager: AiSettingsManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    val conversations: StateFlow<List<ConversationEntity>> = conversationDao
        .getRecentConversations(20)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private var currentConversationId: Long? = null
    
    private val _confirmationRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val confirmationRequest: StateFlow<ConfirmationRequest?> = _confirmationRequest.asStateFlow()
    
    private var confirmationContinuation: ((Boolean) -> Unit)? = null
    private var currentChatJob: kotlinx.coroutines.Job? = null
    
    init {
        // Observe settings reactively so agent list + active model are always up-to-date
        viewModelScope.launch {
            aiSettingsManager.settingsFlow.collect { settings ->
                val activeAgent = settings.agentConfigs.find { it.id == settings.activeAgentId }
                _uiState.update {
                    it.copy(
                        agentConfigs  = settings.agentConfigs,
                        activeAgentId = settings.activeAgentId,
                        selectedModel = settings.activeModel.ifBlank {
                            activeAgent?.modelId ?: settings.agentConfigs.firstOrNull()?.modelId ?: ""
                        }
                    )
                }
            }
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isLoading) return
        
        currentChatJob = viewModelScope.launch {
            val userMessage = UiMessage(
                role = MessageRole.USER,
                content = content
            )
            
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    isLoading = true,
                    error = null
                )
            }
            
            val chatHistory = _uiState.value.messages
                .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .map { msg -> ChatMessage(role = msg.role, content = msg.content) }
            
            val assistantMessage = UiMessage(
                role = MessageRole.ASSISTANT,
                content = ""
            )
            
            _uiState.update {
                it.copy(messages = it.messages + assistantMessage)
            }
            
            aiRepository.chat(
                message = content,
                conversationHistory = chatHistory.dropLast(1),
                projectId = _uiState.value.activeProjectId,
                workspacePath = _uiState.value.workspacePath,
                onConfirmation = { request ->
                    _confirmationRequest.value = request
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        confirmationContinuation = { confirmed -> cont.resume(confirmed) { } }
                    }
                }
            ).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> {
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            val lastIndex = messages.lastIndex
                            if (lastIndex >= 0) {
                                val last = messages[lastIndex]
                                if (last.role == MessageRole.ASSISTANT) {
                                    messages[lastIndex] = last.copy(content = last.content + event.text)
                                }
                            }
                            state.copy(messages = messages)
                        }
                    }
                    
                    is AgentEvent.ToolCallStart -> {
                        val toolExecution = ToolExecution(
                            toolCallId = event.toolCallId,
                            name = event.name,
                            arguments = event.arguments,
                            status = ToolStatus.RUNNING
                        )
                        
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            val lastIndex = messages.lastIndex
                            if (lastIndex >= 0) {
                                val last = messages[lastIndex]
                                if (last.role == MessageRole.ASSISTANT) {
                                    messages[lastIndex] = last.copy(
                                        toolExecutions = last.toolExecutions + toolExecution
                                    )
                                }
                            }
                            state.copy(messages = messages)
                        }
                    }
                    
                    is AgentEvent.ToolCallResult -> {
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            for (msgIndex in messages.indices.reversed()) {
                                val msg = messages[msgIndex]
                                if (msg.role == MessageRole.ASSISTANT) {
                                    val execIndex = msg.toolExecutions.indexOfFirst { 
                                        it.toolCallId == event.toolCallId 
                                    }
                                    if (execIndex >= 0) {
                                        val executions = msg.toolExecutions.toMutableList()
                                        executions[execIndex] = executions[execIndex].copy(
                                            result = event.result,
                                            status = if (event.isError) ToolStatus.ERROR else ToolStatus.SUCCESS
                                        )
                                        messages[msgIndex] = msg.copy(toolExecutions = executions)
                                        break
                                    }
                                }
                            }
                            state.copy(messages = messages)
                        }
                    }
                    
                    is AgentEvent.Usage -> {
                        _uiState.update { state ->
                            state.copy(
                                totalInputTokens = state.totalInputTokens + event.inputTokens,
                                totalOutputTokens = state.totalOutputTokens + event.outputTokens
                            )
                        }
                    }
                    
                    is AgentEvent.Error -> {
                        _uiState.update { state ->
                            state.copy(error = event.message, isLoading = false)
                        }
                    }
                    
                    is AgentEvent.NewIteration -> {
                        val newAssistantMessage = UiMessage(
                            role = MessageRole.ASSISTANT,
                            content = ""
                        )
                        _uiState.update { state ->
                            state.copy(messages = state.messages + newAssistantMessage)
                        }
                    }
                    
                    is AgentEvent.Done -> {
                        _uiState.update { it.copy(isLoading = false) }
                        saveCurrentConversation()
                    }
                }
            }
        }
    }
    
    fun respondToConfirmation(confirmed: Boolean) {
        confirmationContinuation?.invoke(confirmed)
        confirmationContinuation = null
        _confirmationRequest.value = null
    }
    
    fun stopGeneration() {
        currentChatJob?.cancel()
        currentChatJob = null
        _uiState.update { it.copy(isLoading = false) }
        saveCurrentConversation()
    }
    
    fun selectProvider(provider: ProviderInfo) {
        _uiState.update {
            it.copy(selectedProvider = provider, selectedModel = provider.models.firstOrNull() ?: "")
        }
    }
    
    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }
    
    fun clearConversation() {
        saveCurrentConversation()
        currentConversationId = null
        _uiState.update {
            it.copy(messages = emptyList(), totalInputTokens = 0, totalOutputTokens = 0)
        }
    }
    
    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                val conversation = conversationDao.getConversationById(conversationId)
                if (conversation != null) {
                    currentConversationId = conversationId
                    val messages = parseMessagesFromJson(conversation.messagesJson)
                    _uiState.update {
                        it.copy(
                            workspacePath = conversation.workspacePath,
                            messages = messages,
                            totalInputTokens = 0,
                            totalOutputTokens = 0
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load conversation: ${e.message}") }
            }
        }
    }
    
    private fun parseMessagesFromJson(json: String): List<UiMessage> {
        if (json.isBlank()) return emptyList()
        return try {
            val messages = mutableListOf<UiMessage>()
            val jsonArray = org.json.JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val roleStr = obj.getString("role")
                val content = obj.getString("content")
                val role = when (roleStr) {
                    "USER" -> MessageRole.USER
                    "ASSISTANT" -> MessageRole.ASSISTANT
                    "SYSTEM" -> MessageRole.SYSTEM
                    else -> MessageRole.USER
                }
                messages.add(UiMessage(role = role, content = content))
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveCurrentConversation() {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return
        
        viewModelScope.launch {
            try {
                val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: "New Conversation"
                val title = firstUserMsg.split("\\s+".toRegex()).take(5).joinToString(" ")
                
                val now = System.currentTimeMillis()
                val messagesJson = serializeMessagesToJson(messages)
                
                if (currentConversationId != null) {
                    val existing = conversationDao.getConversationById(currentConversationId!!)
                    if (existing != null) {
                        conversationDao.updateConversation(
                            existing.copy(title = title.take(50), messagesJson = messagesJson, updatedAt = now)
                        )
                    }
                } else {
                    val conversation = ConversationEntity(
                        id = 0,
                        title = title.take(50),
                        workspacePath = _uiState.value.workspacePath,
                        messagesJson = messagesJson,
                        createdAt = now,
                        updatedAt = now
                    )
                    currentConversationId = conversationDao.insertConversation(conversation)
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }
    
    private fun serializeMessagesToJson(messages: List<UiMessage>): String {
        val jsonArray = org.json.JSONArray()
        messages.forEach { msg ->
            val obj = org.json.JSONObject()
            obj.put("role", msg.role.name)
            obj.put("content", msg.content)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
    
    fun setActiveProject(projectId: Long?) {
        _uiState.update { it.copy(activeProjectId = projectId) }
    }
    
    fun setWorkspace(path: String?) {
        _uiState.update { it.copy(workspacePath = path) }
    }

    fun setSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch {
            aiSettingsManager.setActiveModel(model)
        }
    }

    fun setSelectedAgent(agent: com.opencode.mobile.data.remote.api.AgentConfig) {
        _uiState.update { it.copy(selectedModel = agent.modelId, activeAgentId = agent.id) }
        viewModelScope.launch {
            val apiKey = aiSettingsManager.getAgentApiKey(agent.id)
            aiSettingsManager.setActiveAgent(agent.id, agent.modelId)
            aiSettingsManager.setOpenAiSettings(apiKey, agent.baseUrl)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

@Immutable
data class ChatUiState(
    val messages:      List<UiMessage>    = emptyList(),
    val isLoading:     Boolean            = false,
    val error:         String?            = null,
    val providers:     List<ProviderInfo> = emptyList(),
    val selectedProvider: ProviderInfo?  = null,
    val selectedModel: String            = "",
    val activeProjectId: Long?           = null,
    val workspacePath: String?           = null,
    val totalInputTokens:  Int           = 0,
    val totalOutputTokens: Int           = 0,
    // ── Agent configs ──────────────────────────────────────────────
    val agentConfigs:  List<AgentConfig> = emptyList(),
    val activeAgentId: String            = ""
)

@Immutable
data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolExecutions: List<ToolExecution> = emptyList()
)

@Immutable
data class ToolExecution(
    val toolCallId: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING
)

enum class ToolStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}
