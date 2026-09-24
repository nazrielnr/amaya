package com.amaya.intelligence.impl.local.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun BrowserConversationSession.executeBrowserTool(toolName: String, arguments: Map<String, Any?>): BrowserToolResponse {
        val controller = ensureController()
        return when (toolName) {
            "open_url" -> {
                val url = arguments["url"]?.toString() ?: return BrowserToolResponse.Failure("Missing url")
                pendingRestoreUrl = null
                controller.openUrl(url, longArg(arguments, "timeout_ms", 30_000)).also { updateTabAfterNavigation(controller) }
            }
            "new_page" -> {
                createTab(arguments["url"]?.toString())
                val runtime = withContext(Dispatchers.Main.immediate) {
                    ensureSharedControllerOnMain().also { attachHeadlessSurfaceOnMain() }
                }
                GeckoBrowserRuntime.attach(context, runtime.second.session, reloadIfNeeded = false)
                runtime.second.newPage(arguments["url"]?.toString()).also { updateTabAfterNavigation(runtime.second) }
            }
            "close_page" -> closeActiveTab(controller)
            "switch_tab" -> switchTab(controller, arguments["page_id"]?.toString() ?: arguments["tab_id"]?.toString())
            "click_element" -> {
                val selector = selectorArg(arguments)
                    ?: return domBackedFailure(controller, "Missing selector/element_id for click. Use element_id from interactive_elements.")
                val clicked = try {
                    controller.click(selector)
                } catch (error: IllegalStateException) {
                    if (error.message == "Browser document navigated") controller.waitForNavigation(30_000)
                    else BrowserToolResponse.Failure("Click failed: ${error.message ?: "unknown error"}")
                }
                clicked.also { updateTabAfterNavigation(controller) }
            }
            "tap" -> controller.tap(
                intArg(arguments, "x", -1),
                intArg(arguments, "y", -1),
                selectorArg(arguments)
            ).also { updateTabAfterNavigation(controller) }
            "swipe" -> controller.swipe(
                arguments["direction"]?.toString(),
                floatArg(arguments, "distance", 0.65f),
                intArg(arguments, "start_x", -1),
                intArg(arguments, "start_y", -1),
                intArg(arguments, "end_x", -1),
                intArg(arguments, "end_y", -1),
                longArg(arguments, "duration_ms", 420)
            )
            "focus" -> {
                val selector = selectorArg(arguments)
                    ?: return domBackedFailure(controller, "Missing selector/element_id for focus. Use element_id from interactive_elements.")
                controller.focus(selector)
            }
            "press_key" -> {
                val key = arguments["key"]?.toString() ?: arguments["text"]?.toString() ?: "ENTER"
                controller.pressKey(key).also { updateTabAfterNavigation(controller) }
            }
            "search" -> {
                val text = arguments["query"]?.toString() ?: arguments["text"]?.toString()
                    ?: return domBackedFailure(controller, "Missing query/text for search.")
                val selector = selectorArg(arguments)
                controller.search(text, selector).also { updateTabAfterNavigation(controller) }
            }
            "type_text" -> {
                val selector = selectorArg(arguments)
                val text = arguments["text"]?.toString() ?: return BrowserToolResponse.Failure("Missing text for type_text")
                // External keyboard focus is valid even when the field is outside the
                // DOM viewport. Omit element_id/selector to type into document.activeElement.
                val typed = controller.typeText(selector, text, boolArg(arguments, "append", true))
                if (typed is BrowserToolResponse.Success && boolArg(arguments, "submit", false)) {
                    controller.submitFromContext(selector).also { updateTabAfterNavigation(controller) }
                } else typed
            }
            "clear_input" -> {
                val selector = selectorArg(arguments)
                    ?: return domBackedFailure(controller, "Missing selector/element_id for clear_input. Use element_id from interactive_elements.")
                controller.clearInput(selector)
            }
            "scroll_page" -> {
                val direction = arguments["direction"]?.toString()?.trim()?.lowercase()
                val distance = when (arguments["amount"]?.toString()?.lowercase()) {
                    "small" -> 420
                    "large" -> 1_400
                    else -> (floatArg(arguments, "distance", 0.28f) * 2_000).toInt().coerceIn(320, 1_600)
                }
                val (deltaX, deltaY) = when (direction) {
                    "left" -> distance to 0
                    "right" -> -distance to 0
                    "up" -> 0 to distance
                    "down" -> 0 to -distance
                    else -> intArg(arguments, "delta_x", 0) to intArg(arguments, "delta_y", 800)
                }
                // DOM scrolling works for headless Gecko sessions; touch coordinates do not.
                controller.scrollPage(deltaX, deltaY)
            }
            "get_dom" -> controller.getDom()
            "get_html" -> controller.getHtml()
            "get_visible_text" -> controller.getVisibleText()
            "page_fingerprint" -> controller.pageFingerprint()
            "get_screenshot" -> controller.screenshot()
            "evaluate" -> {
                val expression = arguments["expression"]?.toString() ?: arguments["script"]?.toString()
                    ?: return BrowserToolResponse.Failure("Missing expression for evaluate")
                controller.evaluate(expression, longArg(arguments, "timeout_ms", 10_000))
            }
            "hover" -> {
                val selector = selectorArg(arguments)
                    ?: return domBackedFailure(controller, "Missing selector/element_id for hover")
                controller.hover(selector)
            }
            "select_option" -> {
                val selector = selectorArg(arguments)
                    ?: return domBackedFailure(controller, "Missing selector/element_id for select_option")
                val value = firstString(arguments, "value", "text", "label")
                    ?: return BrowserToolResponse.Failure("Missing value for select_option")
                controller.selectOption(selector, value)
            }
            "upload_file" -> uploadWorkspaceFiles(controller, arguments)
            "find_element" -> {
                val query = queryArg(arguments)
                if (query == null) {
                    return domBackedFailure(controller, "Missing query for find_element. Call get_dom or provide query/text/label/target.")
                }
                val found = controller.findElement(query)
                if (found is BrowserToolResponse.Failure && found.message.contains("not found", ignoreCase = true)) {
                    domBackedFailure(controller, "Element not found for query: $query")
                } else found
            }
            "find_text" -> {
                val query = queryArg(arguments)
                    ?: return BrowserToolResponse.Failure("Missing query/text for find_text")
                controller.findText(query)
            }
            "wait_for_element" -> {
                val query = queryArg(arguments)
                    ?: return domBackedFailure(controller, "Missing query for wait_for_element. Provide query/text/label/target.")
                val found = controller.waitForElement(query, longArg(arguments, "timeout_ms", 10_000))
                if (found is BrowserToolResponse.Failure && found.message.contains("Timed out", ignoreCase = true)) {
                    domBackedFailure(controller, "Timed out waiting for element: $query")
                } else found
            }
            "wait_for_navigation" -> controller.waitForNavigation(longArg(arguments, "timeout_ms", 30_000)).also { updateTabAfterNavigation(controller) }
            "go_back" -> controller.goBack().also { updateTabAfterNavigation(controller) }
            "go_forward" -> controller.goForward().also { updateTabAfterNavigation(controller) }
            "reload_page" -> controller.reload().also { updateTabAfterNavigation(controller) }
            else -> BrowserToolResponse.Failure("Unknown browser tool: $toolName")
        }
    }
