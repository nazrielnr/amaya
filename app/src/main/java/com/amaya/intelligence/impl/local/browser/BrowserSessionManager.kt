package com.amaya.intelligence.impl.local.browser

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import org.mozilla.geckoview.GeckoView
import javax.inject.Inject
import javax.inject.Singleton
import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.tools.ToolExecutionContext

@Singleton
class BrowserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
) {
    private data class SessionKey(val conversationKey: String, val agentId: Long)

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val headlessSurfaceSlots = Semaphore(2)
    private val executionWakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amaya:browser-agent")
        .apply { setReferenceCounted(true) }
    private val sessions = object : LinkedHashMap<SessionKey, BrowserConversationSession>(8, 0.75f, true) {}
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
    private var visibleKey: SessionKey? = null
    private var sharedViewOwner: SessionKey? = null
    private var visibleStateJob: Job? = null
    private var workspacePath: String? = null

    init {
        val template = context.assets.open("browser-bridge/dom-inspector.js")
            .bufferedReader()
            .use { it.readText() }
        DomInspector.installBaseInspectorTemplate(template)
    }

    @Synchronized
    private fun sessionFor(key: SessionKey): BrowserConversationSession {
        sessions[key]?.let { return it }
        val session = BrowserConversationSession(context, headlessSurfaceSlots, ::evictIdleHeadlessSurface, memoryRepository).apply {
            resetForConversation(key.conversationKey, key.agentId)
            setWorkspace(workspacePath)
        }
        sessions[key] = session
        return session
    }

    @Synchronized
    private fun selected(): BrowserConversationSession? = visibleKey?.let(sessions::get)

    @Synchronized
    private fun trimSessions() {
        while (sessions.size > MAX_RESIDENT_SESSIONS) {
            val removable = sessions.entries.firstOrNull {
                it.key != visibleKey && it.key != sharedViewOwner && !it.value.isExecuting()
            } ?: return
            sessions.remove(removable.key)
            removable.value.close()
        }
    }

    @Synchronized
    fun selectConversation(key: String, agentId: Long? = null) {
        if (agentId == null) {
            visibleKey = null
            visibleStateJob?.cancel()
            visibleStateJob = null
            _uiState.value = BrowserUiState()
            return
        }
        val sessionKey = SessionKey(key, agentId)
        val session = sessionFor(sessionKey)
        visibleKey = sessionKey
        trimSessions()
        visibleStateJob?.cancel()
        _uiState.value = session.uiState.value
        visibleStateJob = scope.launch { session.uiState.collect { _uiState.value = it } }
    }

    fun setWorkspace(path: String?) {
        workspacePath = path?.takeIf(String::isNotBlank)
        selected()?.setWorkspace(workspacePath)
    }

    fun canOpenOperator(): Boolean = selected()?.canOpenOperator() == true
    fun markAuthHandoffCompleted() { selected()?.markAuthHandoffCompleted() }
    fun releaseInactiveRuntimes() { synchronized(this) { sessions.values.toList() }.forEach(BrowserConversationSession::releaseInactiveRuntimes); trimSessions() }
    fun resetForConversation(key: String, agentId: Long? = null) = selectConversation(key, agentId)
    fun resetEphemeral() { selectConversation("", null) }
    fun sessionId(): String = selected()?.sessionId().orEmpty()
    fun takeLastScreenshotAttachment(executionContext: ToolExecutionContext): String? {
        val agentId = executionContext.agentId ?: return null
        val key = SessionKey(executionContext.conversationId?.let { "conversation:$it" } ?: "agent:$agentId", agentId)
        return synchronized(this) { sessions[key] }?.takeLastScreenshotAttachment()
    }
    suspend fun captureScreenshotToWorkspace(): BrowserToolResponse = selected()?.captureScreenshotToWorkspace() ?: BrowserToolResponse.Failure("No active browser session")
    fun clearActiveSiteData() { selected()?.clearActiveSiteData() }
    fun provideUploadUris(uris: Array<Uri>?) { selected()?.provideUploadUris(uris) }
    fun cancelPendingUpload() { selected()?.cancelPendingUpload() }
    fun openDownload(download: BrowserDownload): Uri? = selected()?.openDownload(download)
    fun deleteDownload(download: BrowserDownload) { selected()?.deleteDownload(download) }
    fun clearSessionState() { selected()?.clearSessionState() }
    suspend fun switchToTab(pageId: String): BrowserToolResponse = selected()?.switchToTab(pageId) ?: BrowserToolResponse.Failure("No active browser session")
    fun acquireSharedBrowserView(): GeckoView {
        val previous: BrowserConversationSession?
        val current: BrowserConversationSession
        synchronized(this) {
            val key = checkNotNull(visibleKey) { "No active browser session" }
            previous = sharedViewOwner?.takeIf { it != key }?.let(sessions::get)
            sharedViewOwner = key
            current = checkNotNull(sessions[key]) { "No active browser session" }
        }
        previous?.releaseSharedBrowserView()
        return current.acquireSharedBrowserView()
    }

    fun releaseSharedBrowserView() {
        val owner = synchronized(this) {
            sharedViewOwner?.let(sessions::get).also { sharedViewOwner = null }
        }
        owner?.releaseSharedBrowserView()
        trimSessions()
    }

    /**
     * The operator host stopped or resumed without leaving composition. A stopped host no
     * longer keeps its page alive on its own, so the session has to hold active state, high
     * priority, and an offscreen display until it comes back.
     */
    fun onHostVisibilityChanged(visible: Boolean) {
        synchronized(this) { sharedViewOwner?.let(sessions::get) }?.setHostVisible(visible)
    }

    /** Frees one offscreen surface slot from an idle conversation so a busy one can warm up. */
    private fun evictIdleHeadlessSurface(requester: BrowserConversationSession) {
        val victim = synchronized(this) {
            sessions.values.firstOrNull { it !== requester && it.holdsHeadlessSurface() && !it.isExecuting() }
        }
        victim?.releaseHeadlessSurface()
    }
    fun isDispatchingAgentInput(): Boolean = selected()?.isDispatchingAgentInput() == true
    fun hideSoftKeyboardForAgent() { selected()?.hideSoftKeyboardForAgent() }
    fun onAssistantStreamingChanged(streaming: Boolean) { selected()?.onAssistantStreamingChanged(streaming) }
    fun onAssistantTextDelta(delta: String) { selected()?.onAssistantTextDelta(delta) }
    fun onAgentTouch(x: Float, y: Float) { selected()?.onAgentTouch(x, y) }
    fun onBrowserError(message: String) { selected()?.onBrowserError(message) }
    suspend fun executeBrowserTask(arguments: Map<String, Any?>, executionContext: ToolExecutionContext = ToolExecutionContext()): String {
        val agentId = executionContext.agentId
            ?: return JSONObject().put("status", "error").put("error", "Browser requires an active Agent").toString(2)
        val key = SessionKey(executionContext.conversationId?.let { "conversation:$it" } ?: "agent:$agentId", agentId)
        val session = synchronized(this) { sessionFor(key).also { it.retain() } }
        executionWakeLock.acquire(BROWSER_ACTION_WAKE_TIMEOUT_MS)
        return try {
            session.setWorkspace(executionContext.workspacePath?.takeIf(String::isNotBlank) ?: workspacePath)
            session.executeBrowserTask(arguments, executionContext)
        } finally {
            if (executionWakeLock.isHeld) executionWakeLock.release()
            session.release()
            trimSessions()
        }
    }
    suspend fun execute(toolName: String, arguments: Map<String, Any?>): BrowserToolResponse =
        selected()?.execute(toolName, arguments) ?: BrowserToolResponse.Failure("No active browser session")
    fun cancelFromUser() { selected()?.cancelFromUser() }

    companion object {
        private const val MAX_RESIDENT_SESSIONS = 6
        private const val BROWSER_ACTION_WAKE_TIMEOUT_MS = 60_000L
    }
}
