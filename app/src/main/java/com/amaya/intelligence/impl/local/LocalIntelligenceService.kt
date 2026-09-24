package com.amaya.intelligence.impl.local

import com.amaya.intelligence.domain.ai.IntelligenceService
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.ai.PendingClarification
import com.amaya.intelligence.data.remote.api.AiSettingsManager

import com.amaya.intelligence.data.remote.api.MessageRole

import com.amaya.intelligence.data.local.dao.ConversationDao
import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.ProjectDao
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.data.repository.AiRepository
import com.amaya.intelligence.data.repository.AgentConversationRepository
import com.amaya.intelligence.data.repository.SessionMemoryRepository

import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.common.mappers.ModelUiMapper
import com.amaya.intelligence.impl.local.browser.BrowserSessionManager
import com.amaya.intelligence.impl.local.tools.LocalToolMapper
import com.amaya.intelligence.di.ApplicationScope
import com.amaya.intelligence.tools.ClarificationRequest
import com.amaya.intelligence.tools.ConfirmationRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.amaya.intelligence.tools.SubagentResult
import com.amaya.intelligence.util.StreamDebugLog
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

/**
 * Local implementation of IntelligenceService.
 * Wraps AiRepository and handles persistence via ConversationDao.
 */
@Singleton
class LocalIntelligenceService @Inject constructor(
    internal val aiRepository: AiRepository,
    internal val conversationDao: ConversationDao,
    internal val agentDao: AgentDao,
    internal val projectDao: ProjectDao,
    internal val sessionMemoryRepository: SessionMemoryRepository,
    internal val agentConversationRepository: AgentConversationRepository,
    internal val ledgerStore: com.amaya.intelligence.data.repository.ActiveContextLedgerStore,
    internal val settingsManager: AiSettingsManager,
    internal val browserSessionManager: BrowserSessionManager,
    @ApplicationContext internal val appContext: Context,
    @ApplicationScope appScope: CoroutineScope
) : IntelligenceService {

    // Confine mutable chat/UI state to the main thread while retaining process lifetime.
    internal val scope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    internal val _uiState = MutableStateFlow(ChatUiState(
        sessionMode = IntelligenceSessionManager.SessionMode.LOCAL
    ))
    override val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    internal val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    override val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()
    override val allLocalConversations: StateFlow<List<ConversationEntity>> = conversationDao.observeAllConversations()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    internal val _runningSessions = MutableStateFlow<List<RunningSession>>(emptyList())
    override val runningSessions: StateFlow<List<RunningSession>> = _runningSessions.asStateFlow()
    internal val _completedSessions = kotlinx.coroutines.flow.MutableSharedFlow<RunningSession>(extraBufferCapacity = 16)
    override val completedSessions: kotlinx.coroutines.flow.SharedFlow<RunningSession> = _completedSessions

    internal val _workspaces = MutableStateFlow<List<RemoteWorkspace>>(emptyList())
    override val workspaces: StateFlow<List<RemoteWorkspace>> = _workspaces.asStateFlow()

    internal var chatJob: Job? = null
    internal var compactJob: Job? = null
    @Volatile internal var currentConversationId: Long? = null
    internal var currentAssistantMessageId: String? = null
    internal val assistantTextBuffer = StringBuilder()
    internal val assistantThinkingBuffer = StringBuilder()
    internal var lastAssistantTextUiEmitAt = 0L
    internal var assistantFlushJob: Job? = null
    internal var thinkingFlushJob: Job? = null
    internal val conversationSaveMutex = Mutex()
    internal val conversationStartMutex = Mutex()
    internal val pendingToolConfirmations = ToolConfirmationRegistry()
    internal val pendingConfirmationUi = ConcurrentHashMap<String, ConfirmationRequest>()
    internal val pendingApprovalIds = ConcurrentHashMap<String, String>()

    internal val pendingClarifications = ToolClarificationRegistry()
    internal val pendingClarificationUi = ConcurrentHashMap<String, ClarificationRequest>()
    internal val pendingClarificationIds = ConcurrentHashMap<String, String>()
    internal val _pendingClarification = MutableStateFlow<PendingClarification?>(null)
    override val pendingClarification: StateFlow<PendingClarification?> = _pendingClarification
    internal val titleJobs = ConcurrentHashMap<Long, Job>()
    internal val nextTurnId = AtomicLong(0L)
    internal val targetEpoch = AtomicLong(0L)
    internal val activeTurns = ConcurrentHashMap<Long, LocalTurn>()
    internal val startingConversations = ConcurrentHashMap<Long, Long>()
    internal val startingNewTurnId = AtomicLong(0L)
    internal val stoppingConversations = ConcurrentHashMap.newKeySet<Long>()
    internal val queuedConversationEvents = ConcurrentHashMap<Long, java.util.concurrent.ConcurrentLinkedQueue<UiMessage>>()
    internal val injectingConversationEventTasks = ConcurrentHashMap.newKeySet<String>()
    /** One continuation owns each terminal completion batch; concurrent completions coalesce here. */
    internal val continuationJobs = ConcurrentHashMap<Long, Job>()
    internal val continuationRequested = ConcurrentHashMap.newKeySet<Long>()
    internal val delegationCompletionLocks = ConcurrentHashMap<Long, Mutex>()
    internal val expectedDelegationTasks = ConcurrentHashMap<Long, MutableSet<Long>>()
    internal val lastDelegationCompletionAt = ConcurrentHashMap<Long, Long>()
    internal data class DeferredDelegationCompletion(val result: String, val failed: Boolean)
    internal val deferredDelegationCompletions = ConcurrentHashMap<Long, DeferredDelegationCompletion>()
    internal val nextDelegationEventOrder = AtomicLong(System.currentTimeMillis() * 1_000L)
    internal val turnsById = ConcurrentHashMap<Long, LocalTurn>()
    internal data class PendingMessage(
        val content: String,
        val images: List<com.amaya.intelligence.data.remote.api.ChatImage>
    )
    internal data class LocalTurn(
        val turnId: Long,
        val conversationId: Long,
        val prompt: String,
        val isNewConversation: Boolean,
        var state: ChatUiState,
        val notificationTitle: String,
        val notificationSender: String,
        val notificationThreadKey: String,
        var assistantMessageId: String? = null,
        var job: Job? = null,
        var delegationActive: Boolean = false,
        var lastStatus: String = "Streaming",
        var lastDetail: String = "Waiting for response",
        var lastNotificationAt: Long = 0L,
        var delegateTotal: Int = 0,
        var delegateCompleted: Int = 0,
        var activeDelegateName: String? = null,
        var pendingMessage: PendingMessage? = null,
        /**
         * Streamed assistant text not yet folded into canonicalHistory. Buffered so the hot path
         * costs an append instead of re-parsing and re-serializing the whole accumulated response
         * on every delta, on the main thread.
         */
        val pendingCanonicalText: StringBuilder = StringBuilder()
    )

    /** Take the buffered assistant text, if any, so it can be committed before the next entry. */
    internal fun LocalTurn.drainCanonicalText(): String? {
        if (pendingCanonicalText.isEmpty()) return null
        val text = pendingCanonicalText.toString()
        pendingCanonicalText.setLength(0)
        return text
    }

    init {
        scope.launch { recoverInterruptedTurns() }

        // Keep the drawer scoped to Chat/Project. An Agent owns exactly one conversation.
        scope.launch {
            _uiState
                .map { Triple(it.assistantMode, it.ownerId, it.agentId) }
                .distinctUntilChanged()
                .collectLatest { (mode, ownerId, agentId) ->
                    val source = if (mode == AssistantMode.AGENT && agentId != null) {
                        conversationDao.observeAgentConversation(agentId)
                    } else {
                        conversationDao.observeOwnedConversations(mode.name, ownerId)
                    }
                    source.collect { list -> _conversations.value = list }
                }
        }
        scope.launch {
            settingsManager.settingsFlow.collect { settings ->
                val options = settings.connections.flatMap { connection ->
                    connection.visibleModels.mapNotNull { model ->
                        ModelUiMapper.mapConnectionModel(connection, model)
                    }
                }
            // Load persisted effort when active model changes.
            val persistedEffort = settings.activeSelection?.let { sel ->
                settingsManager.getThinkingEffort(sel.connectionId, sel.modelId)
            } ?: com.amaya.intelligence.data.remote.api.ThinkingEffort.MEDIUM
            _uiState.update {
                it.copy(
                    modelOptions = options,
                    activeModelKey = settings.activeSelection?.key.orEmpty(),
                    selectedModel = settings.activeSelection?.modelId.orEmpty(),
                    effort = persistedEffort
                )
            }
            }
        }
    }

    override fun sendMessage(content: String) = sendMessageInternal(content, emptyList())

    override suspend fun sendMessageToConversation(conversationId: Long, content: String): Boolean =
        sendMessageToConversationImpl(conversationId, content)

    suspend fun runDelegatedAgentTurn(conversationId: Long, request: String): SubagentResult =
        runDelegatedAgentTurnImpl(conversationId, request)

    suspend fun completeDelegationEvent(
        conversationId: Long,
        taskId: Long,
        title: String,
        sourceAgentName: String,
        targetAgentName: String,
        result: String,
        failed: Boolean,
        deliveryOrder: Long = nextDelegationEventOrder.incrementAndGet()
    ): Unit = withContext(Dispatchers.Main.immediate) {
        if (taskId <= 0L) return@withContext
        delegationCompletionLocks.getOrPut(conversationId) { Mutex() }.withLock {
        var turn = activeTurns[conversationId]
        // The initial conversation write can still be in flight. Appending to Room here would be
        // overwritten by that stale initial state, so retry through the normal active/idle paths.
        if (turn == null && startingConversations.containsKey(conversationId)) {
            scope.launch {
                while (startingConversations.containsKey(conversationId)) delay(25)
                completeDelegationEvent(conversationId, taskId, title, sourceAgentName, targetAgentName, result, failed, deliveryOrder)
            }
            return@withContext
        }
        // Durable Room locking and queue de-duplication make retries idempotent. Do not drop a
        // retry before it can repair a partially persisted messages/context projection.
        // `activeTurns` is removed in LocalTurn's finally block, after its state has already been
        // marked done. Join that tail before writing an external event, otherwise the old finally
        // persistence can overwrite the event and the continuation starts from stale history.
        if (turn == null || !turn.state.isStreaming) {
            turn?.job?.takeIf { it.isActive }?.join()
            val inserted = agentConversationRepository.appendDelegationCompletion(
                conversationId = conversationId,
                title = title,
                sourceAgentName = sourceAgentName,
                targetAgentName = targetAgentName,
                result = SubagentResult(targetAgentName, result),
                failed = failed,
                taskId = taskId,
                deliveryOrder = deliveryOrder
            )
            if (inserted) {
                lastDelegationCompletionAt[conversationId] = System.currentTimeMillis()
                // Idle completions are persisted directly, so publish the same durable event before
                // starting the hidden continuation. A pending sibling must not hide completed events.
                refreshConversationEvent(conversationId)
                scheduleContinuationAfterConversationEvent(conversationId)
            } else {
                StreamDebugLog.event(conversationId, null, "DELEGATE_DUPLICATE", "task=$taskId")
            }
            return@withContext
        }

        if (agentConversationRepository.hasDelegationCompletion(conversationId, taskId)) {
            deferredDelegationCompletions[taskId] = DeferredDelegationCompletion(result, failed)
            if (completeDelegationTool(turn, taskId, result, failed)) deferredDelegationCompletions.remove(taskId)
            StreamDebugLog.event(conversationId, null, "DELEGATE_DUPLICATE", "task=$taskId")
            return@withLock
        }
        lastDelegationCompletionAt[conversationId] = System.currentTimeMillis()
        StreamDebugLog.event(conversationId, null, "DELEGATE_ACCEPTED", "task=$taskId active=${turn.state.isStreaming} order=$deliveryOrder")
        val event = conversationEventMessage(
            type = ConversationEventType.DELEGATION_COMPLETED,
            label = title,
            state = if (failed) ConversationEventState.FAILED else ConversationEventState.DONE,
            detail = result,
            metadata = mapOf(
                "sourceAgentName" to sourceAgentName,
                "targetAgentName" to targetAgentName,
                "delegationTaskId" to taskId.toString(),
                "deliveryOrder" to deliveryOrder.toString()
            )
        )
        // Do not render or persist this event yet. The provider must receive it first at its next
        // safe request boundary; the same injection callback then commits the work-card event.
        queueConversationEvent(conversationId, event)
        deferredDelegationCompletions[taskId] = DeferredDelegationCompletion(result, failed)
        if (completeDelegationTool(turn, taskId, result, failed)) deferredDelegationCompletions.remove(taskId)
        // The event stays hidden until provider injection, but the tool terminal state must survive
        // a process death in the gap between child completion and the next provider boundary.
        persistTurn(turn)
        publishTurn(turn, turn.lastStatus, turn.lastDetail, urgent = true)
        // The owning turn's finally block schedules the continuation after every deferred
        // ToolCallResult has been projected. Scheduling here races the remaining tool results.
        }
    }

    internal fun queueConversationEvent(conversationId: Long, event: UiMessage) {
        val taskId = event.metadata["delegationTaskId"]
        val queue = queuedConversationEvents.getOrPut(conversationId) { java.util.concurrent.ConcurrentLinkedQueue() }
        synchronized(queue) {
            val key = taskId?.takeIf(String::isNotBlank)?.let { "$conversationId:$it" }
            if (key == null || (key !in injectingConversationEventTasks && queue.none { it.metadata["delegationTaskId"] == taskId })) {
                queue.add(event)
            }
        }
    }

    internal fun drainQueuedConversationEvents(conversationId: Long): List<UiMessage> = buildList {
        val queue = queuedConversationEvents[conversationId] ?: return@buildList
        synchronized(queue) {
            while (true) {
                val event = queue.poll() ?: break
                event.metadata["delegationTaskId"]?.let { injectingConversationEventTasks.add("$conversationId:$it") }
                add(event)
            }
            if (queue.isEmpty()) queuedConversationEvents.remove(conversationId, queue)
        }
        sortWith(compareBy { it.metadata["deliveryOrder"]?.toLongOrNull() ?: Long.MAX_VALUE })
    }

    internal fun acknowledgeConversationEvents(conversationId: Long, events: List<UiMessage>) {
        events.forEach { event ->
            event.metadata["delegationTaskId"]?.let { taskId ->
                injectingConversationEventTasks.remove("$conversationId:$taskId")
                expectedDelegationTasks[conversationId]?.remove(taskId.toLongOrNull())
            }
        }
    }

    private suspend fun awaitDelegationBatch(conversationId: Long) {
        val expected = expectedDelegationTasks[conversationId]?.toSet().orEmpty()
        if (expected.isEmpty()) return
        withTimeoutOrNull(120_000L) {
            while (true) {
                val entity = conversationDao.getConversationById(conversationId)
                val messages = entity?.messagesJson?.let { parseMessagesFromJson(it).getOrNull() }.orEmpty()
                val context = entity?.contextMessagesJson?.let { parseMessagesFromJson(it).getOrNull() }.orEmpty()
                val persistedEvents = (messages + context).mapNotNull { it.conversationEvent() }
                val persistedIds = (messages + context).flatMap { message ->
                    listOfNotNull(message.metadata["delegationTaskId"])
                }.mapNotNull(String::toLongOrNull).toSet()
                val queuedIds = queuedConversationEvents[conversationId].orEmpty()
                    .mapNotNull { it.metadata["delegationTaskId"]?.toLongOrNull() }.toSet()
                val delivered = persistedIds + queuedIds
                if (expected.all { it in delivered }) return@withTimeoutOrNull
                delay(100)
            }
        }
    }

    internal fun hasQueuedConversationEvents(conversationId: Long): Boolean =
        queuedConversationEvents[conversationId]?.isNotEmpty() == true

    internal fun scheduleContinuationAfterConversationEvent(conversationId: Long) {
        if (!continuationRequested.add(conversationId)) return
        val job = continuationJobs.computeIfAbsent(conversationId) {
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    // A completion can win the gap between persistence and active-turn registration.
                    // Wait for that registration/turn to settle instead of launching a second turn.
                    while (activeTurns.containsKey(conversationId) || startingConversations.containsKey(conversationId)) {
                        delay(25)
                    }
                    // Let sibling completions in the same terminal window settle before taking
                    // the durable snapshot. New completions move this quiet point forward.
                    awaitDelegationBatch(conversationId)
                    continueAfterConversationEvent(conversationId)
                    // startTurn returns after registration, not after the provider turn finishes.
                    // Keep the coalescing gate closed for the whole hidden continuation.
                    while (activeTurns.containsKey(conversationId) || startingConversations.containsKey(conversationId)) {
                        delay(25)
                    }
                } finally {
                    continuationJobs.remove(conversationId)
                    continuationRequested.remove(conversationId)
                    if (hasQueuedConversationEvents(conversationId)) scheduleContinuationAfterConversationEvent(conversationId)
                }
            }
        }
        job.start()
    }

    internal suspend fun continueAfterConversationEvent(conversationId: Long) {
        val entity = conversationDao.getConversationById(conversationId) ?: return
        val messages = parseMessagesFromJson(entity.messagesJson).getOrNull() ?: return
        val contextMessages = parseMessagesFromJson(entity.contextMessagesJson.ifBlank { entity.messagesJson }).getOrNull() ?: return
        val settings = settingsManager.getSettings()
        val modelKey = entity.agentId?.let { agentDao.getById(it)?.defaultModelKeysJson }
            ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?.let { values -> (0 until values.length()).map(values::optString).firstOrNull(String::isNotBlank) }
            ?: settings.activeSelection?.key.orEmpty()
        val parts = modelKey.split('|', limit = 3)
        val state = ChatUiState(
            messages = messages,
            contextMessages = contextMessages,
            selectedModel = parts.getOrNull(2).orEmpty().ifBlank { settings.activeSelection?.modelId.orEmpty() },
            workspacePath = entity.workspacePath,
            assistantMode = runCatching { AssistantMode.valueOf(entity.assistantMode) }.getOrDefault(AssistantMode.CHAT),
            ownerId = entity.ownerId,
            agentId = entity.agentId,
            modelOptions = _uiState.value.modelOptions,
            activeModelKey = modelKey,
            conversationId = conversationId.toString(),
            effort = if (parts.size == 3) settingsManager.getThinkingEffort(parts[1], parts[2]) else _uiState.value.effort,
            sessionMode = IntelligenceSessionManager.SessionMode.LOCAL
        )
        startTurn(
            content = "A background delegation completed. Read the latest system event and continue the task with that result. Do not repeat the event marker.",
            images = emptyList(),
            initialState = state,
            projectVisible = currentConversationId == conversationId,
            internalContinuation = true
        )
    }

    suspend fun refreshConversationEvent(conversationId: Long) {
        if (currentConversationId != conversationId) return
        activeTurns[conversationId]?.let { turn ->
            val entity = conversationDao.getConversationById(conversationId) ?: return
            val persistedMessages = parseMessagesFromJson(entity.messagesJson, includeModelState = false).getOrNull() ?: return
            val persistedContext = parseMessagesFromJson(entity.contextMessagesJson.ifBlank { entity.messagesJson }).getOrNull() ?: return
            val visibleEvents = persistedMessages.filter { it.conversationEvent() != null }
            val contextEvents = persistedContext.filter { it.conversationEvent() != null }
            turn.state = turn.state.copy(
                messages = mergeConversationEvents(turn.state.messages, visibleEvents),
                contextMessages = mergeConversationEvents(turn.state.contextMessages, contextEvents)
            )
            publishTurn(turn, turn.lastStatus, turn.lastDetail, urgent = true)
            return
        }
        val entity = conversationDao.getConversationById(conversationId) ?: return
        val messages = parseMessagesFromJson(entity.messagesJson, includeModelState = false).getOrNull() ?: return
        val contextMessages = parseMessagesFromJson(entity.contextMessagesJson.ifBlank { entity.messagesJson }).getOrNull() ?: return
        if (currentConversationId == conversationId) {
            _uiState.update { state ->
                state.copy(messages = messages, contextMessages = contextMessages)
            }
        }
    }

    override fun sendMessageWithImage(content: String, imageBase64: String, mimeType: String, fileName: String) {
        if (!mimeType.startsWith("image/") || imageBase64.isBlank() || imageBase64.length > 1_000_000) {
            _uiState.update { it.copy(error = "Invalid or oversized image attachment") }
            return
        }
        sendMessageInternal(
            content,
            listOf(com.amaya.intelligence.data.remote.api.ChatImage(imageBase64, mimeType, fileName))
        )
    }

    private fun sendMessageInternal(
        content: String,
        images: List<com.amaya.intelligence.data.remote.api.ChatImage>
    ) {
        val currentState = _uiState.value
        scope.launch {
            startTurn(content, images, currentState, projectVisible = true)
        }
    }

    override fun editMessage(messageId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank()) return
        val currentState = _uiState.value
        if (currentState.isStreaming) {
            stopGeneration()
        }
        val targetIdx = currentState.messages.indexOfFirst { it.id == messageId }
        if (targetIdx < 0) return
        val targetMsg = currentState.messages[targetIdx]
        if (targetMsg.role != MessageRole.USER) return

        val updatedUserMsg = targetMsg.copy(
            content = trimmed,
            metadata = targetMsg.metadata + mapOf(
                "isEdited" to "true",
                "editedAt" to System.currentTimeMillis().toString()
            )
        )
        val truncatedMessages = currentState.messages.take(targetIdx) + updatedUserMsg
        val contextIdx = currentState.contextMessages.indexOfFirst { it.id == messageId }
        val truncatedContext = if (contextIdx >= 0) {
            currentState.contextMessages.take(contextIdx) + updatedUserMsg
        } else {
            truncatedMessages
        }
        val updatedState = currentState.copy(
            messages = truncatedMessages,
            contextMessages = truncatedContext,
            error = null,
            isLoading = true,
            isStreaming = true
        )
        _uiState.value = updatedState
        saveCurrentConversation()
        scope.launch {
            startTurn(
                content = trimmed,
                images = updatedUserMsg.attachments.map { com.amaya.intelligence.data.remote.api.ChatImage(it.dataBase64, it.mimeType, it.fileName) },
                initialState = updatedState,
                projectVisible = true,
                preexistingUserMessage = true
            )
        }
    }

    override fun resendMessage(messageId: String) {
        val currentState = _uiState.value
        if (currentState.isStreaming) return
        val targetIdx = currentState.messages.indexOfFirst { it.id == messageId }
        if (targetIdx < 0) return
        val targetMsg = currentState.messages[targetIdx]
        if (targetMsg.role != MessageRole.USER) return

        val truncatedMessages = currentState.messages.take(targetIdx + 1)
        val contextIdx = currentState.contextMessages.indexOfFirst { it.id == messageId }
        val truncatedContext = if (contextIdx >= 0) {
            currentState.contextMessages.take(contextIdx + 1)
        } else {
            truncatedMessages
        }
        val updatedState = currentState.copy(
            messages = truncatedMessages,
            contextMessages = truncatedContext,
            error = null,
            isLoading = true,
            isStreaming = true
        )
        _uiState.value = updatedState
        saveCurrentConversation()
        scope.launch {
            startTurn(
                content = targetMsg.content,
                images = targetMsg.attachments.map { com.amaya.intelligence.data.remote.api.ChatImage(it.dataBase64, it.mimeType, it.fileName) },
                initialState = updatedState,
                projectVisible = true,
                preexistingUserMessage = true
            )
        }
    }

    override fun stopGeneration() {
        currentConversationId?.let { conversationId ->
            activeTurns[conversationId]?.let { turn ->
                stoppingConversations.add(conversationId)
                pendingToolConfirmations.cancel(turn.turnId)
                pendingClarifications.cancel(turn.turnId)
                turn.job?.cancel()
                return
            }
            startingConversations.remove(conversationId)
        } ?: startingNewTurnId.set(0L)
        chatJob?.cancel()
        chatJob = null
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        thinkingFlushJob?.cancel()
        thinkingFlushJob = null
        pendingToolConfirmations.cancelAll()
        pendingConfirmationUi.clear()
        pendingApprovalIds.clear()
        pendingClarifications.cancelAll()
        pendingClarificationUi.clear()
        pendingClarificationIds.clear()
        _pendingClarification.value = null
        flushAssistantThinkingBuffer()
        flushAssistantTextBuffer()
        markActiveToolsStopped()
        markCurrentAssistantTerminal("cancelled")
        browserSessionManager.cancelFromUser()
        browserSessionManager.onAssistantStreamingChanged(false)
        _uiState.update { it.copy(isLoading = false, isStreaming = false, isAutoCompacting = false) }
        saveCurrentConversation()
    }

    override fun clearConversation() {
        targetEpoch.incrementAndGet()
        resetVisibleConversation()
    }

    private fun resetVisibleConversation() {
        chatJob = null
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        thinkingFlushJob?.cancel()
        thinkingFlushJob = null
        currentConversationId = null
        currentAssistantMessageId = null
        assistantTextBuffer.clear()
        assistantThinkingBuffer.clear()
        lastAssistantTextUiEmitAt = 0L
        browserSessionManager.clearSessionState()
        _uiState.update { it.copy(
            conversationId = null,
            messages = emptyList(),
            contextMessages = emptyList(),
            error = null,
            isLoading = false,
            isLoadingHistory = false,
            isStreaming = false,
            isAutoCompacting = false,
            isCompressing = false,
            totalInputTokens = 0,
            totalOutputTokens = 0
        )}
    }

    override fun loadConversation(id: String) {
        val longId = id.toLongOrNull() ?: return
        val epoch = targetEpoch.incrementAndGet()
        currentConversationId = longId
        activeTurns[longId]?.let { running ->
            if (running.state.assistantMode == AssistantMode.AGENT) {
                browserSessionManager.selectConversation("conversation:$longId", running.state.agentId)
                browserSessionManager.onAssistantStreamingChanged(true)
            }
            _uiState.value = running.state.copy(isLoading = true, isStreaming = true)
            return
        }
        _uiState.update { state ->
            state.copy(
                conversationId = longId.toString(),
                messages = emptyList(),
                isLoading = false,
                isLoadingHistory = true,
                isStreaming = false,
                isAutoCompacting = false,
                error = null,
                totalInputTokens = 0,
                totalOutputTokens = 0
            )
        }
        scope.launch {
            val entity = conversationDao.getConversationById(longId)
            if (targetEpoch.get() != epoch || currentConversationId != longId) return@launch
            activeTurns[longId]?.let { running ->
                if (running.state.assistantMode == AssistantMode.AGENT) {
                    browserSessionManager.selectConversation("conversation:$longId", running.state.agentId)
                    browserSessionManager.onAssistantStreamingChanged(true)
                }
                _uiState.value = running.state.copy(isLoading = true, isStreaming = true)
                return@launch
            }
            entity?.let { conv ->
                val parsed = parseMessagesFromJson(conv.messagesJson, includeModelState = false)
                val messages = parsed.getOrElse {
                    _uiState.update { state -> state.copy(error = "Conversation data is corrupted and could not be loaded") }
                    return@launch
                }
                val contextMessages = parseMessagesFromJson(conv.contextMessagesJson.ifBlank { conv.messagesJson })
                    .getOrElse {
                        _uiState.update { state -> state.copy(error = "Conversation context is corrupted and could not be loaded") }
                        return@launch
                    }
                currentConversationId = conv.id
                currentAssistantMessageId = null
                assistantTextBuffer.clear()
                assistantThinkingBuffer.clear()
                lastAssistantTextUiEmitAt = 0L
                browserSessionManager.selectConversation("conversation:${conv.id}", conv.agentId)
                _uiState.update { it.copy(
                    conversationId = conv.id.toString(),
                    workspacePath = conv.workspacePath,
                        assistantMode = runCatching { AssistantMode.valueOf(conv.assistantMode) }.getOrDefault(AssistantMode.forWorkspace(conv.workspacePath)),
                    ownerId = conv.ownerId,
                    agentId = conv.agentId,
                    messages = messages,
                    contextMessages = contextMessages,
                    totalInputTokens = 0,
                    totalOutputTokens = 0,
                    error = null,
                    isLoading = false,
                    isLoadingHistory = false,
                    isStreaming = false,
                    isAutoCompacting = false
                )}
            } ?: _uiState.update { it.copy(isLoading = false, isLoadingHistory = false, isStreaming = false, error = "Conversation not found") }
        }
    }

    override fun deleteConversation(id: String) {
        val longId = id.toLongOrNull() ?: return
        titleJobs.remove(longId)?.cancel()
        if (currentConversationId == longId) clearConversation()
        // The compaction ledger describes a conversation that is about to stop existing.
        ledgerStore.invalidate(id)
        scope.launch {
            conversationDao.deleteConversationById(longId)
            runCatching { sessionMemoryRepository.deleteSession(id) }
        }
    }

    override fun clearVisibleHistory(deleteContext: Boolean) {
        val conversationId = currentConversationId ?: return
        if (activeTurns.containsKey(conversationId)) {
            _uiState.update { it.copy(error = "Wait for the current response before clearing this chat") }
            return
        }
        val updatedContext = contextAfterHistoryClear(_uiState.value.contextMessages, deleteContext)
        // A cached ledger describes context that no longer exists.
        if (deleteContext) ledgerStore.invalidate(conversationId.toString())
        _uiState.update { it.copy(messages = emptyList(), contextMessages = updatedContext) }
        scope.launch {
            try {
                conversationSaveMutex.withLock {
                    conversationDao.clearConversationHistory(
                        id = conversationId,
                        contextMessagesJson = serializeMessagesToJson(updatedContext)
                    )
                }
                if (deleteContext) sessionMemoryRepository.deleteSession(conversationId.toString())
            } catch (error: Exception) {
                _uiState.update { it.copy(error = "Could not clear this chat: ${error.message.orEmpty()}") }
            }
        }
    }

    override fun compactConversation(focus: String) {
        val conversationId = currentConversationId ?: run {
            _uiState.update { it.copy(error = "Nothing to compress") }
            return
        }
        if (activeTurns.containsKey(conversationId) || compactJob?.isActive == true) {
            _uiState.update { it.copy(error = "Wait for the current operation before compressing") }
            return
        }
        val state = _uiState.value
        _uiState.update { it.copy(isCompressing = true, error = null) }
        compactJob = scope.launch {
            try {
                val history = state.contextMessages.flatMap { it.toChatMessages() }
                val summary = aiRepository.compressConversation(
                    conversationHistory = history,
                    selectedModel = state.selectedModel,
                    connectionId = state.activeModelKey.takeIf { it.startsWith("model|") }?.split('|', limit = 3)?.getOrNull(1),
                    focus = focus
                ).getOrThrow()
                val context = compressedSessionContext(summary)
                val compactionEvent = conversationEventMessage(
                    type = ConversationEventType.COMPACTION,
                    label = "Compacted",
                    detail = "Manual summary created${focus.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}"
                )
                val messages = state.messages + compactionEvent
                val visibleContext = context + compactionEvent
                // The manual summary supersedes whatever the automatic ledger had accumulated.
                ledgerStore.invalidate(conversationId.toString())
                conversationSaveMutex.withLock {
                    conversationDao.updateConversationCompaction(
                        id = conversationId,
                        messagesJson = serializeMessagesToJson(messages),
                        contextMessagesJson = serializeMessagesToJson(visibleContext)
                    )
                }
                if (currentConversationId == conversationId) {
                    _uiState.update { it.copy(messages = messages, contextMessages = visibleContext) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "Could not compress conversation") }
            } finally {
                _uiState.update { it.copy(isCompressing = false) }
                compactJob = null
            }
        }
    }

    override fun cancelCompactConversation() {
        compactJob?.cancel()
    }

    override fun selectModel(modelKey: String) {
        if (_uiState.value.isStreaming) stopGeneration()
        scope.launch {
            val parts = modelKey.split('|', limit = 3)
            if (parts.size != 3 || parts[0] != "model") return@launch
            settingsManager.setActiveModel(
                com.amaya.intelligence.data.remote.api.ActiveModelSelection(
                    connectionId = parts[1],
                    modelId = parts[2]
                )
            )
            // Optimistically load persisted effort for the newly selected model.
            val loaded = settingsManager.getThinkingEffort(parts[1], parts[2])
            _uiState.update { it.copy(effort = loaded) }
        }
    }

    private fun applyModelToUi(modelKey: String) {
        val parts = modelKey.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != "model") return
        val settings = settingsManager.getSettings()
        val model = settings.connections.firstOrNull { it.id == parts[1] }?.visibleModels?.firstOrNull { it.id == parts[2] } ?: return
        _uiState.update {
            it.copy(
                activeModelKey = modelKey,
                selectedModel = model.id,
                effort = settingsManager.getThinkingEffort(parts[1], parts[2])
            )
        }
    }

    override fun setEffort(effort: com.amaya.intelligence.data.remote.api.ThinkingEffort) {
        _uiState.update { it.copy(effort = effort) }
        // Persist per-model so it survives restart and model switches.
        val selection = settingsManager.getSettings().activeSelection ?: return
        scope.launch {
            settingsManager.setThinkingEffort(selection.connectionId, selection.modelId, effort)
        }
    }

    override fun setWorkspace(path: String?) {
        // Workspace browsing must never change Chat/Project/Agent ownership.
        browserSessionManager.setWorkspace(path)
        _uiState.update { state ->
            state.copy(workspacePath = path?.takeIf(String::isNotBlank) ?: state.workspacePath)
        }
    }

    override fun setAssistantOwner(mode: AssistantMode, ownerId: String?, workspacePath: String?, agentId: Long?) {
        val epoch = targetEpoch.incrementAndGet()
        resetVisibleConversation()
        _uiState.update {
            it.copy(
                assistantMode = mode,
                ownerId = ownerId,
                agentId = if (mode == AssistantMode.AGENT) agentId else null,
                workspacePath = if (mode == AssistantMode.CHAT) null else workspacePath
            )
        }
        browserSessionManager.setWorkspace(if (mode == AssistantMode.CHAT) null else workspacePath)
        if (mode == AssistantMode.AGENT && agentId != null) {
            val targetOwnerId = ownerId
            scope.launch {
                val active = agentDao.getById(agentId)?.defaultModelKeysJson.orEmpty()
                    .let { runCatching { org.json.JSONArray(it) }.getOrNull() }
                    ?.let { array -> (0 until array.length()).map { array.optString(it) }.firstOrNull() }
                val selected = active ?: settingsManager.getSettings().activeSelection?.key
                val conversation = conversationDao.getAgentConversation(agentId)
                val state = _uiState.value
                if (targetEpoch.get() != epoch || state.assistantMode != AssistantMode.AGENT || state.agentId != agentId || state.ownerId != targetOwnerId) return@launch
                if (selected != null) applyModelToUi(selected)
                conversation?.let { loadConversation(it.id.toString()) }
            }
        }
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
        val turnId = executionId.substringBefore(':', "").toLongOrNull() ?: return
        val toolCallId = executionId.substringAfter(':', executionId)
        pendingToolConfirmations.resolve(executionId, turnId, confirmed) {
            pendingConfirmationUi.remove(toolCallId)
            pendingApprovalIds.remove(toolCallId, executionId)
            turnsById[turnId]?.let { turn ->
                updateTurnToolExecution(turn, toolCallId) { tool ->
                    tool.copy(
                        status = if (confirmed) ToolStatus.RUNNING else ToolStatus.ERROR,
                        result = if (confirmed) tool.result else "User declined: ${tool.metadata["approvalReason"].orEmpty()}",
                        metadata = tool.metadata + mapOf(
                            "approvalRequired" to "false",
                            "approvalState" to if (confirmed) "accepted" else "declined"
                        )
                    )
                }
            }
        }
    }

    override fun regenerateLastResponse() {
        val conversationId = currentConversationId ?: return
        if (activeTurns.containsKey(conversationId)) {
            _uiState.update { it.copy(error = "Wait for the current response before regenerating") }
            return
        }
        val state = _uiState.value
        val lastUserIdx = state.messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIdx < 0) return
        val lastUser = state.messages[lastUserIdx]
        if (lastUser.content.isBlank() && lastUser.attachments.isEmpty()) return
        // Drop everything after the last user prompt (the trailing assistant turn) and re-run
        // that prompt. The trimmed state is persisted by the turn machinery (preexistingUserMessage
        // means startTurn does not append a duplicate user bubble).
        val trimmedMessages = state.messages.take(lastUserIdx + 1)
        val trimmedContext = state.contextMessages.take(lastUserIdx + 1)
        val regeneratedState = state.copy(
            messages = trimmedMessages,
            contextMessages = trimmedContext,
            isLoading = false,
            isStreaming = false,
            error = null
        )
        _uiState.value = regeneratedState
        scope.launch {
            startTurn(
                content = lastUser.content,
                images = lastUser.attachments.map { com.amaya.intelligence.data.remote.api.ChatImage(it.dataBase64, it.mimeType, it.fileName) },
                initialState = regeneratedState,
                projectVisible = currentConversationId == conversationId,
                preexistingUserMessage = true
            )
        }
    }

    override fun respondToClarification(executionId: String, answer: String?) {
        // Both id forms are accepted: the full "$turnId:$toolCallId" id from the inline tool-card
        // metadata, or a bare toolCallId (used by the pendingClarification dialog).
        val toolCallId = executionId.substringAfter(':', executionId)
        val fullId = if (':' in executionId) executionId else pendingClarificationIds[toolCallId]
        val turnId = fullId?.substringBefore(':')?.toLongOrNull() ?: return
        pendingClarifications.resolve(fullId, turnId, answer) {
            pendingClarificationUi.remove(toolCallId)
            pendingClarificationIds.remove(toolCallId, fullId)
            _pendingClarification.value = null
            turnsById[turnId]?.let { turn ->
                val dismissed = answer.isNullOrBlank()
                updateTurnToolExecution(turn, toolCallId) { tool ->
                    tool.copy(
                        status = if (dismissed) ToolStatus.ERROR else ToolStatus.RUNNING,
                        result = if (dismissed) "User dismissed the question" else "User answered: $answer",
                        metadata = tool.metadata + mapOf(
                            "clarificationState" to if (dismissed) "dismissed" else "answered",
                            "clarificationAnswer" to answer.orEmpty()
                        )
                    )
                }
            }
        }
    }

    internal fun mergeConversationEvents(current: List<UiMessage>, persisted: List<UiMessage>): List<UiMessage> {
        val known = current.mapNotNull { it.conversationEvent()?.let { _ -> it.metadata["completedAt"] ?: it.id } }.toSet()
        return current + persisted.filter { message ->
            val event = message.conversationEvent() ?: return@filter false
            (message.metadata["completedAt"] ?: message.id) !in known
        }
    }

    internal fun updateTurnToolExecution(turn: LocalTurn, toolCallId: String, transform: (ToolExecution) -> ToolExecution) {
        updateTurnMessage(turn) { message ->
            message.copy(
                toolExecutions = message.toolExecutions.map { if (it.toolCallId == toolCallId) transform(it) else it },
                steps = message.steps.map { if (it is MessageStep.ToolCall && it.execution.toolCallId == toolCallId) it.copy(execution = transform(it.execution)) else it }
            )
        }
        val execution = turn.state.messages.asReversed().asSequence()
            .flatMap { it.toolExecutions.asReversed().asSequence() }
            .firstOrNull { it.toolCallId == toolCallId }
        val pending = execution?.metadata?.get("approvalState") == "pending"
        publishTurn(
            turn,
            if (pending) "Approval required" else turn.lastStatus,
            if (pending) execution?.let { "Tools: ${LocalToolMapper.displayLabel(it.name, it.arguments)}" } ?: "Waiting for your decision" else turn.lastDetail,
            urgent = true
        )
    }

    private fun saveCurrentConversation() {
        val conversationId = currentConversationId ?: return
        val messages = _uiState.value.messages
        val contextMessages = _uiState.value.contextMessages
        val messagesJson = serializeMessagesToJson(messages)
        val contextMessagesJson = serializeMessagesToJson(contextMessages)
        scope.launch {
            conversationSaveMutex.withLock {
                try {
                    if (currentConversationId == conversationId &&
                        serializeMessagesToJson(_uiState.value.messages) != messagesJson
                    ) return@withLock
                    val existing = conversationDao.getConversationById(conversationId) ?: return@withLock
                    conversationDao.updateConversation(
                        existing.copy(
                            messagesJson = messagesJson,
                            contextMessagesJson = contextMessagesJson,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (error: Exception) {
                    if (currentConversationId == conversationId) {
                        _uiState.update { it.copy(error = "Could not save this conversation: ${error.message.orEmpty()}") }
                    }
                }
            }
        }
    }

    internal fun launchTitleGeneration(userMessage: String, conversationId: Long?) {
        if (conversationId == null) return
        val settings = settingsManager.getSettings()
        val selection = settings.activeSelection ?: return
        val connection = settings.connections.firstOrNull { it.id == selection.connectionId } ?: return
        val selectedModel = _uiState.value.selectedModel.ifBlank { selection.modelId }
        titleJobs.remove(conversationId)?.cancel()
        titleJobs[conversationId] = scope.launch {
            try {
                val title = aiRepository.generateTitle(userMessage, connection, selectedModel)
                val conversation = conversationDao.getConversationById(conversationId) ?: return@launch
                conversationDao.updateTitle(conversationId, title)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Title generation failure is non-fatal.
            } finally {
                titleJobs.remove(conversationId, coroutineContext[Job])
            }
        }
    }


    private suspend fun persistCurrentConversation(): Long? = conversationSaveMutex.withLock {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return@withLock currentConversationId
        val hasContent = messages.any { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() } ||
            messages.any { it.role == MessageRole.USER && it.content.isNotBlank() }
        if (!hasContent) return@withLock currentConversationId

        return@withLock try {
            val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: "New Conversation"
            val title = firstUserMsg.split("\\s+".toRegex()).take(5).joinToString(" ").take(50)
            val now = System.currentTimeMillis()
            val messagesJson = serializeMessagesToJson(messages)

            val existingId = currentConversationId
            if (existingId != null) {
                val existing = conversationDao.getConversationById(existingId)
                if (existing != null) {
                    conversationDao.updateConversation(
                        existing.copy(
                            workspacePath = _uiState.value.workspacePath,
                            assistantMode = _uiState.value.assistantMode.name,
                            ownerId = _uiState.value.ownerId,
                            agentId = _uiState.value.agentId,
                            messagesJson = messagesJson,
                            contextMessagesJson = serializeMessagesToJson(_uiState.value.contextMessages),
                            updatedAt = now
                        )
                    )
                    existingId
                } else {
                    currentConversationId = null
                    insertConversationLocked(title, messagesJson, now)
                }
            } else {
                insertConversationLocked(title, messagesJson, now)
            }
        } catch (error: Exception) {
            _uiState.update { it.copy(error = "Could not save this conversation: ${error.message.orEmpty()}") }
            null
        }
    }

    private suspend fun insertConversationLocked(title: String, messagesJson: String, now: Long): Long {
        val agentId = _uiState.value.agentId.takeIf { _uiState.value.assistantMode == AssistantMode.AGENT }
        if (agentId != null) {
            conversationDao.getAgentConversation(agentId)?.let { existing ->
                conversationDao.updateConversation(
                    existing.copy(
                        title = title,
                        workspacePath = _uiState.value.workspacePath,
                        ownerId = _uiState.value.ownerId,
                        messagesJson = messagesJson,
                        contextMessagesJson = serializeMessagesToJson(_uiState.value.contextMessages),
                        updatedAt = now
                    )
                )
                currentConversationId = existing.id
                _uiState.update { it.copy(conversationId = existing.id.toString()) }
                return existing.id
            }
        }
        val newId = conversationDao.insertConversation(
            ConversationEntity(
                id = 0,
                title = title,
                workspacePath = _uiState.value.workspacePath,
                assistantMode = _uiState.value.assistantMode.name,
                ownerId = _uiState.value.ownerId,
                agentId = _uiState.value.agentId,
                messagesJson = messagesJson,
                contextMessagesJson = serializeMessagesToJson(_uiState.value.contextMessages),
                createdAt = now,
                updatedAt = now
            )
        )
        currentConversationId = newId
        _uiState.update { it.copy(conversationId = newId.toString()) }
        return newId
    }


}
