package com.amaya.intelligence.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.local.db.dao.ConversationDao
import com.amaya.intelligence.data.local.db.entity.ConversationEntity
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.repository.AgentEvent
import com.amaya.intelligence.data.repository.AiRepository
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val conversationDao: ConversationDao,
    private val aiSettingsManager: AiSettingsManager,
    private val cronJobRepository: CronJobRepository,
    private val personaRepository: PersonaRepository,
    private val todoRepository: TodoRepository
) : ViewModel() {

    /** Number of currently active reminders — shown as badge on session info button. */
    val activeReminderCount: StateFlow<Int> = cronJobRepository.activeJobCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Live todo items emitted by the AI via update_todo tool. */
    val todoItems: StateFlow<List<TodoItem>> = todoRepository.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether there is a memory log entry for today. */
    val hasTodayMemory: StateFlow<Boolean> = flow {
        emit(personaRepository.hasTodayLog())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
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

    override fun onCleared() {
        super.onCleared()
        // Release confirmation continuation to avoid memory leak
        confirmationContinuation?.invoke(false)
        confirmationContinuation = null
    }
    
    init {
        // Observe settings reactively — sync agent list and resolve active agent from enabled agents only.
        viewModelScope.launch {
            aiSettingsManager.settingsFlow.collect { settings ->
                val enabledAgents = settings.agentConfigs.filter { it.enabled }
                // Resolve active agent: prefer current selection if still enabled, else first enabled
                val resolvedAgent = enabledAgents.find { it.id == settings.activeAgentId }
                    ?: enabledAgents.firstOrNull()

                _uiState.update { current ->
                    // If current selected agent is still enabled, keep it
                    val currentAgentStillEnabled = enabledAgents.any { it.id == current.activeAgentId }
                    val newActiveAgentId = when {
                        current.activeAgentId.isNotBlank() && currentAgentStillEnabled -> current.activeAgentId
                        else -> resolvedAgent?.id ?: ""
                    }
                    val newSelectedModel = when {
                        // Keep current model if agent is still enabled and model matches
                        current.selectedModel.isNotBlank() && currentAgentStillEnabled -> current.selectedModel
                        // Use resolved agent's model
                        else -> resolvedAgent?.modelId ?: ""
                    }
                    current.copy(
                        agentConfigs  = settings.agentConfigs, // keep all for settings screen
                        activeAgentId = newActiveAgentId,
                        selectedModel = newSelectedModel
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
                conversationId = currentConversationId,
                activeAgentId = _uiState.value.activeAgentId,
                selectedModel = _uiState.value.selectedModel.ifBlank { null },
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
                    
                    is AgentEvent.SubagentUpdate -> {
                        _uiState.update { state ->
                            val messages = state.messages.toMutableList()
                            for (msgIndex in messages.indices.reversed()) {
                                val msg = messages[msgIndex]
                                if (msg.role == MessageRole.ASSISTANT) {
                                    val execIndex = msg.toolExecutions.indexOfFirst {
                                        it.toolCallId == event.parentToolCallId
                                    }
                                    if (execIndex >= 0) {
                                        val executions = msg.toolExecutions.toMutableList()
                                        val exec = executions[execIndex]
                                        val children = exec.children.toMutableList()
                                        val childIdx = children.indexOfFirst { it.index == event.index }
                                        val child = SubagentExecution(
                                            index    = event.index,
                                            taskName = event.taskName,
                                            prompt   = event.prompt,
                                            result   = event.result,
                                            status   = when {
                                                !event.isComplete          -> ToolStatus.RUNNING
                                                event.isError              -> ToolStatus.ERROR
                                                else                       -> ToolStatus.SUCCESS
                                            }
                                        )
                                        if (childIdx >= 0) children[childIdx] = child
                                        else children.add(child)
                                        executions[execIndex] = exec.copy(children = children.sortedBy { it.index })
                                        messages[msgIndex] = msg.copy(toolExecutions = executions)
                                        break
                                    }
                                }
                            }
                            state.copy(messages = messages)
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
    
    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        // Persist to DataStore so AiRepository always gets the latest model
        viewModelScope.launch { aiSettingsManager.setActiveModel(model) }
    }
    
    fun clearConversation() {
        saveCurrentConversation()
        currentConversationId = null
        todoRepository.clear()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                totalInputTokens = 0,
                totalOutputTokens = 0,
                isLoading = false,
                error = null
            )
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
                    "USER"      -> MessageRole.USER
                    "ASSISTANT" -> MessageRole.ASSISTANT
                    "SYSTEM"    -> MessageRole.SYSTEM
                    else        -> MessageRole.USER
                }
                // Restore tool executions
                val toolExecutions = mutableListOf<ToolExecution>()
                if (obj.has("toolExecutions")) {
                    val execArr = obj.getJSONArray("toolExecutions")
                    for (j in 0 until execArr.length()) {
                        val e = execArr.getJSONObject(j)
                        val argsMap = mutableMapOf<String, Any?>()
                        if (e.has("arguments")) {
                            val argsObj = e.getJSONObject("arguments")
                            argsObj.keys().forEach { k -> argsMap[k] = argsObj.get(k) }
                        }
                        toolExecutions.add(
                            ToolExecution(
                                toolCallId = e.getString("toolCallId"),
                                name       = e.getString("name"),
                                arguments  = argsMap,
                                result     = e.optString("result", null as String?),
                                status     = try { ToolStatus.valueOf(e.getString("status")) }
                                             catch (_: Exception) { ToolStatus.SUCCESS }
                            )
                        )
                    }
                }
                messages.add(UiMessage(role = role, content = content, toolExecutions = toolExecutions))
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
            if (msg.toolExecutions.isNotEmpty()) {
                val execArr = org.json.JSONArray()
                msg.toolExecutions.forEach { exec ->
                    val e = org.json.JSONObject()
                    e.put("toolCallId", exec.toolCallId)
                    e.put("name", exec.name)
                    e.put("status", exec.status.name)
                    exec.result?.let { e.put("result", it) }
                    val argsObj = org.json.JSONObject()
                    exec.arguments.forEach { (k, v) -> argsObj.put(k, v ?: org.json.JSONObject.NULL) }
                    e.put("arguments", argsObj)
                    execArr.put(e)
                }
                obj.put("toolExecutions", execArr)
            }
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

    fun setSelectedModel(model: String) = selectModel(model)

    fun setSelectedAgent(agent: com.amaya.intelligence.data.remote.api.AgentConfig) {
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

    /**
     * Remove a single tool execution card from a specific message.
     * Used by the delete button on ToolCallCard.
     */
    fun removeToolExecution(messageId: String, toolCallId: String) {
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val msgIndex = messages.indexOfFirst { it.id == messageId }
            if (msgIndex >= 0) {
                val msg = messages[msgIndex]
                messages[msgIndex] = msg.copy(
                    toolExecutions = msg.toolExecutions.filter { it.toolCallId != toolCallId }
                )
            }
            state.copy(messages = messages)
        }
    }
}

@Immutable
data class ChatUiState(
    val messages:         List<UiMessage> = emptyList(),
    val isLoading:        Boolean         = false,
    val error:            String?         = null,
    val selectedModel:    String          = "",
    val activeProjectId:  Long?           = null,
    val workspacePath:    String?         = null,
    val totalInputTokens:  Int            = 0,
    val totalOutputTokens: Int            = 0,
    // ── Agent configs ──────────────────────────────────────────────
    val agentConfigs:  List<AgentConfig>  = emptyList(),
    val activeAgentId: String             = ""
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
    val status: ToolStatus = ToolStatus.PENDING,
    // For invoke_subagents — each subagent becomes a child
    val children: List<SubagentExecution> = emptyList()
)

@Immutable
data class SubagentExecution(
    val index: Int,
    val taskName: String,
    val prompt: String,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING
)

enum class ToolStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}
