package com.amaya.intelligence.impl.local.browser

import com.amaya.intelligence.tools.ToolExecutionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

internal suspend fun BrowserConversationSession.executeBrowserTask(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext = ToolExecutionContext()
    ): String = executionMutex.withLock {
        val restoreKey = visibleConversationKey
        val restoreAgentId = visibleAgentId
        var needsHeadlessSurface = false
        try {
        if (executionContext.assistantMode != com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
            return@withLock JSONObject().put("status", "error").put("error", "Browser is available only in Agent mode").toString(2)
        }
        if (executionContext.agentCapabilityProfile?.browser == false) {
            return@withLock JSONObject().put("status", "error").put("error", "Browser capability is disabled for this Agent").toString(2)
        }
        if (executionContext.agentId == null) {
            return@withLock JSONObject().put("status", "error").put("error", "Browser requires an active Agent").toString(2)
        }
        val contextKey = executionContext.conversationId?.let { "conversation:$it" }
            ?: "agent:${executionContext.agentId}"
        workspacePath = executionContext.workspacePath?.takeIf(String::isNotBlank) ?: workspacePath
        if (conversationKey != contextKey || _uiState.value.agentId != executionContext.agentId) {
            resetForConversation(contextKey, executionContext.agentId)
        }
        _uiState.update { it.copy(browserAccessActive = true) }
        needsHeadlessSurface = !visibleHost
        withContext(Dispatchers.Main.immediate) {
            ensureSharedControllerOnMain()
            if (needsHeadlessSurface) attachHeadlessSurfaceOnMain()
        }
        val callId = executionContext.toolCallId
            ?: "browser_task_${UUID.randomUUID().toString().take(8)}"
        val resumeStep = pendingApprovalStepIndex.takeIf {
            executionContext.confirmed && pendingApprovalCallId == callId
        }
        val reset = resumeStep == null && (arguments["reset_task"] == true || parentSubToolcalls.isEmpty())
        if (reset) {
            parentTaskId = callId
            parentStartedAt = BrowserResponseFormatter.nowIso()
            parentSubToolcalls.clear()
            pendingApprovalCallId = null
            pendingApprovalStepIndex = null
        }
        arguments["task"]?.toString()?.takeIf { it.isNotBlank() }?.let { parentSummary = it.take(160) }
        val steps = parseSteps(arguments)
        val totalSteps = steps.size.coerceAtLeast(1)
        var lastLabel = "Preparing browser"
        steps.forEachIndexed { index, step ->
            if (resumeStep != null && index < resumeStep) return@forEachIndexed
            val action = normalizeActionName(step.first)
            val params = if (executionContext.confirmed) step.second + ("__confirmed" to true) else step.second
            lastLabel = readableAction(action)
            val sub = executeNormalizedSubtool(
                parentCallId = parentTaskId,
                action = action,
                params = params,
                currentStep = index + 1,
                totalSteps = totalSteps
            )
            parentSubToolcalls += sub
            if (sub.optJSONObject("error")?.optString("code") == "USER_APPROVAL_REQUIRED") {
                pendingApprovalCallId = callId
                pendingApprovalStepIndex = index
            } else if (resumeStep == index) {
                pendingApprovalCallId = null
                pendingApprovalStepIndex = null
            }
            if (sub.optString("status") in setOf("paused", "cancelled", "error", "timeout")) {
                return parentTaskJsonWithSiteMemory(lastLabel, index + 1, totalSteps).toString(2)
            }
        }
        parentTaskJsonWithSiteMemory(lastLabel, totalSteps, totalSteps).toString(2)
        } finally {
            // The offscreen display stays attached between tool calls on purpose. An agent
            // thinks for seconds between browser actions, and a detached session is reclaimed
            // by Android within a few seconds of the app leaving the foreground.
            if (needsHeadlessSurface && visibleHost) withContext(Dispatchers.Main.immediate) { detachHeadlessSurface() }
            if (restoreKey != null && restoreKey != conversationKey) {
                resetForConversation(restoreKey, restoreAgentId)
            }
        }
    }
