package com.amaya.intelligence.impl.local

import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.local.dao.ConversationDao
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.data.repository.AiRepository
import com.amaya.intelligence.data.repository.AgentEvent
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.common.mappers.AgentUiMapper
import com.amaya.intelligence.impl.local.tools.LocalToolMapper
import com.amaya.intelligence.di.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local implementation of IntelligenceService.
 * Wraps AiRepository and handles persistence via ConversationDao.
 */
@Singleton
class LocalIntelligenceService @Inject constructor(
    private val aiRepository: AiRepository,
    private val conversationDao: ConversationDao,
    private val settingsManager: AiSettingsManager,
    @ApplicationScope private val scope: CoroutineScope
) : IntelligenceService {

    private val _uiState = MutableStateFlow(ChatUiState(
        sessionMode = IntelligenceSessionManager.SessionMode.LOCAL
    ))
    override val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    override val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _workspaces = MutableStateFlow<List<RemoteWorkspace>>(emptyList())
    override val workspaces: StateFlow<List<RemoteWorkspace>> = _workspaces.asStateFlow()

    private var chatJob: Job? = null
    private var currentConversationId: Long? = null
    private var currentAssistantMessageId: String? = null
    private val conversationSaveMutex = Mutex()

    init {
        // Observe conversations from DB
        scope.launch {
            conversationDao.getAllConversations().collect { list ->
                _conversations.value = list
            }
        }
        // Observe settings for agents
        scope.launch {
            settingsManager.settingsFlow.collect { settings ->
                val selectorItems = settings.agentConfigs.map { 
                    AgentUiMapper.mapToSelectorItem(it)
                }
                _uiState.update { it.copy(
                    agentConfigs = selectorItems,
                    activeAgentId = settings.activeAgentId,
                    selectedModel = settings.activeModel
                )}
            }
        }
    }

    override fun sendMessage(content: String) {
        chatJob?.cancel()
        currentAssistantMessageId = null
        
        val currentState = _uiState.value
        val userMsg = UiMessage(
            role = MessageRole.USER,
            content = content
        )
        
        // Optimistic update
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            isLoading = true
        )}

        // Persist first user message immediately so the conversation appears in sidebar.
        if (currentConversationId == null && _uiState.value.messages.size == 1) {
            saveCurrentConversation()
        }

        chatJob = scope.launch {
            try {
                // Map UiMessage to ChatMessage for repository
                val history = _uiState.value.messages.map { it.toChatMessage() }
                
                aiRepository.chat(
                    message = content,
                    conversationHistory = history.dropLast(1), // Exclude the one we just added
                    workspacePath = currentState.workspacePath,
                    activeAgentId = currentState.activeAgentId,
                    selectedModel = currentState.selectedModel
                ).collect { event ->
                    handleAgentEvent(event)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> {
                ensureAssistantMessage()
                updateCurrentAssistantMessage { msg ->
                    val newContent = msg.content + event.text
                    val lastStep = msg.steps.lastOrNull()
                    val newSteps = if (lastStep is MessageStep.Text) {
                        msg.steps.dropLast(1) + lastStep.copy(content = lastStep.content + event.text)
                    } else {
                        msg.steps + MessageStep.Text(content = event.text)
                    }
                    msg.copy(content = newContent, steps = newSteps)
                }
            }
            is AgentEvent.ToolCallStart -> {
                val normalizedName = LocalToolMapper.mapToolName(event.name)
                val normalizedArgs = LocalToolMapper.mapToolArgs(event.name, event.arguments)
                val toolExec = ToolExecution(
                    toolCallId = event.toolCallId,
                    name = normalizedName,
                    arguments = normalizedArgs,
                    status = ToolStatus.RUNNING,
                    metadata = mapOf(
                        "source" to "local",
                        "animateOnMount" to "true"
                    ),
                    uiMetadata = LocalToolMapper.getUiMetadata(event.name, event.arguments)
                )
                ensureAssistantMessage()
                updateCurrentAssistantMessage { msg ->
                    msg.copy(
                        toolExecutions = msg.toolExecutions + toolExec,
                        steps = msg.steps + MessageStep.ToolCall(execution = toolExec)
                    )
                }
            }
            is AgentEvent.ToolCallResult -> {
                updateCurrentAssistantMessage { msg ->
                    val updatedTools = msg.toolExecutions.map {
                        if (it.toolCallId == event.toolCallId) {
                            it.copy(
                                result = event.result,
                                status = if (event.isError) ToolStatus.ERROR else ToolStatus.SUCCESS
                            )
                        } else it
                    }
                    val updatedSteps = msg.steps.map { step ->
                        if (step is MessageStep.ToolCall && step.execution.toolCallId == event.toolCallId) {
                            step.copy(
                                execution = step.execution.copy(
                                    result = event.result,
                                    status = if (event.isError) ToolStatus.ERROR else ToolStatus.SUCCESS
                                )
                            )
                        } else step
                    }
                    msg.copy(toolExecutions = updatedTools, steps = updatedSteps)
                }
            }
            is AgentEvent.Error -> {
                _uiState.update { it.copy(error = event.message, isLoading = false) }
            }
            is AgentEvent.Done -> {
                _uiState.update { it.copy(isLoading = false) }
                saveCurrentConversation()
            }
            else -> {}
        }
    }

    private fun ensureAssistantMessage() {
        val assistantMetadata = currentAssistantMetadata()
        val assistantId = currentAssistantMessageId
        val state = _uiState.value
        val msgs = state.messages.toMutableList()
        val currentIdx = assistantId?.let { id -> msgs.indexOfLast { it.id == id } } ?: -1

        if (currentIdx == -1) {
            val assistantMsg = UiMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                metadata = assistantMetadata
            )
            currentAssistantMessageId = assistantMsg.id
            _uiState.value = state.copy(messages = msgs + assistantMsg)
            return
        }

        val existing = msgs[currentIdx]
        if (existing.metadata.isEmpty() && assistantMetadata.isNotEmpty()) {
            msgs[currentIdx] = existing.copy(metadata = assistantMetadata)
        }
        _uiState.value = state.copy(messages = msgs)
    }
    private fun currentAssistantMetadata(): Map<String, String> {
        val state = _uiState.value
        val agent = state.agentConfigs.firstOrNull { it.id == state.activeAgentId }
            ?: state.agentConfigs.firstOrNull()

        return buildMap {
            put("source", "local")
            agent?.name?.takeIf { it.isNotBlank() }?.let { put("agent_name", it) }
            agent?.modelId?.takeIf { it.isNotBlank() }?.let { put("model_id", it) }
            if (!containsKey("agent_name")) {
                agent?.id?.takeIf { it.isNotBlank() }?.let { put("agent_name", it) }
            }
        }
    }

    private fun updateCurrentAssistantMessage(update: (UiMessage) -> UiMessage) {
        val assistantId = currentAssistantMessageId
        if (assistantId == null) return

        val state = _uiState.value
        val msgs = state.messages.toMutableList()
        val assistantIdx = msgs.indexOfLast { it.id == assistantId }
        if (assistantIdx == -1) return

        msgs[assistantIdx] = update(msgs[assistantIdx])
        _uiState.value = state.copy(messages = msgs)
    }

    override fun stopGeneration() {
        chatJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun clearConversation() {
        chatJob?.cancel()
        currentConversationId = null
        currentAssistantMessageId = null
        _uiState.update { it.copy(
            conversationId = null,
            messages = emptyList(),
            error = null,
            isLoading = false
        )}
    }

    override fun loadConversation(id: String) {
        val longId = id.toLongOrNull() ?: return
        scope.launch {
            val entity = conversationDao.getConversationById(longId)
            entity?.let { conv ->
                currentConversationId = conv.id
                currentAssistantMessageId = null
                val messages = parseMessagesFromJson(conv.messagesJson)
                _uiState.update { it.copy(
                    conversationId = conv.id.toString(),
                    workspacePath = conv.workspacePath,
                    messages = messages,
                    totalInputTokens = 0,
                    totalOutputTokens = 0,
                    error = null
                )}
            }
        }
    }

    override fun deleteConversation(id: String) {
        val longId = id.toLongOrNull() ?: return
        scope.launch {
            conversationDao.deleteConversationById(longId)
            if (currentConversationId == longId) {
                clearConversation()
            }
        }
    }

    override fun setSelectedAgent(agentId: String) {
        scope.launch {
            val settings = settingsManager.getSettings()
            val agent = settings.agentConfigs.find { it.id == agentId }
            agent?.let {
                settingsManager.setActiveAgent(it.id, it.modelId)
            }
        }
    }

    override fun setWorkspace(path: String?) {
        _uiState.update { it.copy(workspacePath = path) }
    }

    override fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun loadMoreConversations() {
        // No-op for local for now
    }

    override fun hasMoreConversations(): Boolean {
        return false
    }

    override fun getProjectFiles(path: String) {
        // Local project files logic if needed
    }

    override fun respondToToolInteraction(executionId: String, confirmed: Boolean) {
        // Local tool interaction logic if needed
    }

    private fun parseMessagesFromJson(json: String): List<UiMessage> {
        if (json.isBlank()) return emptyList()
        return try {
            val messages = mutableListOf<UiMessage>()
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val role = when (obj.optString("role")) {
                    "USER" -> MessageRole.USER
                    "ASSISTANT" -> MessageRole.ASSISTANT
                    "SYSTEM" -> MessageRole.SYSTEM
                    else -> MessageRole.USER
                }

                val toolExecutions = mutableListOf<ToolExecution>()
                if (obj.has("toolExecutions")) {
                    val execArr = obj.getJSONArray("toolExecutions")
                    for (j in 0 until execArr.length()) {
                        val e = execArr.getJSONObject(j)
                        toolExecutions.add(parseToolExecutionFromJson(e))
                    }
                }

                val steps = mutableListOf<MessageStep>()
                if (obj.has("steps")) {
                    val stepsArr = obj.getJSONArray("steps")
                    for (j in 0 until stepsArr.length()) {
                        val s = stepsArr.getJSONObject(j)
                        val stepId = s.optString("id", UUID.randomUUID().toString())
                        when (s.optString("type")) {
                            "text" -> {
                                steps.add(MessageStep.Text(
                                    id = stepId,
                                    content = s.getString("content"),
                                    formattedContent = s.optString("formattedContent").takeIf { it.isNotBlank() }
                                ))
                            }
                            "toolCall" -> {
                                val eObj = s.getJSONObject("execution")
                                steps.add(MessageStep.ToolCall(
                                    id = stepId,
                                    execution = parseToolExecutionFromJson(eObj)
                                ))
                            }
                        }
                    }
                }

                val todoItems = mutableListOf<com.amaya.intelligence.tools.TodoItem>()
                if (obj.has("todoItems")) {
                    val todoArr = obj.getJSONArray("todoItems")
                    for (j in 0 until todoArr.length()) {
                        val t = todoArr.getJSONObject(j)
                        todoItems.add(
                            com.amaya.intelligence.tools.TodoItem(
                                id = t.getInt("id"),
                                content = t.optString("content").takeIf { it.isNotBlank() },
                                activeForm = t.optString("activeForm").takeIf { it.isNotBlank() },
                                status = runCatching {
                                    com.amaya.intelligence.tools.TodoStatus.valueOf(t.getString("status"))
                                }.getOrDefault(com.amaya.intelligence.tools.TodoStatus.PENDING)
                            )
                        )
                    }
                }

                val metadata = mutableMapOf<String, String>()
                if (obj.has("metadata")) {
                    val metaObj = obj.getJSONObject("metadata")
                    metaObj.keys().forEach { key ->
                        metadata[key] = metaObj.optString(key, "")
                    }
                }

                messages.add(
                    UiMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        role = role,
                        content = obj.optString("content"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        metadata = metadata,
                        toolExecutions = toolExecutions,
                        steps = steps,
                        todoItems = todoItems
                    )
                )
            }
            messages
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCurrentConversation() {
        scope.launch {
            conversationSaveMutex.withLock {
                val messages = _uiState.value.messages
                if (messages.isEmpty()) return@withLock
                val hasContent = messages.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() } ||
                    messages.any { it.role == MessageRole.USER && it.content.isNotBlank() }
                if (!hasContent) return@withLock

                try {
                    val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: "New Conversation"
                    val title = firstUserMsg.split("\\s+".toRegex()).take(5).joinToString(" ").take(50)
                    val now = System.currentTimeMillis()
                    val messagesJson = serializeMessagesToJson(messages)

                    if (currentConversationId != null) {
                        val existing = conversationDao.getConversationById(currentConversationId!!)
                        if (existing != null) {
                            conversationDao.updateConversation(
                                existing.copy(
                                    messagesJson = messagesJson,
                                    updatedAt = now
                                )
                            )
                        }
                    } else {
                        val newId = conversationDao.insertConversation(
                            ConversationEntity(
                                id = 0,
                                title = title,
                                workspacePath = _uiState.value.workspacePath,
                                messagesJson = messagesJson,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        currentConversationId = newId
                        _uiState.update { it.copy(conversationId = newId.toString()) }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun serializeMessagesToJson(messages: List<UiMessage>): String {
        val jsonArray = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("role", msg.role.name)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            }

            if (msg.toolExecutions.isNotEmpty()) {
                val execArr = JSONArray()
                msg.toolExecutions.forEach { exec ->
                    execArr.put(serializeToolExecutionToJson(exec))
                }
                obj.put("toolExecutions", execArr)
            }

            if (msg.steps.isNotEmpty()) {
                val stepsArr = JSONArray()
                msg.steps.forEach { step ->
                    val stepObj = JSONObject().apply {
                        put("id", step.id)
                        when (step) {
                            is MessageStep.Text -> {
                                put("type", "text")
                                put("content", step.content)
                                step.formattedContent?.let { put("formattedContent", it) }
                            }
                            is MessageStep.ToolCall -> {
                                put("type", "toolCall")
                                put("execution", serializeToolExecutionToJson(step.execution))
                            }
                        }
                    }
                    stepsArr.put(stepObj)
                }
                obj.put("steps", stepsArr)
            }

            if (msg.metadata.isNotEmpty()) {
                val metaObj = JSONObject()
                msg.metadata.forEach { (key, value) -> metaObj.put(key, value) }
                obj.put("metadata", metaObj)
            }

            if (msg.todoItems.isNotEmpty()) {
                val todoArr = JSONArray()
                msg.todoItems.forEach { todo ->
                    todoArr.put(
                        JSONObject().apply {
                            put("id", todo.id)
                            todo.content?.let { put("content", it) }
                            todo.activeForm?.let { put("activeForm", it) }
                            put("status", todo.status.name)
                        }
                    )
                }
                obj.put("todoItems", todoArr)
            }

            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
    private fun parseToolExecutionFromJson(e: JSONObject): ToolExecution {
        val argsMap = mutableMapOf<String, Any?>()
        if (e.has("arguments")) {
            val argsObj = e.getJSONObject("arguments")
            argsObj.keys().forEach { key -> argsMap[key] = argsObj.get(key) }
        }
        val children = mutableListOf<SubagentExecution>()
        if (e.has("children")) {
            val childArr = e.getJSONArray("children")
            for (k in 0 until childArr.length()) {
                val c = childArr.getJSONObject(k)
                children.add(
                    SubagentExecution(
                        index = c.getInt("index"),
                        taskName = c.getString("taskName"),
                        prompt = c.getString("prompt"),
                        result = c.optString("result").takeIf { it.isNotBlank() },
                        status = runCatching { ToolStatus.valueOf(c.getString("status")) }
                            .getOrDefault(ToolStatus.SUCCESS)
                    )
                )
            }
        }
        val metaMap = mutableMapOf<String, String>()
        if (e.has("metadata")) {
            val mObj = e.getJSONObject("metadata")
            mObj.keys().forEach { key -> metaMap[key] = mObj.getString(key) }
        } else {
            metaMap["source"] = "local"
        }
        return ToolExecution(
            toolCallId = e.getString("toolCallId"),
            name = e.getString("name"),
            arguments = argsMap,
            result = e.optString("result").takeIf { it.isNotBlank() },
            status = runCatching { ToolStatus.valueOf(e.getString("status")) }
                .getOrDefault(ToolStatus.SUCCESS),
            children = children,
            metadata = metaMap,
            uiMetadata = LocalToolMapper.getUiMetadata(
                toolName = e.getString("name"),
                args = argsMap
            )
        )
    }

    private fun serializeToolExecutionToJson(exec: ToolExecution): JSONObject {
        return JSONObject().apply {
            put("toolCallId", exec.toolCallId)
            put("name", exec.name)
            put("status", exec.status.name)
            exec.result?.let { put("result", it) }
            put("arguments", JSONObject().apply {
                exec.arguments.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
            })
            if (exec.children.isNotEmpty()) {
                val childArr = JSONArray()
                exec.children.forEach { child ->
                    childArr.put(
                        JSONObject().apply {
                            put("index", child.index)
                            put("taskName", child.taskName)
                            put("prompt", child.prompt)
                            child.result?.let { put("result", it) }
                            put("status", child.status.name)
                        }
                    )
                }
                put("children", childArr)
            }
            if (exec.metadata.isNotEmpty()) {
                val mObj = JSONObject()
                exec.metadata.forEach { (k, v) -> mObj.put(k, v) }
                put("metadata", mObj)
            }
        }
    }
}

// Extension to map domain to repository model
private fun UiMessage.toChatMessage(): ChatMessage {
    return ChatMessage(
        role = this.role,
        content = this.content
    )
}
