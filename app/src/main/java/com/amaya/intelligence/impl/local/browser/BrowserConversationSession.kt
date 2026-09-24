package com.amaya.intelligence.impl.local.browser
import android.content.Context
import android.net.Uri
import android.view.View
import android.graphics.PixelFormat
import android.media.ImageReader
import org.mozilla.geckoview.GeckoDisplay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import androidx.core.content.FileProvider
import com.amaya.intelligence.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
@Singleton
internal class BrowserConversationSession(
    internal val context: Context,
    internal val headlessSurfaceSlots: Semaphore,
    internal val requestHeadlessSlot: (BrowserConversationSession) -> Unit,
    internal val memoryRepository: MemoryRepository,
) {
    internal var sessionId = newSessionId()
    private val initialTab = BrowserPageTab()
    internal val _uiState = MutableStateFlow(BrowserUiState(
        sessionId = sessionId,
        activeTabId = initialTab.id,
        tabs = listOf(initialTab)
    ))
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
    internal class PageRuntime(
        val tabId: String,
        val view: GeckoView,
        val session: GeckoSession,
        val controller: AndroidBrowserController,
        var display: GeckoDisplay? = null,
        var imageReader: ImageReader? = null,
        var processGone: Boolean = false
    )
    @Volatile internal var controller: AndroidBrowserController? = null
    internal val pageRuntimes = mutableMapOf<String, PageRuntime>()
    internal val assistantStreamBuffer = StringBuilder()
    internal var lastAssistantStreamUiEmitAt = 0L
    internal val browserId = "browser_local_001"
    internal var parentTaskId = "browser_task_${UUID.randomUUID().toString().take(8)}"
    internal var parentStartedAt = BrowserResponseFormatter.nowIso()
    internal var parentSummary = "Browser task"
    internal val parentSubToolcalls = mutableListOf<JSONObject>()
    internal var pendingApprovalCallId: String? = null
    internal var pendingApprovalStepIndex: Int? = null
    internal var conversationKey: String? = null
    internal var visibleConversationKey: String? = null
    internal var visibleAgentId: Long? = null
    internal var pendingRestoreUrl: String? = null
    internal val persistence = BrowserSessionPersistence(context.getSharedPreferences("browser_sessions", Context.MODE_PRIVATE))
    internal val executionMutex = Mutex()
    @Volatile internal var fileChooserCallback: ((Array<Uri>?) -> Unit)? = null
    @Volatile internal var queuedUploadDecision = false
    @Volatile internal var queuedUploadUris: Array<Uri>? = null
    @Volatile internal var pendingUploadAcceptTypes: Array<String> = emptyArray()
    @Volatile internal var pendingUploadMultiple: Boolean = false
    internal var workspacePath: String? = null
    internal var lastDownloadUri: String? = null
    internal var lastDownloadAtMs = 0L
    internal val resumeScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    @Volatile internal var visibleHost = false
    internal var headlessSurfaceHeld = false
    internal val clients = AtomicInteger()
    suspend fun switchToTab(pageId: String): BrowserToolResponse = execute("switch_tab", mapOf("page_id" to pageId))
    internal fun newSessionId(): String = "sess_android_${UUID.randomUUID().toString().take(8)}"
    internal fun stableSessionId(key: String, agentId: Long?): String = "sess_android_${UUID.nameUUIDFromBytes("$key|agent:$agentId".toByteArray()).toString().take(8)}"
    internal fun restoreState(id: String): String? = persistence.activeUrl(id)
    internal fun restoreTabs(id: String): List<BrowserPageTab> = persistence.tabs(id)
    internal fun restoreHistory(id: String): List<BrowserHistoryEntry> = persistence.history(id)
    internal fun persistState(state: BrowserUiState) = persistence.save(state)
    fun acquireSharedBrowserView(): GeckoView {
        if (headlessSurfaceHeld) detachHeadlessSurface()
        visibleHost = true
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Browser view must be acquired on the main thread"
        }
        val view = ensureSharedControllerOnMain().first
        controller?.setVisibleFileChooserHost(true)
        pageRuntimes.forEach { (tabId, runtime) ->
            val active = tabId == _uiState.value.activeTabId
            runtime.session.setActive(active)
            runtime.session.setPriorityHint(if (active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
        }
        if (!pendingRestoreUrl.isNullOrBlank() && controller?.currentUrl() == "about:blank") {
            val url = pendingRestoreUrl ?: return view
            pendingRestoreUrl = null
            controller?.session?.loadUri(url)
        }
        return view
    }
    fun releaseSharedBrowserView() {
        visibleHost = false
        controller?.setVisibleFileChooserHost(false)
        // A task in flight still needs its page: hand it to the offscreen display instead
        // of deactivating the session, which would let Android kill the content process.
        if (isExecuting()) {
            moveToOffscreenDisplay()
            return
        }
        detachHeadlessSurface()
        pageRuntimes.values.forEach { runtime ->
            runtime.session.setActive(false)
            runtime.session.setFocused(false)
            runtime.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        }
    }
    fun setHostVisible(visible: Boolean) {
        if (visibleHost == visible) return
        visibleHost = visible
        controller?.setVisibleFileChooserHost(visible)
        if (visible) {
            // Release the offscreen display before the recreated GeckoView surface claims it.
            detachHeadlessSurface()
            val active = _uiState.value.activeTabId
            pageRuntimes.forEach { (tabId, runtime) ->
                runtime.session.setActive(tabId == active)
                runtime.session.setPriorityHint(if (tabId == active) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT)
            }
        } else {
            moveToOffscreenDisplay()
        }
    }
    /**
     * A stopped host usually keeps its GeckoView display, so the page survives as long as the
     * session stays active and high priority. Re-assert that first, then try the offscreen
     * display for hosts that really did release their surface.
     */
    private fun moveToOffscreenDisplay() {
        pageRuntimes[_uiState.value.activeTabId]?.session?.let { session ->
            session.setActive(true)
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        }
        resumeScope.launch { runCatching { attachHeadlessSurfaceOnMain() } }
    }
    fun isDispatchingAgentInput(): Boolean = controller?.isDispatchingAgentInput == true
    fun hideSoftKeyboardForAgent() {
        controller?.hideSoftKeyboard()
    }
    internal suspend fun attachHeadlessSurfaceOnMain() {
        val tabId = _uiState.value.activeTabId ?: return
        val runtime = pageRuntimes[tabId] ?: return
        // Only a *visible* host paints the session. A stopped activity keeps its GeckoView
        // attached while its surface is gone, so isAttachedToWindow alone used to skip the
        // offscreen display and leave the content process eligible for reclaim.
        if (visibleHost && runtime.view.isAttachedToWindow) return
        if (runtime.display != null) return
        if (headlessSurfaceHeld) detachHeadlessSurface(releaseSlot = false)
        else if (!acquireHeadlessSlot()) {
            // Every slot belongs to a session that is also busy. Run without the offscreen
            // display; the action still works, the page just stays reclaimable.
            android.util.Log.w("AmayaBrowser", "no offscreen browser surface slot available")
            return
        }
        val imageReader = ImageReader.newInstance(1080, 1920, PixelFormat.RGBX_8888, 2).apply {
            setOnImageAvailableListener({ reader -> reader.acquireLatestImage()?.close() }, null)
        }
        val display = runCatching { runtime.session.acquireDisplay() }.getOrElse { error ->
            // Expected while a stopped-but-composed host still owns the display; that display
            // keeps the page alive on its own, so this is a no-op rather than a failure.
            android.util.Log.d("AmayaBrowser", "offscreen display unavailable: ${error.message}")
            imageReader.close()
            headlessSurfaceSlots.release()
            return
        }
        display.surfaceChanged(GeckoDisplay.SurfaceInfo.Builder(imageReader.surface).size(1080, 1920).build())
        runtime.imageReader = imageReader
        runtime.display = display
        runtime.session.setActive(true)
        runtime.session.setFocused(true)
        runtime.session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        headlessSurfaceHeld = true
    }
    private suspend fun acquireHeadlessSlot(): Boolean {
        if (headlessSurfaceSlots.tryAcquire()) return true
        requestHeadlessSlot(this)
        if (headlessSurfaceSlots.tryAcquire()) return true
        return withTimeoutOrNull(HEADLESS_SLOT_WAIT_MS) { headlessSurfaceSlots.acquire(); true } == true
    }
    fun holdsHeadlessSurface(): Boolean = headlessSurfaceHeld
    fun releaseHeadlessSurface() = detachHeadlessSurface()
    internal fun detachHeadlessSurface(releaseSlot: Boolean = true) {
        val hasDisplay = pageRuntimes.values.any { it.display != null }
        if (!headlessSurfaceHeld && !hasDisplay) return
        pageRuntimes.values.forEach { runtime ->
            runtime.display?.let { display ->
                runCatching { display.surfaceDestroyed() }
                runCatching { runtime.session.releaseDisplay(display) }
            }
            runtime.imageReader?.close()
            runtime.display = null
            runtime.imageReader = null
            runtime.session.setActive(false)
            runtime.session.setFocused(false)
            runtime.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        }
        val releasePermit = headlessSurfaceHeld
        headlessSurfaceHeld = false
        if (releaseSlot && releasePermit) headlessSurfaceSlots.release()
    }
    internal fun ensureSharedControllerOnMain(): Pair<GeckoView, AndroidBrowserController> {
        val tabId = _uiState.value.activeTabId ?: BrowserPageTab().id
        pageRuntimes[tabId]?.let { runtime ->
            controller = runtime.controller
            return runtime.view to runtime.controller
        }
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: BrowserPageTab(id = tabId)
        val session = GeckoSession()
        session.open(GeckoBrowserRuntime.get(context))
        val view = GeckoView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setSession(session)
            // Headless Gecko still needs a logical viewport for native file-input
            // gestures; the offscreen display supplies the actual pixels.
            measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        session.setActive(false)
        session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT)
        val newController = AndroidBrowserController(
            geckoView = view,
            session = session,
            capturePixels = { pageRuntimes[tabId]?.display?.capturePixels() ?: view.capturePixels() },
            onNavigationChanged = { url, title, progress, back, forward ->
                onNavigationChanged(tabId, url, title, progress, back, forward)
            },
            onScrollChanged = { x, y -> onScrollChanged(tabId, x, y) },
            onError = this::onBrowserError,
            onAgentTouch = this::onAgentTouch,
            onDownload = this::handleGeckoDownload,
            onProcessGone = { lastUrl -> handleProcessGone(tabId, lastUrl) },
            onFileChooser = { acceptTypes, multiple, callback ->
                fileChooserCallback?.invoke(null)
                fileChooserCallback = callback
                pendingUploadAcceptTypes = acceptTypes
                pendingUploadMultiple = multiple
                _uiState.update { it.copy(uploadPending = true, uploadAcceptTypes = acceptTypes.filter(String::isNotBlank), uploadRequestNonce = System.currentTimeMillis()) }
                if (queuedUploadDecision) {
                    val queued = queuedUploadUris
                    queuedUploadDecision = false
                    queuedUploadUris = null
                    if (queued.isNullOrEmpty()) {
                        callback(null)
                    } else {
                        // Agent-selected workspace files already have stable FileProvider
                        // URIs. Pass them directly; staging here changes the filename and
                        // breaks the upload postcondition.
                        callback(queued)
                        _uiState.update { it.copy(uploadPending = false, uploadAcceptTypes = emptyList()) }
                    }
                }
            }
        )
        pageRuntimes[tabId] = PageRuntime(tabId, view, session, newController)
        controller = newController
        _uiState.update { state ->
            state.copy(
                sessionId = sessionId,
                browserId = browserId,
                conversationKey = conversationKey,
                status = BrowserAgentStatus.IDLE,
                currentAction = "Browser ready",
                lastError = null
            )
        }
        return view to newController
    }
    private fun onScrollChanged(tabId: String, x: Int, y: Int) {
        _uiState.update { state -> state.copy(tabs = state.tabs.map { tab -> if (tab.id == tabId) tab.copy(scrollX = x, scrollY = y) else tab }) }
        persistState(_uiState.value)
    }
    private fun onNavigationChanged(tabId: String, url: String, title: String, progress: Float, canGoBack: Boolean, canGoForward: Boolean) {
        // Gecko may still deliver a final callback after a tab is removed.
        if (_uiState.value.tabs.none { it.id == tabId }) return
        _uiState.update { state ->
            val isActive = tabId == state.activeTabId
            val tabs = state.tabs.map { tab ->
                if (tab.id == tabId) tab.copy(title = title.ifBlank { url }, url = url, canGoBack = canGoBack, canGoForward = canGoForward) else tab
            }
            state.copy(
                activeUrl = if (isActive) url else state.activeUrl,
                activeTitle = if (isActive) title.ifBlank { url } else state.activeTitle,
                progress = if (isActive) progress else state.progress,
                tabs = tabs,
                sessionHistory = if (!isActive || url == "about:blank" || state.sessionHistory.lastOrNull()?.url == url) state.sessionHistory else
                    (state.sessionHistory + BrowserHistoryEntry(url, title.ifBlank { url })).takeLast(60)
            )
        }
        persistState(_uiState.value)
    }
    fun onAgentTouch(x: Float, y: Float) {
        _uiState.update { state -> state.copy(agentTouchX = x, agentTouchY = y, agentTouchNonce = System.currentTimeMillis()) }
    }
    private fun handleProcessGone(tabId: String, lastUrl: String) {
        pageRuntimes[tabId]?.processGone = true
        // Keep the reclaimed page's URL in tab state; recovery reloads from it.
        if (lastUrl.isNotBlank() && lastUrl != "about:blank") {
            _uiState.update { state ->
                state.copy(tabs = state.tabs.map { tab -> if (tab.id == tabId) tab.copy(url = lastUrl) else tab })
            }
            persistState(_uiState.value)
        }
    }
    fun onBrowserError(message: String) {
        _uiState.update { it.copy(status = BrowserAgentStatus.ERROR, lastError = message, currentAction = "Browser error") }
        appendLog("browser", "", "error", message)
    }
    suspend fun execute(toolName: String, arguments: Map<String, Any?>): BrowserToolResponse {
        val argsPreview = arguments
            .filterKeys { !it.startsWith("__") && it != "text" }
            .entries.joinToString { "${it.key}=${it.value}" }
            .take(180)
        appendLog(toolName, argsPreview, "running", "Starting")
        _uiState.update {
            it.copy(
                status = BrowserAgentStatus.BROWSING,
                currentAction = readableAction(toolName),
                inspectedElement = (arguments["selector"] ?: arguments["query"])?.toString(),
                lastError = null,
                isCancelled = false
            )
        }
        val result = try {
            when (toolName) {
                "cancel_action" -> cancelAction()
                else -> executeBrowserTool(toolName, arguments)
            }
        } catch (e: Exception) {
            android.util.Log.e("AmayaBrowser", "$toolName failed", e)
            BrowserToolResponse.Failure("${toolName} failed: ${e.message ?: e::class.java.simpleName}")
        }
        when (result) {
            is BrowserToolResponse.Success -> {
                appendLog(toolName, argsPreview, "success", result.output.take(220))
                val image = result.metadata["image_base64"] as? String
                _uiState.update {
                    it.copy(
                        status = BrowserAgentStatus.IDLE,
                        currentAction = "Completed ${readableAction(toolName)}",
                        screenshotBase64 = image ?: it.screenshotBase64,
                        evaluateResult = if (toolName == "evaluate") result.output else it.evaluateResult,
                        lastError = null
                    )
                }
            }
            is BrowserToolResponse.Failure -> {
                appendLog(toolName, argsPreview, "error", result.message)
                _uiState.update {
                    it.copy(
                        status = if (result.recoverable) BrowserAgentStatus.ERROR else BrowserAgentStatus.CANCELLED,
                        currentAction = "Failed ${readableAction(toolName)}",
                        lastError = result.message
                    )
                }
            }
        }
        return result
    }
    fun cancelFromUser() {
        controller?.cancel()
        _uiState.update {
            it.copy(
                status = BrowserAgentStatus.CANCELLED,
                currentAction = "Cancelled by user",
                isCancelled = true
            )
        }
        appendLog("cancel_action", "", "cancelled", "Cancelled by user")
    }
    internal suspend fun hydrateRestoredPageIfNeeded(action: String) {
        if (action in setOf("open_url", "new_page", "new_tab", "get_status", "list_pages")) return
        val url = pendingRestoreUrl?.takeIf { it.isNotBlank() && it != "about:blank" } ?: return
        pendingRestoreUrl = null
        val active = ensureController()
        if (active.currentUrl() == "about:blank") active.openUrl(url)
    }
    internal suspend fun ensureController(): AndroidBrowserController {
        val active = withContext(Dispatchers.Main.immediate) {
            ensureSharedControllerOnMain().also { attachHeadlessSurfaceOnMain() }.second
        }
        val tabId = _uiState.value.activeTabId
        // A page whose content process died keeps a stale bridge port and never answers
        // again. Rebuild the session instead of waiting out every action's timeout.
        if (pageRuntimes[tabId]?.processGone == true || GeckoBrowserRuntime.isBridgeStale(active.session)) {
            return recoverActiveRuntime()
        }
        // A delegated turn may select this persisted session before its GeckoView is
        // mounted. Attach/reload here so DOM actions get the same ready bridge as the UI.
        return try {
            GeckoBrowserRuntime.attach(context, active.session, reloadIfNeeded = active.currentUrl() != "about:blank")
            active
        } catch (_: GeckoBrowserRuntime.BridgeUnrecoverable) {
            recoverActiveRuntime()
        }
    }
    /**
     * Replaces the active tab's dead GeckoSession with a fresh one and reloads the page it
     * was showing, so the agent's next action continues where the reclaimed process stopped.
     */
    private suspend fun recoverActiveRuntime(): AndroidBrowserController {
        val tabId = _uiState.value.activeTabId
        val restoreUrl = _uiState.value.tabs.firstOrNull { it.id == tabId }?.url
            ?.takeIf { it.isNotBlank() && it != "about:blank" }
        android.util.Log.w("AmayaBrowser", "rebuilding reclaimed browser session tab=$tabId urlHost=${Uri.parse(restoreUrl).host.orEmpty()}")
        val rebuilt = withContext(Dispatchers.Main.immediate) {
            tabId?.let(::discardRuntimeOnMain)
            ensureSharedControllerOnMain().also { attachHeadlessSurfaceOnMain() }.second
        }
        _uiState.update { it.copy(currentAction = "Restoring reclaimed browser page") }
        GeckoBrowserRuntime.attach(context, rebuilt.session, reloadIfNeeded = false)
        if (restoreUrl != null) rebuilt.openUrl(restoreUrl)
        return rebuilt
    }
    private fun discardRuntimeOnMain(tabId: String) {
        val runtime = pageRuntimes.remove(tabId) ?: return
        runtime.display?.let { display ->
            runCatching { display.surfaceDestroyed() }
            runCatching { runtime.session.releaseDisplay(display) }
        }
        runtime.imageReader?.close()
        runtime.display = null
        runtime.imageReader = null
        GeckoBrowserRuntime.detach(runtime.session)
        runCatching { runtime.session.close() }
        if (controller === runtime.controller) controller = null
    }
internal fun createTab(url: String?) {
        if (_uiState.value.tabs.size >= 8) {
            val removable = _uiState.value.tabs.firstOrNull { it.id != _uiState.value.activeTabId }
            removable?.let { old -> pageRuntimes.remove(old.id)?.let { it.controller.resetToBlank(); GeckoBrowserRuntime.detach(it.session); it.session.close() } }
            _uiState.update { it.copy(tabs = it.tabs.filterNot { tab -> tab.id == removable?.id }) }
        }
        val tab = BrowserPageTab(title = "New Page", url = url ?: "about:blank")
        _uiState.update { it.copy(tabs = it.tabs + tab, activeTabId = tab.id) }
    }
internal suspend fun switchTab(@Suppress("UNUSED_PARAMETER") current: AndroidBrowserController, pageId: String?): BrowserToolResponse {
        val target = _uiState.value.tabs.firstOrNull { it.id == pageId }
            ?: return BrowserToolResponse.Failure("Tab not found: ${pageId ?: "missing page_id"}")
        _uiState.update { it.copy(activeTabId = target.id, activeUrl = target.url, activeTitle = target.title) }
        val targetRuntime = withContext(Dispatchers.Main.immediate) {
            ensureSharedControllerOnMain().also {
                it.second.session.setActive(true)
                it.second.session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
                attachHeadlessSurfaceOnMain()
            }
        }
        // A background GeckoSession may have lost its bridge while its page stayed
        // resident. Attach with recovery enabled; otherwise the next DOM action waits
        // 15 seconds on a dead port.
        GeckoBrowserRuntime.attach(context, targetRuntime.second.session, reloadIfNeeded = true)
        return if (targetRuntime.second.currentUrl() == "about:blank" && target.url != "about:blank") {
            targetRuntime.second.openUrl(target.url).also { updateTabAfterNavigation(targetRuntime.second) }
        } else {
            updateTabAfterNavigation(targetRuntime.second)
            BrowserToolResponse.Success("Switched to ${targetRuntime.second.currentUrl()}", targetRuntime.second.currentMetadata())
        }
    }
internal suspend fun closeActiveTab(controller: AndroidBrowserController): BrowserToolResponse {
        val state = _uiState.value
        val active = state.activeTabId
        val remaining = state.tabs.filterNot { it.id == active }
        return if (remaining.isEmpty()) {
            pageRuntimes.remove(active)?.let { it.controller.resetToBlank(); GeckoBrowserRuntime.detach(it.session); it.session.close() }
            val newTab = BrowserPageTab()
            _uiState.update { it.copy(tabs = listOf(newTab), activeTabId = newTab.id, activeUrl = "about:blank", activeTitle = "New Page") }
            val runtime = withContext(Dispatchers.Main.immediate) {
                ensureSharedControllerOnMain().also { if (!visibleHost) attachHeadlessSurfaceOnMain() }
            }
            GeckoBrowserRuntime.attach(context, runtime.second.session, reloadIfNeeded = false)
            runtime.second.closePage()
        } else {
            val next = remaining.last()
            pageRuntimes.remove(active)?.let { it.controller.resetToBlank(); GeckoBrowserRuntime.detach(it.session); it.session.close() }
            _uiState.update { it.copy(tabs = remaining, activeTabId = next.id, activeUrl = next.url, activeTitle = next.title) }
            val nextRuntime = withContext(Dispatchers.Main.immediate) {
                ensureSharedControllerOnMain().also { if (!visibleHost) attachHeadlessSurfaceOnMain() }
            }
            GeckoBrowserRuntime.attach(context, nextRuntime.second.session, reloadIfNeeded = false)
            nextRuntime.second.openUrl(next.url).also { updateTabAfterNavigation(nextRuntime.second) }
        }
    }
internal suspend fun domBackedFailure(controller: AndroidBrowserController, message: String): BrowserToolResponse.Failure {
        val dom = controller.getDom()
        val metadata = if (dom is BrowserToolResponse.Success) mapOf("dom" to (dom.metadata["dom"] ?: dom.output)) else emptyMap()
        return BrowserToolResponse.Failure(message, recoverable = true, metadata = metadata)
    }
internal suspend fun updateTabAfterNavigation(controller: AndroidBrowserController) {
        withContext(Dispatchers.Main.immediate) {
            val metadata = controller.currentMetadata()
            val tabId = _uiState.value.activeTabId ?: return@withContext
            onNavigationChanged(
                tabId,
                controller.currentUrl(),
                controller.currentTitle(),
                1f,
                metadata["can_go_back"] == true,
                metadata["can_go_forward"] == true
            )
        }
    }

    private fun cancelAction(): BrowserToolResponse {
        cancelFromUser()
        return BrowserToolResponse.Success("Browser action cancelled")
    }
    internal fun appendLog(toolName: String, args: String, status: String, message: String) {
        flushAssistantStreamBuffer()
        _uiState.update { state ->
            val existingRunningIndex = state.logs.indexOfFirst {
                it.toolName == toolName &&
                    it.argumentsPreview == args &&
                    it.status == "running" &&
                    status != "running"
            }
            // Snapshot accumulated assistant text as a separate log entry
            // before adding a new tool, creating natural textâ†”tool interleaving.
            val streamSnap = state.assistantStreamText.trim()
            val snapshotLog = if (streamSnap.isNotBlank() && existingRunningIndex < 0 && status == "running") {
                val isThinking = hasOpenThinkingTag(streamSnap)
                listOf(BrowserToolLog(
                    toolName = if (isThinking) "thinking" else "assistant",
                    argumentsPreview = "",
                    status = if (isThinking) "running" else "completed",
                    message = streamSnap,
                    timestamp = System.currentTimeMillis() - 1
                ))
            } else emptyList()
            val updatedLogs = if (existingRunningIndex >= 0) {
                state.logs.toMutableList().also { logs ->
                    val current = logs[existingRunningIndex]
                    logs[existingRunningIndex] = current.copy(status = status, message = message)
                }
            } else {
                listOf(BrowserToolLog(toolName = toolName, argumentsPreview = args, status = status, message = message)) + snapshotLog + state.logs
            }
            state.copy(
                logs = updatedLogs.take(120),
                // Clear the stream so subsequent text becomes a new segment
                assistantStreamText = if (snapshotLog.isNotEmpty()) "" else state.assistantStreamText,
                assistantStreamUpdatedAt = if (snapshotLog.isNotEmpty()) 0L else state.assistantStreamUpdatedAt
            )
        }
    }
    internal fun readableAction(toolName: String): String = toolName.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    companion object {
        internal const val UPLOAD_CHUNK_BYTES = 192 * 1024
        private const val HEADLESS_SLOT_WAIT_MS = 5_000L
    }
    private fun selectorArg(arguments: Map<String, Any?>): String? = firstString(arguments, "element_id", "target", "selector", "query", "id")
    private fun queryArg(arguments: Map<String, Any?>): String? = firstString(arguments, "query", "text", "label", "name", "target", "selector", "element_id")
    private fun firstString(arguments: Map<String, Any?>, vararg keys: String): String? {
        keys.forEach { key ->
            val value = arguments[key]?.toString()?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
    private fun intArg(arguments: Map<String, Any?>, key: String, default: Int): Int = (arguments[key] as? Number)?.toInt() ?: arguments[key]?.toString()?.toIntOrNull() ?: default
    private fun longArg(arguments: Map<String, Any?>, key: String, default: Long): Long = (arguments[key] as? Number)?.toLong() ?: arguments[key]?.toString()?.toLongOrNull() ?: default
    private fun boolArg(arguments: Map<String, Any?>, key: String, default: Boolean): Boolean = arguments[key] as? Boolean ?: arguments[key]?.toString()?.toBooleanStrictOrNull() ?: default
    private fun floatArg(arguments: Map<String, Any?>, key: String, default: Float): Float = (arguments[key] as? Number)?.toFloat() ?: arguments[key]?.toString()?.toFloatOrNull() ?: default
}
