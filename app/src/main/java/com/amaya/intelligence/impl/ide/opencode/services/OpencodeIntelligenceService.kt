package com.amaya.intelligence.impl.ide.opencode.services

import com.amaya.intelligence.data.local.dao.ConversationDao
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.data.local.entity.ConversationScope
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.di.ApplicationScope
import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.bridge.AgentModes
import com.amaya.intelligence.domain.models.ModelOption
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.MessageStep
import com.amaya.intelligence.domain.models.finishThinking
import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.domain.models.ToolStatus
import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.impl.bridge.windows.toChatConnectionState
import com.amaya.intelligence.impl.common.conversation.ConversationJsonCodec
import com.amaya.intelligence.impl.bridge.windows.tools.WindowsBridgeController

import com.amaya.intelligence.impl.ide.opencode.OpencodeClient
import com.amaya.intelligence.impl.ide.opencode.OpencodeMessagePartUpdate
import com.amaya.intelligence.impl.ide.opencode.OpencodeModelSummary
import com.amaya.intelligence.impl.ide.opencode.OpencodeSessionSummary
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoRepository
import com.amaya.intelligence.tools.TodoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opencode implementation of [IntelligenceService]. Rides on the Windows Bridge
 * transport through [OpencodeClient] + [WindowsBridgeController]. History
 * persists to the conversation DB under [ConversationScope.OPENCODE].
 */
