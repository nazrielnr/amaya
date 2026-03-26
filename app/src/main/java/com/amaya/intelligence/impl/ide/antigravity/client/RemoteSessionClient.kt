package com.amaya.intelligence.impl.ide.antigravity.client

import android.content.Context
import com.amaya.intelligence.domain.models.MessageAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.ArrayDeque
import com.amaya.intelligence.domain.models.ConnectionState

/**
 * WebSocket client that connects to Antigravity IDE extension server.
 * Receives chat state, streaming text, tool calls from Antigravity.
 * Sends user commands (send message, new chat, etc.) to Antigravity.
 *
 * This is a pure remote display client — no AI processing happens here.
 * All AI configuration, model selection, and processing is done by Antigravity.
 */
@Singleton
class RemoteSessionClient @Inject constructor(
    @ApplicationContext context: Context
) {
    private var wsClient: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var lastIp: String? = null
    private var lastPort: Int? = null
    private var isManualDisconnect = false
    private var reconnectJob: Job? = null
    private var postConnectRefreshJob: Job? = null
    @Volatile private var reconnecting = false
    private var lastSeqId = -1
    private var lastServerSessionId: String? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY

    private val localStreamingHint: MutableMap<String, Boolean> = mutableMapOf()
    private val pendingCommands: ArrayDeque<String> = ArrayDeque()
    private val maxPendingCommands = 64
    private var lastForegroundUpdateAt = 0L
    private val minForegroundUpdateInterval = 1200L // Rate limit updates to ~1Hz

    companion object {
        private const val PREFS_NAME = "amaya_remote_session"
        private const val KEY_LAST_IP = "last_ip"
        private const val KEY_LAST_PORT = "last_port"
        private const val KEY_LAST_SEQ_ID = "last_seq_id"
        private const val KEY_LAST_SERVER_SESSION_ID = "last_server_session_id"
        private const val INITIAL_RECONNECT_DELAY = 1000L
    }

    init {
        lastIp = prefs?.getString(KEY_LAST_IP, null)
        lastPort = prefs?.getInt(KEY_LAST_PORT, 0)?.takeIf { it > 0 }
        lastSeqId = prefs?.getInt(KEY_LAST_SEQ_ID, -1) ?: -1
        lastServerSessionId = prefs?.getString(KEY_LAST_SERVER_SESSION_ID, null)
    }

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Incoming events from Antigravity
    private val _events = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<RemoteEvent> = _events.asSharedFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Server info
    private val _serverInfo = MutableStateFlow<String?>(null)
    val serverInfo: StateFlow<String?> = _serverInfo.asStateFlow()

    @Synchronized
    fun connect(ip: String, port: Int, isReconnect: Boolean = false) {
        if (!isReconnect) {
            isManualDisconnect = false
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectDelay = INITIAL_RECONNECT_DELAY
        }
        
        // If we're already connecting/connected to THIS ip/port, don't restart unless forced.
        if (lastIp == ip && lastPort == port && _connectionState.value != ConnectionState.DISCONNECTED) {
            android.util.Log.v("RemoteSessionClient", "Already connecting/connected to $ip:$port, skipping redundant connect")
            return
        }

        lastIp = ip
        lastPort = port
        prefs?.edit()?.putString(KEY_LAST_IP, ip)?.putInt(KEY_LAST_PORT, port)?.apply()

        android.util.Log.i("RemoteSessionClient", "Connecting to $ip:$port (isReconnect=$isReconnect)")
        
        postConnectRefreshJob?.cancel()
        postConnectRefreshJob = null
        
        disconnectInternal()

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null

        val uri = URI("ws://$ip:$port")
        val newClient = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                if (this@RemoteSessionClient.wsClient != this) return
                android.util.Log.i("RemoteSessionClient", "WebSocket onOpen: $ip:$port")
                
                _connectionState.value = ConnectionState.CONNECTED
                _serverInfo.value = "$ip:$port"
                _errorMessage.value = null
                runCatching { RemoteSessionForegroundService.start(appContext) }
                runCatching { RemoteSessionForegroundService.updateStatus(appContext, RemoteSessionForegroundService.STATE_CONNECTED) }
                reconnecting = false
                reconnectDelay = INITIAL_RECONNECT_DELAY
                
                flushPendingCommands()
                forceResync(resetSequence = true)
            }

            override fun onMessage(message: String?) {
                message ?: return
                this@RemoteSessionClient.onMessage(message)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                if (this@RemoteSessionClient.wsClient != this) {
                    android.util.Log.v("RemoteSessionClient", "Stale WebSocket onClose ignored")
                    return
                }
                android.util.Log.w("RemoteSessionClient", "WebSocket onClose: code=$code, reason=$reason, remote=$remote, manual=$isManualDisconnect")

                _connectionState.value = ConnectionState.DISCONNECTED
                runCatching { RemoteSessionForegroundService.stop(appContext) }
                if (!isManualDisconnect) {
                    val error = "Connection lost: ${reason ?: "unknown"} ($code)"
                    _errorMessage.value = "$error. Reconnecting..."
                    attemptReconnect()
                }
            }

            override fun onError(ex: Exception?) {
                if (this@RemoteSessionClient.wsClient != this) return
                android.util.Log.e("RemoteSessionClient", "WebSocket onError: ${ex?.message}")
                _errorMessage.value = "Connection error: ${ex?.message ?: "unknown"}"
            }
        }

        wsClient = newClient
        newClient.connectionLostTimeout = 10
        newClient.connect()
    }

    private fun onMessage(message: String) {
        runCatching {
            val json = JSONObject(message)
            val type = json.optString("event").takeIf { it.isNotBlank() } 
                ?: json.optString("type").takeIf { it.isNotBlank() } 
                ?: return@runCatching
            
            android.util.Log.v("RemoteSessionClient", "Received message: $type (seq=${json.optInt("seqId", -1)})")

            // Update session ID if present (check both serverSessionId and sessionId)
            val sid = json.optNullableString("serverSessionId") ?: json.optNullableString("sessionId")
            sid?.let { newSid ->
                if (newSid != lastServerSessionId) {
                    android.util.Log.i(
                        "RemoteSessionClient",
                        "Server session changed ($lastServerSessionId -> $newSid). Resetting seq cursor."
                    )
                    lastServerSessionId = newSid
                    prefs?.edit()?.putString(KEY_LAST_SERVER_SESSION_ID, newSid)?.apply()
                    lastSeqId = -1 // Reset sequence on new session
                    prefs?.edit()?.putInt(KEY_LAST_SEQ_ID, -1)?.apply()
                }
            }

            val seqId = json.optInt("seqId", -1)
            if (shouldSkipEvent(seqId, sid ?: lastServerSessionId)) return@runCatching
            if (seqId > lastSeqId) {
                lastSeqId = seqId
                prefs?.edit()?.putInt(KEY_LAST_SEQ_ID, lastSeqId)?.apply()
            }

            // Handle Heartbeat
            if (type == "ping") {
                sendRawMessage(JSONObject().put("action", "pong").toString(), queueIfDisconnected = false)
                return@runCatching
            }

            val event = parseEvent(json)
            if (event != null) {
                updateForegroundStatus(event)
                val cid = event.conversationId
                if (cid != null) {
                    // Update streaming hints based on event type
                    when (event) {
                        is RemoteEvent.ToolCallStart,
                        is RemoteEvent.AiThinking,
                        is RemoteEvent.TitleGenerated -> {
                            if (localStreamingHint[cid] != true) {
                                localStreamingHint[cid] = true
                                _events.tryEmit(RemoteEvent.StateUpdate(isLoading = false, isStreaming = true, seqId = 0, conversationId = cid))
                            }
                        }
                        is RemoteEvent.TextDelta -> {
                            if (localStreamingHint[cid] != true) {
                                localStreamingHint[cid] = true
                                _events.tryEmit(RemoteEvent.StateUpdate(isLoading = false, isStreaming = true, seqId = 0, conversationId = cid))
                            }
                        }
                        is RemoteEvent.StreamDone,
                        is RemoteEvent.Error -> {
                            localStreamingHint[cid] = false
                            _events.tryEmit(RemoteEvent.StateUpdate(isLoading = false, isStreaming = false, seqId = 0, conversationId = cid))
                        }
                        is RemoteEvent.StateUpdate -> {
                            if (!event.isStreaming) localStreamingHint[cid] = false
                        }
                        is RemoteEvent.StateSync -> {
                            localStreamingHint[cid] = event.isStreaming
                        }
                        else -> Unit
                    }
                }
                _events.tryEmit(event)
            }
        }.onFailure { ex ->
            android.util.Log.e("RemoteSessionClient", "Error processing message: ${ex.message}", ex)
        }
    }

    private fun shouldSkipEvent(seqId: Int, currentSessionId: String?): Boolean {
        // If seqId is -1 (not present or invalid), always process.
        // If currentSessionId is null (no session established yet), always process.
        // If the session ID has changed, we've already reset lastSeqId, so process.
        if (seqId == -1 || currentSessionId == null || currentSessionId != lastServerSessionId) {
            return false
        }
        // Only skip if it's a positive seqId we've already seen in the same session.
        return seqId > 0 && seqId <= lastSeqId
    }

    private fun attemptReconnect() {
        if (isManualDisconnect) return
        val ip = lastIp ?: return
        val port = lastPort ?: return

        // Single-flight reconnect: don't start another loop if one is already running.
        if (reconnecting && reconnectJob?.isActive == true) return

        reconnectJob?.cancel()
        reconnecting = true
        reconnectJob = scope.launch {
            try {
                while (isActive && !isManualDisconnect && _connectionState.value != ConnectionState.CONNECTED) {
                    // Avoid repeatedly creating sockets while a connect is already in progress.
                    if (_connectionState.value == ConnectionState.CONNECTING) {
                        delay(250)
                        continue
                    }

                    val jitter = (0..350).random().toLong()
                    val delayMs = (reconnectDelay + jitter).coerceAtMost(30000L)
                    delay(delayMs)

                    if (!isActive || isManualDisconnect) break
                    if (_connectionState.value == ConnectionState.CONNECTED) break

                    try {
                        connect(ip, port, isReconnect = true)
                    } catch (_: Exception) {}

                    // Wait a bit for the connection attempt to succeed before escalating backoff.
                    withTimeoutOrNull(6000L) {
                        while (isActive && _connectionState.value == ConnectionState.CONNECTING) {
                            delay(150)
                        }
                    }

                    if (_connectionState.value != ConnectionState.CONNECTED) {
                        reconnectDelay = (reconnectDelay * 2).coerceAtMost(15000L)
                    }
                }
            } finally {
                reconnecting = false
            }
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        reconnecting = false
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try {
            wsClient?.close()
        } catch (_: Exception) {}
        wsClient = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _serverInfo.value = null
        runCatching { RemoteSessionForegroundService.stop(appContext) }
    }

    // ── Commands to Antigravity ──────────────────────────────────

    fun sendMessage(content: String, conversationId: String? = null, mode: String? = null, attachments: List<RemoteAttachment> = emptyList()) {
        android.util.Log.d("RemoteSessionClient", "sendMessage: content=${content.take(50)}..., attachments=${attachments.size}")
        attachments.forEachIndexed { idx, att ->
            android.util.Log.d("RemoteSessionClient", "Attachment[$idx]: mimeType=${att.mimeType}, dataLen=${att.dataBase64.length}, fileName=${att.fileName}")
        }
        sendCommand("send_message", JSONObject().apply {
            put("content", content)
            if (conversationId != null) {
                put("conversationId", conversationId)
                put("id", conversationId) // Backwards compat
            }
            if (!mode.isNullOrBlank()) {
                put("mode", mode)
            }
            if (attachments.isNotEmpty()) {
                put("attachments", JSONArray().apply {
                    attachments.forEach { attachment ->
                        put(JSONObject().apply {
                            put("mimeType", attachment.mimeType)
                            put("dataBase64", attachment.dataBase64)
                            if (attachment.fileName.isNotBlank()) {
                                put("fileName", attachment.fileName)
                            }
                        })
                    }
                })
            }
        })
    }

    fun stopGeneration(conversationId: String? = null) {
        sendCommand("stop_generation", JSONObject().apply {
            if (!conversationId.isNullOrBlank()) {
                put("conversationId", conversationId)
                put("id", conversationId)
            }
        })
    }

    fun respondToToolInteraction(
        toolCallId: String,
        accepted: Boolean,
        conversationId: String? = null,
        name: String? = null,
        arguments: Map<String, Any?> = emptyMap(),
        metadata: Map<String, String> = emptyMap()
    ) {
        sendCommand("tool_interaction", JSONObject().apply {
            put("toolCallId", toolCallId)
            put("accepted", accepted)
            if (!conversationId.isNullOrBlank()) {
                put("conversationId", conversationId)
                put("id", conversationId)
            }
            if (!name.isNullOrBlank()) {
                put("name", name)
            }
            put("arguments", JSONObject().apply {
                arguments.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
            })
            put("metadata", JSONObject().apply {
                metadata.forEach { (key, value) -> put(key, value) }
            })
        })
    }

    fun newChat() {
        sendCommand("new_chat")
    }

    fun getModels() {
        sendCommand("get_models")
    }

    fun selectModel(modelId: String) {
        sendCommand("select_model", JSONObject().apply {
            put("modelId", modelId)
        })
    }

    fun switchConversation(conversationId: String) {
        sendCommand("switch_conversation", JSONObject().apply {
            put("conversationId", conversationId)
        })
    }

    fun getConversations() {
        sendCommand("get_conversations")
    }

    fun openAgent() {
        sendCommand("open_agent")
    }

    fun refreshState() {
        if (lastSeqId > 0) {
            sendRawMessage(
                JSONObject().apply {
                    put("action", "get_events_since")
                    put("data", JSONObject().put("lastSeqId", lastSeqId))
                }.toString()
            )
        }
        sendCommand("get_state")
    }

    fun forceResync(resetSequence: Boolean = true) {
        if (resetSequence) {
            lastSeqId = 0
            prefs?.edit()?.putInt(KEY_LAST_SEQ_ID, 0)?.apply()
        }
        sendCommand("get_state")
        getModels()
        getConversations()
        getWorkspaces()
    }

    fun loadConversation(id: String) {
        sendCommand("load_conversation", JSONObject().apply {
            put("id", id)
        })
    }

    fun confirmAction(confirmed: Boolean) {
        sendCommand("confirm_action", JSONObject().apply {
            put("confirmed", confirmed)
        })
    }

    fun getWorkspaces() {
        sendCommand("get_workspaces")
    }

    fun getProjectFiles(path: String = "") {
        sendCommand("get_project_files", JSONObject().put("path", path))
    }

    fun getFileDiff() {
        sendCommand("get_file_diff")
    }

    fun getFileContent(path: String) {
        sendCommand("get_file_content", JSONObject().put("path", path))
    }

    fun setConversationMode(mode: com.amaya.intelligence.domain.models.ConversationMode) {
        sendCommand("set_conversation_mode", JSONObject().put("mode", mode.wireValue))
    }

    private fun sendCommand(action: String, data: JSONObject? = null) {
        val msg = JSONObject().apply {
            put("action", action)
            if (data != null) {
                put("data", data)
            }
        }
        sendRawMessage(msg.toString())
    }

    private fun sendRawMessage(payload: String, queueIfDisconnected: Boolean = true) {
        val client = wsClient
        if (client != null && client.isOpen) {
            try {
                client.send(payload)
                return
            } catch (_: Exception) {
            }
        }

        if (queueIfDisconnected) {
            enqueuePendingCommand(payload)
        }
    }

    private fun enqueuePendingCommand(payload: String) {
        val action = extractAction(payload)
        if (action != null && isIdempotentAction(action)) {
            val kept = ArrayDeque<String>()
            while (pendingCommands.isNotEmpty()) {
                val existing = pendingCommands.removeFirst()
                val existingAction = extractAction(existing)
                if (existingAction == action) continue
                kept.addLast(existing)
            }
            pendingCommands.addAll(kept)
        }

        while (pendingCommands.size >= maxPendingCommands) {
            if (pendingCommands.isNotEmpty()) {
                pendingCommands.removeFirst()
            }
        }
        pendingCommands.addLast(payload)
    }

    private fun extractAction(payload: String): String? {
        return try {
            JSONObject(payload).optString("action", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isIdempotentAction(action: String): Boolean {
        return action == "get_state" ||
            action == "get_models" ||
            action == "get_conversations" ||
            action == "get_workspaces" ||
            action == "get_project_files"
    }

    private fun flushPendingCommands() {
        val client = wsClient ?: return
        while (client.isOpen && pendingCommands.isNotEmpty()) {
            val payload = pendingCommands.removeFirst()
            try {
                client.send(payload)
            } catch (_: Exception) {
                pendingCommands.addFirst(payload)
                break
            }
        }
    }

    // ── Parse incoming events ────────────────────────────────────

    private fun parseEvent(json: JSONObject): RemoteEvent? {
        val data = json.optJSONObject("data")
        val seqId = json.optInt("seqId", 0)
        val conversationId = data?.optString("conversationId", "")?.takeIf { it.isNotBlank() } 
            ?: json.optString("conversationId", "").takeIf { it.isNotBlank() }
        val serverIp = json.optNullableString("serverIp")
        
        val eventType = json.optString("event").takeIf { it.isNotBlank() } ?: json.optString("type")
        return when (eventType) {
            "state_sync" -> {
                data ?: return null
                val cw = data.optJSONObject("currentWorkspace")
                RemoteEvent.StateSync(
                    messages = parseMessages(data.optJSONArray("messages")),
                    isLoading = data.optBoolean("isLoading", false),
                    isStreaming = data.optBoolean("isStreaming", false),
                    currentModel = data.optString("currentModel", ""),
                    toolExecutions = parseToolExecutions(data.optJSONArray("toolExecutions")),
                    conversationMode = data.optNullableString("conversationMode"),
                    appName = data.optString("appName", "Antigravity"),
                    appVersion = data.optString("appVersion", ""),
                    currentWorkspace = if (cw != null) RemoteWorkspace(
                        name = cw.optString("name", ""),
                        path = cw.optString("path", ""),
                        isCurrent = true
                    ) else null,
                    seqId = seqId,
                    conversationId = conversationId,
                    serverIp = serverIp
                )
            }
            "state_update" -> {
                data ?: return null
                RemoteEvent.StateUpdate(
                    isLoading = data.optBoolean("isLoading", false),
                    isStreaming = data.optBoolean("isStreaming", false),
                    seqId = seqId,
                    conversationId = conversationId,
                    serverIp = serverIp
                )
            }
            "text_delta" -> {
                data ?: return null
                RemoteEvent.TextDelta(data.optString("text", ""), stepIndex = data.optString("stepIndex", "").takeIf { it.isNotBlank() }, seqId = seqId, conversationId = conversationId)
            }
            "stream_progress" -> {
                data ?: return null
                RemoteEvent.StreamProgress(
                    conversationId = conversationId ?: data.optString("conversationId", ""),
                    sizeDelta = data.optInt("sizeDelta", 0),
                    totalGrowth = data.optInt("totalGrowth", 0),
                    seqId = seqId
                )
            }
            "tool_call_start" -> {
                data ?: return null
                RemoteEvent.ToolCallStart(
                    toolCallId = data.optString("toolCallId", ""),
                    name = data.optString("name", ""),
                    arguments = parseArguments(data.optJSONObject("arguments")),
                    metadata = parseStringMap(data.optJSONObject("metadata")),
                    status = data.optString("status", "RUNNING"),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "tool_call_result" -> {
                data ?: return null
                RemoteEvent.ToolCallResult(
                    toolCallId = data.optString("toolCallId", ""),
                    result = data.optString("result", ""),
                    isError = data.optBoolean("isError", false),
                    seqId = seqId,
                    conversationId = conversationId,
                    serverIp = serverIp
                )
            }
            "tool_activity" -> {
                data ?: return null
                RemoteEvent.ToolActivity(
                    type = data.optString("type", ""),
                    // Extension may send terminal name under `name` instead of `file`
                    file = when {
                        data.has("file") && data.optString("file").isNotBlank() -> data.optString("file", "")
                        data.has("name") && data.optString("name").isNotBlank() -> data.optString("name", "")
                        else -> ""
                    },
                    terminalData = data.optString("data", ""),
                    seqId = seqId,
                    conversationId = conversationId,
                    serverIp = serverIp
                )
            }
            "stream_done" -> {
                val reason = data?.optString("stopReason", "NATURAL_TERMINATION")
                RemoteEvent.StreamDone(stopReason = reason, seqId = seqId, conversationId = conversationId)
            }
            "error" -> {
                data ?: return null

                val message = when {
                    data.has("message") && data.optString("message").isNotBlank() -> data.optString("message")
                    data.has("raw") && data.optString("raw").isNotBlank() -> data.optString("raw")
                    else -> data.toString()
                }

                RemoteEvent.Error(
                    message = humanizeErrorMessage(message),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "confirmation_required" -> {
                data ?: return null
                RemoteEvent.ConfirmationRequired(
                    title = data.optString("title", ""),
                    description = data.optString("description", ""),
                    riskLevel = data.optString("riskLevel", "medium"),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "new_assistant_message" -> RemoteEvent.NewAssistantMessage(seqId = seqId, conversationId = conversationId)
            "new_conversation" -> RemoteEvent.NewConversation(seqId = seqId, conversationId = conversationId, serverIp = serverIp)
            "ai_thinking" -> {
                data ?: return null
                RemoteEvent.AiThinking(
                    text = data.optString("text", ""),
                    stepIndex = data.optString("stepIndex", ""),
                    isRunning = data.optBoolean("isRunning", true),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "title_generated" -> {
                data ?: return null
                RemoteEvent.TitleGenerated(
                    title = data.optString("title", ""),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "status_change" -> {
                data ?: return null
                val status = data.optString("status", "")
                RemoteEvent.StatusChange(
                    status = status,
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "external_activity" -> {
                data ?: return null
                RemoteEvent.ExternalActivity(
                    conversationId = conversationId ?: data.optString("conversationId", ""),
                    seqId = seqId
                )
            }
            "debug_log" -> {
                data ?: return null
                RemoteEvent.DebugLog(
                    message = data.optString("message", ""),
                    timestamp = data.optLong("timestamp", 0L),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "user_message" -> {
                data ?: return null
                val attachmentsArr = data.optJSONArray("attachments")
                val attachments = mutableListOf<MessageAttachment>()
                if (attachmentsArr != null) {
                    for (i in 0 until attachmentsArr.length()) {
                        val a = attachmentsArr.optJSONObject(i) ?: continue
                        attachments.add(MessageAttachment(
                            mimeType = a.optString("mimeType", ""),
                            dataBase64 = a.optString("dataBase64", a.optString("inlineData", "")),
                            fileName = a.optString("fileName", "")
                        ))
                    }
                }
                RemoteEvent.UserMessage(data.optString("content", ""), attachments, seqId = seqId, conversationId = conversationId)
            }
            "conversations_list" -> {
                data ?: return null
                val convArr = data.optJSONArray("conversations")
                val list = mutableListOf<RemoteConversationMeta>()
                if (convArr != null) {
                    for (i in 0 until convArr.length()) {
                        val c = convArr.optJSONObject(i) ?: continue
                        list.add(RemoteConversationMeta(
                            id = c.optString("id", ""),
                            lastModified = c.optLong("lastModified", 0),
                            size = c.optLong("size", 0),
                            title = c.optString("title", ""),
                            preview = c.optString("preview", ""),
                            workspacePath = c.optString("workspacePath", "")
                        ))
                    }
                }
                RemoteEvent.ConversationsList(list, data.optNullableString("currentWorkspacePath"), seqId = seqId, conversationId = conversationId)
            }
            "models_list" -> {
                data ?: return null
                val modelArr = data.optJSONArray("models")
                val selectedModelId = data.optString("selectedModelId", "")
                val list = mutableListOf<RemoteModelInfo>()
                if (modelArr != null) {
                    for (i in 0 until modelArr.length()) {
                        val m = modelArr.optJSONObject(i) ?: continue
                        val quotaInfo = m.optJSONObject("quotaInfo")
                        val quota = when {
                            m.has("quota") -> m.optDouble("quota", 1.0)
                            quotaInfo != null -> quotaInfo.optDouble("remainingFraction", 1.0)
                            else -> 1.0
                        }
                        val resetTime = m.optNullableString("resetTime")
                            ?: quotaInfo?.optNullableString("resetTime")
                        list.add(RemoteModelInfo(
                            id = m.optString("id", ""),
                            label = m.optString("label", ""),
                            isRecommended = m.optBoolean("isRecommended", false),
                            quota = quota,
                            quotaLabel = m.optNullableString("quotaLabel"),
                            resetTime = resetTime,
                            tagTitle = m.optNullableString("tagTitle"),
                            supportsImages = m.optBoolean("supportsImages", false)
                        ))
                    }
                }
                RemoteEvent.ModelsList(list, selectedModelId, seqId = seqId, conversationId = conversationId)
            }
            "conversation_loaded" -> {
                data ?: return null
                RemoteEvent.ConversationLoaded(
                    conversationId = conversationId ?: data.optString("conversationId", ""),
                    messages = parseMessages(data.optJSONArray("messages")),
                    conversationMode = data.optNullableString("conversationMode"),
                    seqId = seqId,
                    serverIp = serverIp
                )
            }
            "model_selected" -> {
                data ?: return null
                RemoteEvent.ModelSelected(data.optString("modelId", ""), seqId = seqId, conversationId = conversationId)
            }
            "workspaces_list" -> {
                data ?: return null
                val wsArr = data.optJSONArray("workspaces")
                val list = mutableListOf<RemoteWorkspace>()
                if (wsArr != null) {
                    for (i in 0 until wsArr.length()) {
                        val w = wsArr.optJSONObject(i) ?: continue
                        list.add(RemoteWorkspace(
                            name = w.optString("name", ""),
                            path = w.optString("path", ""),
                            isCurrent = w.optBoolean("isCurrent", false)
                        ))
                    }
                }
                RemoteEvent.WorkspacesList(list, seqId = seqId, conversationId = conversationId)
            }
            "project_files" -> {
                data ?: return null
                val fileArr = data.optJSONArray("files")
                val list = mutableListOf<RemoteFileEntry>()
                if (fileArr != null) {
                    for (i in 0 until fileArr.length()) {
                        val f = fileArr.optJSONObject(i) ?: continue
                        list.add(RemoteFileEntry(
                            name = f.optString("name", ""),
                            path = f.optString("path", ""),
                            type = f.optString("type", "file"),
                            size = f.optLong("size", 0)
                        ))
                    }
                }
                RemoteEvent.ProjectFiles(list, data.optString("path", ""), seqId = seqId, conversationId = conversationId)
            }
            "file_diff" -> {
                data ?: return null
                RemoteEvent.FileDiff(
                    diff = data.optString("diff", ""),
                    error = data.optNullableString("error"),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "file_content" -> {
                data ?: return null
                RemoteEvent.FileContent(
                    path = data.optString("path", ""),
                    content = data.optString("content", ""),
                    error = data.optNullableString("error"),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            "active_conversation" -> {
                data ?: return null
                RemoteEvent.ActiveConversation(
                    conversationId = data.optString("conversationId", conversationId ?: ""),
                    seqId = seqId,
                    serverIp = serverIp
                )
            }
            "current_workspace" -> {
                data ?: return null
                RemoteEvent.CurrentWorkspace(
                    name = data.optString("name", ""),
                    path = data.optString("path", ""),
                    seqId = seqId,
                    conversationId = conversationId
                )
            }
            else -> null
        }
    }

    private fun parseMessages(arr: JSONArray?): List<RemoteChatMessage> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val content = obj.optString("content", "").ifBlank {
                obj.optString("contentPreview", "")
            }
            val thinking = obj.optNullableString("thinking")
                ?: obj.optNullableString("thinkingPreview")
            
            // Parse attachments
            val attachmentsArr = obj.optJSONArray("attachments")
            val attachments = mutableListOf<MessageAttachment>()
            if (attachmentsArr != null) {
                for (j in 0 until attachmentsArr.length()) {
                    val a = attachmentsArr.optJSONObject(j) ?: continue
                    attachments.add(MessageAttachment(
                        mimeType = a.optString("mimeType", ""),
                        dataBase64 = a.optString("dataBase64", a.optString("inlineData", "")),
                        fileName = a.optString("fileName", "")
                    ))
                }
            }
            
            RemoteChatMessage(
                role = obj.optString("role", "user"),
                content = content,
                thinking = thinking,
                intent = obj.optNullableString("intent"),
                toolExecutions = parseToolExecutions(obj.optJSONArray("toolExecutions")),
                metadata = parseStringMap(obj.optJSONObject("metadata")),
                attachments = attachments
            )
        }
    }

    private fun parseToolExecutions(arr: JSONArray?): List<RemoteToolExecution> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            RemoteToolExecution(
                toolCallId = obj.optString("toolCallId", ""),
                name = obj.optString("name", ""),
                arguments = parseArguments(obj.optJSONObject("arguments")),
                result = obj.optString("result", "").takeIf { it.isNotBlank() },
                status = obj.optString("status", "PENDING"),
                metadata = parseStringMap(obj.optJSONObject("metadata"))
            )
        }
    }

    private fun parseStringMap(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.opt(key)?.toString().orEmpty()
        }
        return map
    }

    private fun parseArguments(json: JSONObject?): Map<String, Any?> {
        if (json == null) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.opt(key)
        }
        return map
    }

    private fun humanizeErrorMessage(input: String): String {
        var msg = input

        val re = Regex("(?i)(reset\\s+after\\s+)([0-9dhms]+)\\b")
        val m = re.find(msg)
        if (m != null) {
            val dur = m.groupValues[2]
            var totalMs = 0L

            Regex("(\\d+)([dhms])", RegexOption.IGNORE_CASE).findAll(dur).forEach { p ->
                val v = p.groupValues[1].toLongOrNull() ?: return@forEach
                when (p.groupValues[2].lowercase()) {
                    "d" -> totalMs += v * 24L * 60L * 60L * 1000L
                    "h" -> totalMs += v * 60L * 60L * 1000L
                    "m" -> totalMs += v * 60L * 1000L
                    "s" -> totalMs += v * 1000L
                }
            }

            if (totalMs > 0) {
                val dt = java.util.Date(System.currentTimeMillis() + totalMs)
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                val formatted = fmt.format(dt)
                msg = msg.replace(re, "reset at $formatted")
            }
        }

        if (msg.length > 900) msg = msg.take(900) + "..."
        return msg.trim().ifBlank { "Unknown error" }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun updateForegroundStatus(event: RemoteEvent) {
        val now = System.currentTimeMillis()
        val context = appContext
        
        // Critical states (start/stop) bypass rate limit
        val isCritical = event is RemoteEvent.ToolCallStart || event is RemoteEvent.StreamDone || event is RemoteEvent.Error

        if (!isCritical && now - lastForegroundUpdateAt < minForegroundUpdateInterval) {
            return
        }

        lastForegroundUpdateAt = now

        when (event) {
            is RemoteEvent.ToolCallStart -> {
                runCatching {
                    RemoteSessionForegroundService.updateStatus(
                        context,
                        RemoteSessionForegroundService.STATE_TOOL,
                        event.name
                    )
                }
            }
            is RemoteEvent.AiThinking -> {
                runCatching {
                    RemoteSessionForegroundService.updateStatus(
                        context,
                        RemoteSessionForegroundService.STATE_THINKING,
                        event.text
                    )
                }
            }
            is RemoteEvent.TextDelta -> {
                runCatching {
                    RemoteSessionForegroundService.updateStatus(
                        context,
                        RemoteSessionForegroundService.STATE_TEXT,
                        event.text
                    )
                }
            }
            is RemoteEvent.StreamDone,
            is RemoteEvent.Error -> {
                runCatching {
                    RemoteSessionForegroundService.updateStatus(
                        context,
                        RemoteSessionForegroundService.STATE_DONE
                    )
                }
            }
            else -> Unit
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}

// ── Data Models ──────────────────────────────────────────────────

sealed class RemoteEvent {
    abstract val seqId: Int
    abstract val conversationId: String?
    open val serverIp: String? = null

    data class StateSync(
        val messages: List<RemoteChatMessage>,
        val isLoading: Boolean,
        val isStreaming: Boolean,
        val currentModel: String,
        val toolExecutions: List<RemoteToolExecution>,
        val conversationMode: String? = null,
        val appName: String = "Antigravity",
        val appVersion: String = "",
        val currentWorkspace: RemoteWorkspace? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class StateUpdate(
        val isLoading: Boolean,
        val isStreaming: Boolean,
        override val seqId: Int = 0,
        override val conversationId: String? = null,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class TextDelta(
        val text: String,
        val stepIndex: String? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class StreamProgress(
        override val conversationId: String,
        val sizeDelta: Int,
        val totalGrowth: Int,
        override val seqId: Int = 0
    ) : RemoteEvent()

    data class ToolCallStart(
        val toolCallId: String,
        val name: String,
        val arguments: Map<String, Any?>,
        val metadata: Map<String, String> = emptyMap(),
        val status: String = "RUNNING",
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ToolCallResult(
        val toolCallId: String,
        val result: String,
        val isError: Boolean,
        override val seqId: Int = 0,
        override val conversationId: String? = null,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class ToolActivity(
        val type: String,
        val file: String = "",
        val terminalData: String = "",
        override val seqId: Int = 0,
        override val conversationId: String? = null,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class StreamDone(
        val stopReason: String? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class Error(
        val message: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ConfirmationRequired(
        val title: String,
        val description: String,
        val riskLevel: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class NewAssistantMessage(
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()
    data class NewConversation(
        override val seqId: Int = 0,
        override val conversationId: String? = null,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class ExternalActivity(
        override val conversationId: String,
        override val seqId: Int = 0
    ) : RemoteEvent()

    data class UserMessage(
        val content: String,
        val attachments: List<MessageAttachment> = emptyList(),
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class AiThinking(
        val text: String,
        val stepIndex: String = "",
        val isRunning: Boolean = true,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class TitleGenerated(
        val title: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class StatusChange(
        val status: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ConversationsList(
        val conversations: List<RemoteConversationMeta>,
        val currentWorkspacePath: String? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ModelsList(
        val models: List<RemoteModelInfo>,
        val selectedModelId: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ConversationLoaded(
        override val conversationId: String,
        val messages: List<RemoteChatMessage>,
        val conversationMode: String? = null,
        override val seqId: Int = 0,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class DebugLog(
        val message: String,
        val timestamp: Long,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ModelSelected(
        val modelId: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class WorkspacesList(
        val workspaces: List<RemoteWorkspace>,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ProjectFiles(
        val files: List<RemoteFileEntry>,
        val path: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class FileDiff(
        val diff: String,
        val error: String? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class FileContent(
        val path: String,
        val content: String,
        val error: String? = null,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()

    data class ActiveConversation(
        override val conversationId: String,
        override val seqId: Int = 0,
        override val serverIp: String? = null
    ) : RemoteEvent()

    data class CurrentWorkspace(
        val name: String,
        val path: String,
        override val seqId: Int = 0,
        override val conversationId: String? = null
    ) : RemoteEvent()
}

data class RemoteChatMessage(
    val role: String,
    val content: String,
    val thinking: String? = null,
    val intent: String? = null,
    val toolExecutions: List<RemoteToolExecution> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<MessageAttachment> = emptyList()
)

data class RemoteToolExecution(
    val toolCallId: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val result: String? = null,
    val status: String = "PENDING",
    val metadata: Map<String, String> = emptyMap()
)

data class RemoteAttachment(
    val mimeType: String,
    val dataBase64: String,
    val fileName: String = ""
)

data class RemoteConversationMeta(
    val id: String,
    val lastModified: Long,
    val size: Long,
    val title: String = "",
    val preview: String = "",
    val workspacePath: String = ""
)

data class RemoteModelInfo(
    val id: String,
    val label: String,
    val isRecommended: Boolean,
    val quota: Double,
    val quotaLabel: String? = null,
    val resetTime: String? = null,
    val tagTitle: String? = null,
    val supportsImages: Boolean
)

data class RemoteWorkspace(
    val name: String,
    val path: String,
    val isCurrent: Boolean = false
)

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val type: String, // "file" or "directory"
    val size: Long = 0
)