private suspend fun BrowserConversationSession.executeNormalizedSubtool(
        parentCallId: String,
        action: String,
        params: Map<String, Any?>,
        currentStep: Int,
        totalSteps: Int
    ): JSONObject {
        if (!visibleHost) withContext(Dispatchers.Main.immediate) { attachHeadlessSurfaceOnMain() }
        hydrateRestoredPageIfNeeded(action)
        val started = System.currentTimeMillis()
        val internalName = actionToInternalTool(action)
        _uiState.update { it.copy(progress = currentStep.toFloat() / totalSteps.toFloat(), currentAction = readableAction(action)) }
        // Scheme G: capture a pre-action page fingerprint for mutation actions so a silent no-op
        // (wrong selector, JS-blocked click, unsubmitted form) is reported to the model explicitly
        // instead of being swallowed as a success.
        val verifyMutation = internalName in VERIFY_MUTATION_TOOLS
        val beforeFingerprint = if (verifyMutation) pageFingerprint() else null
        val rawResponse = when (action) {
            "get_status" -> BrowserToolResponse.Success("Browser status read", currentSessionMetadata())
            "analyze_page" -> execute("get_dom", params)
            else -> execute(internalName, params)
        }
        val response = if (verifyMutation && rawResponse is BrowserToolResponse.Success) {
            attachActionVerification(rawResponse, beforeFingerprint)
        } else rawResponse
        val duration = System.currentTimeMillis() - started
        return BrowserResponseFormatter.subToolResponse(
            id = BrowserResponseFormatter.newCallId(),
            parentCallId = parentCallId,
            tool = action,
            status = BrowserResponseFormatter.successStatus(response),
            durationMs = duration,
            session = sessionJson(),
            requestParams = params,
            result = BrowserResponseFormatter.resultFor(action, response),
            safety = BrowserResponseFormatter.safetyFor(response),
            summary = BrowserResponseFormatter.summaryFor(action, response),
            agentStatus = BrowserResponseFormatter.agentStatusFor(response),
            error = BrowserResponseFormatter.errorFor(action, response, _uiState.value.activeUrl, params)
        )
    }
/** Scheme G: mutation actions whose silent no-op is worth detecting via page fingerprint. */
private val VERIFY_MUTATION_TOOLS = setOf(
    "click_element", "tap", "type_text", "press_key", "select_option", "clear_input", "search", "focus", "swipe"
)

/** Scheme G: cheap page fingerprint before/after a mutation action (see DomInspector.getFingerprintScript). */
private suspend fun BrowserConversationSession.pageFingerprint(): String? = try {
    when (val resp = execute("page_fingerprint", emptyMap())) {
        is BrowserToolResponse.Success -> resp.output.take(400)
        else -> null
    }
} catch (e: Exception) {
    null
}

/**
 * Scheme G: if the page fingerprint is unchanged after a successful mutation, the action very
 * likely did not take effect — surface that explicitly instead of a bare success. A missing or
 * unreadable fingerprint (page still loading, headless hiccup) skips verification rather than
 * reporting a false positive.
 */
private suspend fun BrowserConversationSession.attachActionVerification(
    response: BrowserToolResponse.Success,
    before: String?
): BrowserToolResponse {
    if (before == null) return response
    val after = pageFingerprint()
    return if (after == null || after != before) {
        response
    } else {
        BrowserToolResponse.Success(
            output = response.output + "\n\n[verification] No observable page change after this action (URL, title, visible content, and focus state are all unchanged). The action may not have taken effect — verify the target selector/state and retry if needed.",
            metadata = response.metadata + ("verification" to "no_observable_change")
        )
    }
}

private fun BrowserConversationSession.parentSnapshot(label: String, currentStep: Int, totalSteps: Int): JSONObject {
        val lastStatus = parentSubToolcalls.lastOrNull()?.optString("status") ?: "running"
        val parentStatus = when (lastStatus) {
            "paused" -> "paused"
            "cancelled" -> "cancelled"
            "error", "timeout" -> "error"
            else -> if (currentStep >= totalSteps) "completed" else "running"
        }
        return BrowserResponseFormatter.parentTask(
            id = parentTaskId,
            summary = parentSummary,
            status = parentStatus,
            sessionId = sessionId,
            browserId = browserId,
            activePageId = _uiState.value.activeTabId.orEmpty(),
            activeUrl = _uiState.value.activeUrl,
            startedAt = parentStartedAt,
            updatedAt = BrowserResponseFormatter.nowIso(),
            currentStep = currentStep,
            totalSteps = totalSteps,
            label = label,
            subToolcalls = parentSubToolcalls.takeLast(12)
        )
    }

    /**
     * Parent task snapshot enriched with site memory: saved workspace facts that mention the
     * active site's host are attached to the browser result, so the agent can reuse previously
     * learned knowledge about that site (login flows, known selectors, recurring patterns)
     * instead of re-learning it every session.
     */
    private suspend fun BrowserConversationSession.parentTaskJsonWithSiteMemory(label: String, currentStep: Int, totalSteps: Int): JSONObject {
        val snapshot = parentSnapshot(label, currentStep, totalSteps)
        val host = siteHostFromActiveUrl(_uiState.value.activeUrl)
        if (host != null) {
            val facts = memoryRepository.listMemoryRecords(
                type = com.amaya.intelligence.domain.memory.MemoryType.WORKSPACE_FACT,
                query = host,
                limit = 4,
                workspacePath = workspacePath
            )
            if (facts.isNotEmpty()) {
                snapshot.put("site", host)
                snapshot.put("site_memory", org.json.JSONArray(facts.map { "- ${it.title}: ${it.content}" }))
            }
        }
        return snapshot
    }

    private fun BrowserConversationSession.siteHostFromActiveUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()
    }