@Singleton
class OpencodeIntelligenceService @Inject constructor(
    private val opencodeClient: OpencodeClient,
    private val bridgeController: WindowsBridgeController,
    private val conversationDao: ConversationDao,
    private val todoRepository: TodoRepository,
    @ApplicationScope private val scope: CoroutineScope
) : IntelligenceService {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            sessionMode = IntelligenceSessionManager.SessionMode.OPENCODE,
            connectionState = bridgeController.currentConnectionState().toChatConnectionState(),
            conversationModeId = AgentModes.BUILD
        )
    )
    override val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    override val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _projectFiles = MutableStateFlow<List<ProjectFileEntry>>(emptyList())
    override val projectFiles: StateFlow<List<ProjectFileEntry>> = _projectFiles.asStateFlow()

    private val _projectPath = MutableStateFlow("")
    override val projectPath: StateFlow<String> = _projectPath.asStateFlow()

    private val _workspaces = MutableStateFlow<List<RemoteWorkspace>>(emptyList())
    override val workspaces: StateFlow<List<RemoteWorkspace>> = _workspaces.asStateFlow()

    private val _mode = MutableStateFlow(AgentModes.BUILD)
    val mode: StateFlow<String> = _mode.asStateFlow()

    @Volatile private var activeSessionId: String? = null
    @Volatile private var currentAssistantMessageId: String? = null
    @Volatile private var pendingPrompt: String? = null
    @Volatile private var currentRoomConversationId: Long? = null
    private val persistMutex = Mutex()

    init {
        opencodeClient.attach(scope)
        scope.launch {
            conversationDao.observeConversationsByScope(ConversationScope.OPENCODE.wireName)
                .collect { entities -> _conversations.value = entities }
        }
        scope.launch {
            opencodeClient.events.collect { event -> handleEvent(event) }
        }
        scope.launch {
            while (true) {
                _uiState.update { it.copy(connectionState = bridgeController.currentConnectionState().toChatConnectionState()) }
                delay(750)
            }
        }
        scope.launch {
            opencodeClient.runtime.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        sessionMode = IntelligenceSessionManager.SessionMode.OPENCODE,
                        error = snapshot.lastError
                    )
                }
                // As soon as the opencode runtime is ready, pull providers,
                // models, and sessions so the sidebar + agent picker have
                // something to show.
                if (snapshot.isReady) {
                    opencodeClient.requestProviders()
                    opencodeClient.requestModels()
                    opencodeClient.requestSessions()
                }
            }
        }
    }

    // ── Mode / routing helpers ──────────────────────────────────────────────

    fun setMode(newMode: String) {
        _mode.value = when (newMode) {
            AgentModes.BUILD, AgentModes.PLAN -> newMode
            else -> AgentModes.BUILD
        }
        _uiState.update { it.copy(conversationModeId = _mode.value) }
    }

    override fun setConversationModeId(modeId: String) {
        setMode(modeId)
    }

    // ── IntelligenceService ────────────────────────────────────────────────

    override fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val userMsg = UiMessage(role = MessageRole.USER, content = trimmed)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                isStreaming = true,
                error = null,
                sessionMode = IntelligenceSessionManager.SessionMode.OPENCODE
            )
        }
        scheduleSend(trimmed)
    }

    override fun stopGeneration() {
        val session = activeSessionId ?: return
        opencodeClient.abortPrompt(session)
        _uiState.update { it.copy(isLoading = false, isStreaming = false) }
    }

    override fun regenerateLastResponse() {
        if (_uiState.value.isStreaming || _uiState.value.isLoading) {
            _uiState.update { it.copy(error = "Wait for the current response before regenerating") }
            return
        }
        val state = _uiState.value
        val lastUserIdx = state.messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIdx < 0) return
        val lastUser = state.messages[lastUserIdx]
        if (lastUser.content.isBlank() && lastUser.attachments.isEmpty()) return
        // Drop the trailing assistant turn and re-run the last prompt against the same session.
        // The prompt is sent directly (not through sendMessage) so no duplicate user bubble is
        // appended; the bridge already holds the conversation history server-side.
        currentAssistantMessageId = null
        _uiState.update {
            it.copy(
                messages = it.messages.take(lastUserIdx + 1),
                isLoading = true,
                isStreaming = true,
                error = null
            )
        }
        scheduleSend(lastUser.content)
    }

    override fun clearConversation() {
        currentAssistantMessageId = null
        val previous = activeSessionId
        activeSessionId = null
        currentRoomConversationId = null
        todoRepository.clear()
        _uiState.update {
            it.copy(
                conversationId = null,
                messages = emptyList(),
                error = null,
                isLoading = false,
                isStreaming = false,
                sessionMode = IntelligenceSessionManager.SessionMode.OPENCODE
            )
        }
        previous?.let { opencodeClient.deleteSession(it) }
    }

    override fun loadConversation(id: String) {
        val longId = id.toLongOrNull()
        if (longId != null) {
            scope.launch {
                val entity = conversationDao.getConversationById(longId)
                    ?.takeIf { it.scope == ConversationScope.OPENCODE.wireName }
                    ?: return@launch
                currentRoomConversationId = entity.id
                val parsed = ConversationJsonCodec.parseOpencode(entity.messagesJson)
                val opencodeSession = ConversationJsonCodec.extractOpencodeSessionId(entity.messagesJson)
                activeSessionId = opencodeSession
                currentAssistantMessageId = null
                _uiState.update {
                    it.copy(
                        conversationId = entity.id.toString(),
                        messages = parsed,
                        isLoading = opencodeSession != null && parsed.isEmpty(),
                        isStreaming = false,
                        error = null
                    )
                }
                // Always ask opencode for the authoritative history so the UI
                // reflects what actually happened on the server, even if our
                // local Room copy is empty (e.g. sessions created via TUI).
                opencodeSession?.let { opencodeClient.requestSessionMessages(it) }
            }
        } else {
            activeSessionId = id
            _uiState.update { it.copy(conversationId = id) }
            opencodeClient.requestSessionMessages(id)
        }
    }

    override fun deleteConversation(id: String) {
        val longId = id.toLongOrNull()
        if (longId != null) {
            scope.launch {
                val entity = conversationDao.getConversationById(longId) ?: return@launch
                if (entity.scope != ConversationScope.OPENCODE.wireName) return@launch
                val opencodeSession = ConversationJsonCodec.extractOpencodeSessionId(entity.messagesJson)
                conversationDao.deleteConversationById(longId)
                if (currentRoomConversationId == longId) {
                    currentRoomConversationId = null
                    if (activeSessionId == opencodeSession) {
                        activeSessionId = null
                        _uiState.update {
                            it.copy(conversationId = null, messages = emptyList())
                        }
                    }
                }
                opencodeSession?.let { opencodeClient.deleteSession(it) }
            }
        } else {
            opencodeClient.deleteSession(id)
            if (activeSessionId == id) {
                activeSessionId = null
                _uiState.update { it.copy(conversationId = null) }
            }
        }
    }

    override fun selectModel(modelKey: String) {
        val parts = modelKey.split('|', limit = 3)
        if (parts.size == 3 && parts[0] == "opencode") {
            val providerId = parts[1]
            val modelId = parts[2]
            _uiState.update {
                it.copy(
                    activeModelKey = modelKey,
                    selectedModel = modelId
                )
            }
        } else {
            _uiState.update { it.copy(activeModelKey = modelKey, selectedModel = modelKey) }
        }
    }

    override fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ponytail: ceiling = Opencode JSON-RPC envelope has no per-turn reasoning field.
    // Visual-only state update keeps the chat bulb consistent with other runtimes.
    override fun setEffort(effort: com.amaya.intelligence.data.remote.api.ThinkingEffort) {
        _uiState.update { it.copy(effort = effort) }
    }

    override fun refreshModels() {
        opencodeClient.requestProviders()
        opencodeClient.requestModels()
    }

    override fun resync() {
        opencodeClient.requestRuntimeStatus()
        opencodeClient.requestSessions()
    }

    override fun refreshState() {
        resync()
    }

    override fun respondToToolInteraction(executionId: String, confirmed: Boolean) {
        opencodeClient.respondCurrentPermission(if (confirmed) "once" else "reject")
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun scheduleSend(content: String) {
        val existing = activeSessionId
        val currentMode = _mode.value
        val activeOption = _uiState.value.modelOptions.firstOrNull { it.id == _uiState.value.activeModelKey }
        val providerId = activeOption?.providerId?.ifBlank { null }
        val modelId = _uiState.value.selectedModel.ifBlank { null }
        if (existing != null) {
            opencodeClient.sendPrompt(
                sessionId = existing,
                text = content,
                agent = currentMode,
                providerId = providerId,
                modelId = modelId
            )
        } else {
            pendingPrompt = content
            opencodeClient.createSession(
                title = content.take(48),
                agent = currentMode,
                modelId = modelId,
                providerId = providerId
            )
        }
    }

    private fun handleEvent(event: OpencodeClient.Event) {
        when (event) {
            is OpencodeClient.Event.SessionCreated -> {
                activeSessionId = event.session.sessionId
                _uiState.update { it.copy(conversationId = event.session.sessionId) }
                pendingPrompt?.let { content ->
                    opencodeClient.sendPrompt(
                        sessionId = event.session.sessionId,
                        text = content,
                        agent = _mode.value,
                        providerId = _uiState.value.modelOptions
                            .firstOrNull { it.id == _uiState.value.activeModelKey }
                            ?.providerId
                            ?.ifBlank { null },
                        modelId = _uiState.value.selectedModel.ifBlank { null }
                    )
                }
                pendingPrompt = null
                persistCurrentConversationAsync()
                opencodeClient.requestSessions()
            }
            is OpencodeClient.Event.SessionDeleted -> {
                if (event.sessionId == activeSessionId) {
                    activeSessionId = null
                    _uiState.update { it.copy(conversationId = null) }
                }
                scope.launch { purgeConversationByOpencodeSessionId(event.sessionId) }
            }
            is OpencodeClient.Event.MessagePart -> handleMessagePart(event.update)
            is OpencodeClient.Event.SessionStatus -> handleSessionStatus(event)
            is OpencodeClient.Event.SessionError -> _uiState.update {
                it.copy(error = event.message, isLoading = false, isStreaming = false)
            }
            is OpencodeClient.Event.PermissionAsked -> Unit
            is OpencodeClient.Event.PlanUpdate -> handlePlanUpdate(event.entries)
            is OpencodeClient.Event.TodoUpdate -> handleTodoUpdate(event.todos)
            is OpencodeClient.Event.Sessions -> handleSessionList(event.sessions)
            is OpencodeClient.Event.SessionMessages -> handleHistoryLoaded(event.sessionId, event.messages)
            is OpencodeClient.Event.Providers -> Unit
            is OpencodeClient.Event.Models -> handleModelList(
                models = event.models,
                defaultProviderId = event.defaultProviderId,
                defaultModelId = event.defaultModelId
            )
            is OpencodeClient.Event.Mcp -> Unit
            is OpencodeClient.Event.Runtime -> Unit
            is OpencodeClient.Event.Config -> Unit
            is OpencodeClient.Event.Error -> _uiState.update {
                it.copy(error = event.message, isLoading = false, isStreaming = false)
            }
        }
    }

    private fun handleSessionStatus(event: OpencodeClient.Event.SessionStatus) {
        // Opencode signals stream completion via `session.idle` (kind mapped onto
        // SessionStatus in the client) or the data blob `idle = true`. Use either
        // signal to flip streaming indicators.
        val raw = event.data
        val idle = raw["opencodeType"] == "session.idle" ||
            (raw["status"] as? Map<*, *>)?.get("type") == "idle" ||
            raw["idle"] == true
        if (idle) {
            // Mark the last assistant message terminal so UI surfaces that key
            // off `completedAt` (e.g. ThinkingCard's auto-collapse) fire once,
            // at genuine turn end — mirroring LocalIntelligenceService's
            // markCurrentAssistantTerminal and antigravity's
            // markLastAssistantCompleted. Without this the thinking card has no
            // stable terminal signal and never auto-collapses for opencode.
            _uiState.update { state ->
                val nowMs = System.currentTimeMillis()
                val now = nowMs.toString()
                val idx = state.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                val messages = if (idx == -1) state.messages else {
                    val msg = state.messages[idx]
                    if (msg.metadata["completedAt"] != null) state.messages
                    else {
                        val durationMs = msg.thinkingStartedAt?.let { (nowMs - it).coerceAtLeast(0L) }
                        state.messages.toMutableList().apply {
                            this[idx] = msg.copy(
                                metadata = msg.metadata + ("completedAt" to now),
                                isThinking = false,
                                thinkingDurationMs = msg.thinkingDurationMs ?: durationMs
                            )
                        }
                    }
                }
                state.copy(messages = messages, isLoading = false, isStreaming = false)
            }
        }
    }

    private fun handleHistoryLoaded(
        sessionId: String,
        messages: List<Map<String, Any?>>
    ) {
        if (sessionId != activeSessionId) return
        val parsed = messages.mapNotNull { raw ->
            mapOpencodeMessageEntry(raw)
        }
        currentAssistantMessageId = null
        _uiState.update {
            it.copy(
                messages = parsed,
                isLoading = false,
                isStreaming = false
            )
        }
        persistCurrentConversationAsync()
    }

    /**
     * Convert a single `GET /session/{id}/message` entry to a [UiMessage]. The
     * shape is `{ info: { ..., role }, parts: [{ type, text, ... }] }`.
     */
    private fun mapOpencodeMessageEntry(raw: Map<String, Any?>): UiMessage? {
        val info = raw["info"] as? Map<*, *> ?: raw
        val role = when ((info["role"] as? String)?.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> return null
        }
        @Suppress("UNCHECKED_CAST")
        val parts = (raw["parts"] as? List<Map<String, Any?>>).orEmpty()
        val steps = mutableListOf<MessageStep>()
        val tools = mutableListOf<ToolExecution>()
        val textBuilder = StringBuilder()
        for (part in parts) {
            when (part["type"]) {
                "text" -> {
                    val text = (part["text"] as? String).orEmpty()
                    if (text.isNotBlank()) {
                        steps.add(
                            MessageStep.Text(
                                id = (part["id"] as? String) ?: UUID.randomUUID().toString(),
                                content = text
                            )
                        )
                        textBuilder.append(text)
                    }
                }
                "tool", "tool_use" -> {
                    val toolName = (part["tool"] as? String) ?: "tool"
                    val state = part["state"] as? Map<*, *>
                    val status = when (state?.get("status") as? String) {
                        "completed", "success" -> ToolStatus.SUCCESS
                        "error", "failed" -> ToolStatus.ERROR
                        else -> ToolStatus.SUCCESS
                    }
                    val exec = ToolExecution(
                        toolCallId = (part["id"] as? String) ?: UUID.randomUUID().toString(),
                        name = toolName,
                        arguments = emptyMap(),
                        status = status
                    )
                    tools.add(exec)
                    steps.add(MessageStep.ToolCall(execution = exec))
                }
                else -> Unit
            }
        }
        val timestamp = (info["time"] as? Map<*, *>)?.get("created") as? Number
        return UiMessage(
            id = (info["id"] as? String) ?: UUID.randomUUID().toString(),
            role = role,
            content = textBuilder.toString(),
            timestamp = timestamp?.toLong() ?: System.currentTimeMillis(),
            toolExecutions = tools,
            steps = steps
        )
    }

    private fun handleMessagePart(update: OpencodeMessagePartUpdate) {
        when (update.partType) {
            OpencodeMessagePartUpdate.PartType.TEXT -> replaceAssistantText(update)
            OpencodeMessagePartUpdate.PartType.THOUGHT -> replaceThinking(update)
            OpencodeMessagePartUpdate.PartType.TOOL -> upsertToolStep(update)
            OpencodeMessagePartUpdate.PartType.OTHER -> Unit
        }
        if (update.timeEnd != null) {
            _uiState.update { it.copy(isLoading = false, isStreaming = false) }
        }
        persistCurrentConversationAsync()
    }

    private fun replaceAssistantText(update: OpencodeMessagePartUpdate) {
        ensureAssistantMessage()
        val partId = update.partId ?: DEFAULT_TEXT_PART_ID
        _uiState.update { state ->
            val id = currentAssistantMessageId ?: return@update state
            state.copy(messages = state.messages.map { msg ->
                if (msg.id != id) return@map msg
                val steps = replaceTextStep(msg.steps, partId, update.text)
                val newContent = steps.filterIsInstance<MessageStep.Text>()
                    .joinToString(separator = "") { it.content }
                msg.finishThinking().copy(content = newContent, steps = steps)
            })
        }
    }

    private fun replaceTextStep(
        steps: List<MessageStep>,
        partId: String,
        text: String
    ): List<MessageStep> {
        val existingIndex = steps.indexOfFirst { it is MessageStep.Text && it.id == partId }
        return if (existingIndex >= 0) {
            val step = steps[existingIndex] as MessageStep.Text
            steps.toMutableList().apply {
                set(existingIndex, step.copy(content = text))
            }
        } else {
            steps + MessageStep.Text(id = partId, content = text)
        }
    }

    private fun replaceThinking(update: OpencodeMessagePartUpdate) {
        ensureAssistantMessage()
        _uiState.update { state ->
            val id = currentAssistantMessageId ?: return@update state
            state.copy(messages = state.messages.map { msg ->
                if (msg.id != id) msg
                else {
                    val current = msg.steps.lastOrNull() as? MessageStep.Thinking
                    val startedAt = current?.startedAt ?: System.currentTimeMillis()
                    val step = MessageStep.Thinking(
                        id = current?.id ?: update.partId ?: UUID.randomUUID().toString(),
                        text = update.text,
                        isStreaming = update.timeEnd == null,
                        startedAt = startedAt
                    )
                    msg.copy(
                        thinking = update.text,
                        isThinking = step.isStreaming,
                        thinkingStartedAt = startedAt,
                        steps = if (current == null) msg.steps + step else msg.steps.dropLast(1) + step
                    )
                }
            })
        }
    }

    private fun upsertToolStep(update: OpencodeMessagePartUpdate) {
        val toolName = update.toolName ?: return
        ensureAssistantMessage()
        _uiState.update { state ->
            val id = currentAssistantMessageId ?: return@update state
            state.copy(messages = state.messages.map { msg ->
                if (msg.id != id) return@map msg
                val callId = update.partId ?: toolName
                val existing = msg.toolExecutions.firstOrNull { it.toolCallId == callId }
                val nextStatus = when (update.toolState) {
                    "completed", "success" -> ToolStatus.SUCCESS
                    "error", "failed" -> ToolStatus.ERROR
                    else -> ToolStatus.RUNNING
                }
                if (existing == null) {
                    val execution = ToolExecution(
                        toolCallId = callId,
                        name = toolName,
                        arguments = emptyMap(),
                        status = nextStatus
                    )
                    msg.finishThinking().copy(
                        toolExecutions = msg.toolExecutions + execution,
                        steps = msg.steps + MessageStep.ToolCall(execution = execution)
                    )
                } else {
                    val updated = existing.copy(status = nextStatus)
                    msg.finishThinking().copy(
                        toolExecutions = msg.toolExecutions.map { if (it.toolCallId == callId) updated else it },
                        steps = msg.steps.map { step ->
                            if (step is MessageStep.ToolCall && step.execution.toolCallId == callId) {
                                step.copy(execution = updated)
                            } else step
                        }
                    )
                }
            })
        }
    }

    private fun handlePlanUpdate(entries: List<Map<String, Any?>>) {
        val todos = entries.mapIndexedNotNull { index, entry ->
            val content = entry["content"] as? String ?: return@mapIndexedNotNull null
            val status = when (entry["status"] as? String) {
                "completed" -> TodoStatus.COMPLETED
                "in_progress" -> TodoStatus.IN_PROGRESS
                else -> TodoStatus.PENDING
            }
            TodoItem(
                id = index,
                status = status,
                content = content
            )
        }
        todoRepository.replaceAll(todos)
    }

    private fun handleTodoUpdate(todos: List<Map<String, Any?>>) {
        val items = todos.mapIndexedNotNull { index, entry ->
            val content = entry["content"] as? String ?: return@mapIndexedNotNull null
            val status = when (entry["status"] as? String) {
                "completed" -> TodoStatus.COMPLETED
                "in_progress" -> TodoStatus.IN_PROGRESS
                else -> TodoStatus.PENDING
            }
            TodoItem(
                id = index,
                status = status,
                content = content
            )
        }
        todoRepository.replaceAll(items)
    }

    private fun UiMessage.finishThinking(nowMs: Long = System.currentTimeMillis()): UiMessage {
        val finishedSteps = steps.finishThinking(nowMs)
        if (!isThinking && thinkingDurationMs != null && finishedSteps === steps) return this
        val durationMs = thinkingStartedAt?.let { (nowMs - it).coerceAtLeast(0L) }
        return copy(isThinking = false, thinkingDurationMs = thinkingDurationMs ?: durationMs, steps = finishedSteps)
    }

    private fun ensureAssistantMessage() {
        val id = currentAssistantMessageId
        if (id != null && _uiState.value.messages.any { it.id == id }) return
        val msg = UiMessage(role = MessageRole.ASSISTANT, content = "")
        currentAssistantMessageId = msg.id
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private companion object {
        const val DEFAULT_TEXT_PART_ID = "assistant-text"
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private fun persistCurrentConversationAsync() {
        scope.launch { persistCurrentConversation() }
    }

    private suspend fun persistCurrentConversation(): Long? = persistMutex.withLock {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return@withLock currentRoomConversationId
        val opencodeSessionId = activeSessionId
        val title = messages.firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.split("\\s+".toRegex())
            ?.take(5)
            ?.joinToString(" ")
            ?.take(50)
            ?.ifBlank { "Opencode Chat" }
            ?: "Opencode Chat"
        val now = System.currentTimeMillis()
        val json = ConversationJsonCodec.serializeOpencode(messages, opencodeSessionId)
        val existing = currentRoomConversationId
        return@withLock if (existing != null) {
            val row = conversationDao.getConversationById(existing)
            if (row != null && row.scope == ConversationScope.OPENCODE.wireName) {
                conversationDao.updateConversation(row.copy(messagesJson = json, updatedAt = now))
                existing
            } else {
                currentRoomConversationId = null
                insertConversation(title, json, now)
            }
        } else {
            insertConversation(title, json, now)
        }
    }

    private suspend fun insertConversation(title: String, json: String, now: Long): Long {
        val id = conversationDao.insertConversation(
            ConversationEntity(
                title = title,
                workspacePath = null,
                messagesJson = json,
                createdAt = now,
                updatedAt = now,
                scope = ConversationScope.OPENCODE.wireName
            )
        )
        currentRoomConversationId = id
        _uiState.update { it.copy(conversationId = id.toString()) }
        return id
    }


    // ── Models → ModelOption ───────────────────────────────────────────────

    private fun handleModelList(
        models: List<OpencodeModelSummary>,
        defaultProviderId: String?,
        defaultModelId: String?
    ) {
        val items = models.map { model ->
            ModelOption(
                id = "opencode|${model.providerId}|${model.modelId}",
                name = model.displayName.ifBlank { model.modelId },
                modelId = model.modelId,
                iconType = model.providerId.ifBlank { "default" },
                providerId = model.providerId,
                providerName = model.providerId.replaceFirstChar { it.uppercase() },
                isRemote = true,
                supportsImages = model.supportsImages
            )
        }
        val currentOption = _uiState.value.modelOptions.firstOrNull { it.id == _uiState.value.activeModelKey }
        val currentProvider = currentOption?.providerId?.ifBlank { null }
        val currentModel = _uiState.value.selectedModel.ifBlank { null }
        val effectiveProvider = currentProvider ?: defaultProviderId
        val effectiveModel = currentModel ?: defaultModelId
        val effectiveId = if (effectiveProvider != null && effectiveModel != null) {
            "opencode|$effectiveProvider|$effectiveModel"
        } else {
            items.firstOrNull()?.id
        }
        _uiState.update {
            it.copy(
                modelOptions = items,
                activeModelKey = effectiveId ?: it.activeModelKey,
                selectedModel = effectiveModel ?: it.selectedModel
            )
        }
    }

    // ── Opencode session list → ConversationEntity rows ────────────────────

    private fun handleSessionList(sessions: List<OpencodeSessionSummary>) {
        scope.launch {
            val existing = conversationDao.getOpencodeConversations()
            val existingBySessionId: Map<String, com.amaya.intelligence.data.local.entity.ConversationEntity> =
                existing.mapNotNull { row ->
                    val sid = ConversationJsonCodec.extractOpencodeSessionId(row.messagesJson) ?: return@mapNotNull null
                    sid to row
                }.toMap()
            for (session in sessions) {
                val prior = existingBySessionId[session.sessionId]
                if (prior == null) {
                    val title = session.title
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Opencode session"
                    val json = JSONObject().apply {
                        put("opencodeSessionId", session.sessionId)
                        put("messages", JSONArray())
                    }.toString()
                    conversationDao.insertConversation(
                        com.amaya.intelligence.data.local.entity.ConversationEntity(
                            title = title.take(60),
                            workspacePath = null,
                            messagesJson = json,
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt,
                            scope = ConversationScope.OPENCODE.wireName
                        )
                    )
                } else {
                    val newTitle = session.title
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: prior.title
                    if (prior.title != newTitle || prior.updatedAt < session.updatedAt) {
                        conversationDao.updateConversation(
                            prior.copy(title = newTitle.take(60), updatedAt = session.updatedAt)
                        )
                    }
                }
            }
        }
    }

    private suspend fun purgeConversationByOpencodeSessionId(sessionId: String) {
        val rows = conversationDao.getOpencodeConversations()
        for (row in rows) {
            val sid = ConversationJsonCodec.extractOpencodeSessionId(row.messagesJson)
            if (sid == sessionId) {
                conversationDao.deleteConversationById(row.id)
            }
        }
    }

}