private fun BrowserConversationSession.parseSteps(arguments: Map<String, Any?>): List<Pair<String, Map<String, Any?>>> {
        val rawSteps = arguments["steps"]
        if (rawSteps is Iterable<*>) {
            return rawSteps.mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val action = map["action"]?.toString() ?: return@mapNotNull null
                val params = (map["params"] as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to it.value }
                    ?: map.filterKeys { it != "action" && it != "params" }.entries.associate { it.key.toString() to it.value }
                action to params
            }.ifEmpty { listOf("get_status" to emptyMap()) }
        }
        val action = normalizeActionName(arguments["action"]?.toString() ?: "get_status")
        val topLevelParams = arguments.filterKeys { it !in setOf("task", "action", "params", "steps", "reset_task", "debug", "parent_call_id") }
        val nestedParams = (arguments["params"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        return listOf(action to (topLevelParams + nestedParams))
    }
private fun BrowserConversationSession.normalizeActionName(raw: String): String {
        val name = raw.removePrefix("browser.").trim()
        return when (name) {
            "observe", "analyze_page", "get_snapshot" -> "get_dom"
            "get_content" -> "get_visible_text"
            "wait_for_selector" -> "wait_for_element"
            "wait_for_nav", "wait_for_navigation" -> "wait_for_navigation"
            "list_pages" -> "get_status"
            "switch_page" -> "switch_tab"
            "type" -> "type_text"
            "click_element" -> "click"
            "scroll_page", "swipe" -> "scroll"
            "get_screenshot" -> "screenshot"
            "reload_page" -> "reload"
            "new_page" -> "new_tab"
            "close_page" -> "close_tab"
            "expression", "run_js", "run_javascript" -> "evaluate"
            else -> name
        }
    }
private fun BrowserConversationSession.actionToInternalTool(action: String): String = when (action) {
        "new_tab" -> "new_page"
        "close_tab" -> "close_page"
        "list_pages" -> "get_status"
        "switch_page" -> "switch_tab"
        "wait_for_selector" -> "wait_for_element"
        "wait_for_nav" -> "wait_for_navigation"
        "get_content" -> "get_visible_text"
        "click" -> "click_element"
        "scroll" -> "scroll_page"
        "screenshot" -> "get_screenshot"
        "reload" -> "reload_page"
        "switch_tab" -> "switch_tab"
        else -> action
    }
private fun BrowserConversationSession.sessionJson(): JSONObject = JSONObject().apply {
        put("session_id", sessionId)
        put("browser_id", browserId)
        put("active_page_id", _uiState.value.activeTabId ?: "")
        put("pages", BrowserResponseFormatter.toJsonValue(pageList()))
    }
private fun BrowserConversationSession.pageList(): List<Map<String, Any>> = _uiState.value.tabs.map { tab -> mapOf(
        "page_id" to tab.id,
        "active" to (tab.id == _uiState.value.activeTabId),
        "title" to tab.title,
        "url" to tab.url,
        "can_go_back" to tab.canGoBack,
        "can_go_forward" to tab.canGoForward
    ) }
private fun BrowserConversationSession.currentSessionMetadata(): Map<String, Any> = mapOf(
        "url" to _uiState.value.activeUrl,
        "title" to _uiState.value.activeTitle,
        "session_id" to sessionId,
        "browser_id" to browserId,
        "active_page_id" to (_uiState.value.activeTabId ?: ""),
        "pages" to pageList(),
        "status" to _uiState.value.status.name.lowercase()
    )
