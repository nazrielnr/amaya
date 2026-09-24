package com.amaya.intelligence.impl.local.browser

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import org.mozilla.geckoview.GeckoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal suspend fun AndroidBrowserController.click(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val beforeUrl = currentUrl()
        val beforeTitle = currentTitle()
        val beforeState = readElementState(selector)
        val beforeExternalResponse = externalResponseVersion
        val beforeFilePrompt = filePromptVersion
        val beforePopup = popupVersion
        val isFileInput = beforeState?.optString("type")?.equals("file", ignoreCase = true) == true
        val preflight = runCatching { JSONObject(evaluateJson(DomInspector.clickPreflightScript(selector))) }.getOrNull()
            ?: return@withContext BrowserToolResponse.Failure("Unexpected browser response during click preflight")

        if (!preflight.optBoolean("ok", false)) {
            val directHref = preflight.optString("href").takeIf { it.isNotBlank() && !isEmbeddedAppRoute(it) }
            return@withContext if (directHref != null) {
                directOpenHref(directHref, beforeUrl, beforeTitle, "preflight_direct_open")
            } else {
                val metadata = buildMap<String, Any> {
                    preflight.opt("element")?.takeIf { it != JSONObject.NULL }?.let { put("element", it.toString()) }
                    preflight.opt("blocker")?.takeIf { it != JSONObject.NULL }?.let { put("blocker", it.toString()) }
                }
                BrowserToolResponse.Failure(preflight.optString("error", "Click preflight failed"), metadata = metadata)
            }
        }

        val click = preflight.optJSONObject("click")
        val x = click?.optDouble("x")?.toFloat() ?: Float.NaN
        val y = click?.optDouble("y")?.toFloat() ?: Float.NaN
        val directHref = preflight.optString("href").takeIf { it.isNotBlank() && !isEmbeddedAppRoute(it) }
        val covered = preflight.optBoolean("covered", false)
        val inViewport = preflight.optBoolean("in_viewport", false)

        if (isFileInput && !covered && inViewport && x.isFinite() && y.isFinite()) {
            val tapX = x
            val tapY = y
            if (isPointInsideView(tapX, tapY)) dispatchTap(tapX, tapY)
            if (filePromptVersion == beforeFilePrompt) evaluateJson(DomInspector.fileInputClickScript(selector))
            repeat(30) {
                if (filePromptVersion != beforeFilePrompt) return@withContext BrowserToolResponse.Failure(
                    "File selection pending",
                    metadata = mapOf("upload_required" to true)
                )
                delay(50)
            }
            // Native taps may miss a headless file input. Fall through to the
            // DOM click path before reporting failure.
        }

        if (!isFileInput && !covered && inViewport && x.isFinite() && y.isFinite() && !preflight.optBoolean("submits_form") && !preflight.optString("tag").equals("button", ignoreCase = true)) {
            val tapX = x
            val tapY = y
            if (isPointInsideView(tapX, tapY)) {
                dispatchTap(tapX, tapY)
                val pageChanged = try {
                    waitForInteractionSettle(beforeUrl, beforeTitle)
                } catch (error: IllegalStateException) {
                    if (error.message == "Browser document navigated") true
                    else return@withContext BrowserToolResponse.Failure("Click failed: ${error.message ?: "unknown error"}")
                }
                delay(180)
                val effectObserved = pageChanged || externalResponseVersion != beforeExternalResponse || filePromptVersion != beforeFilePrompt || hasClickEffect(beforeState, readElementState(selector))
                if (popupVersion != beforePopup) return@withContext BrowserToolResponse.Failure(
                    "Popup blocked; click outcome requires inspection",
                    metadata = clickOutcomeMetadata(beforeUrl, beforeTitle, pageChanged, beforePopup, popupVersion, true)
                )
                if (effectObserved) {
                    suppressSoftKeyboard()
                    if (isFileInput && filePromptVersion != beforeFilePrompt) return@withContext BrowserToolResponse.Failure(
                        "File selection pending",
                        metadata = mapOf("upload_required" to true)
                    )
                    return@withContext BrowserToolResponse.Success(
                        "Clicked element; page_changed=$pageChanged",
                        currentMetadata() + mapOf(
                            "page_changed" to pageChanged,
                            "before_url" to beforeUrl,
                            "before_title" to beforeTitle,
                            "after_url" to currentUrl(),
                            "after_title" to currentTitle(),
                            "x" to tapX,
                            "y" to tapY,
                            "strategy" to "native_tap"
                        ) + clickOutcomeMetadata(beforeUrl, beforeTitle, pageChanged, beforePopup, popupVersion, effectObserved)
                    )
                }
            }
        }

        val json = if (isFileInput) evaluateJson(DomInspector.fileInputClickScript(selector)) else evaluateJson(DomInspector.clickScript(selector))
        if (isFileInput) {
            repeat(30) {
                if (filePromptVersion != beforeFilePrompt) return@withContext BrowserToolResponse.Failure(
                    "File selection pending",
                    metadata = mapOf("upload_required" to true)
                )
                delay(50)
            }
            return@withContext BrowserToolResponse.Failure(
                "File chooser was not opened",
                metadata = mapOf("upload_required" to true)
            )
        }
        waitForInteractionSettle(beforeUrl, beforeTitle)
        delay(120)
        when (val response = jsOkResponse(json, "Clicked element")) {
            is BrowserToolResponse.Success -> {
                val afterUrl = currentUrl()
                val afterTitle = currentTitle()
                val pageChanged = beforeUrl != afterUrl || beforeTitle != afterTitle
                val effectObserved = pageChanged || hasClickEffect(beforeState, readElementState(selector))
                if (popupVersion != beforePopup) return@withContext BrowserToolResponse.Failure(
                    "Popup blocked; click outcome requires inspection",
                    metadata = clickOutcomeMetadata(beforeUrl, beforeTitle, pageChanged, beforePopup, popupVersion, true)
                )
                if (effectObserved) {
                    suppressSoftKeyboard()
                    if (isFileInput && filePromptVersion != beforeFilePrompt) return@withContext BrowserToolResponse.Failure(
                    "File selection pending",
                    metadata = mapOf("upload_required" to true)
                )
                response.copy(
                        output = "Clicked element; page_changed=$pageChanged",
                        metadata = response.metadata + mapOf(
                            "page_changed" to pageChanged,
                            "before_url" to beforeUrl,
                            "before_title" to beforeTitle,
                            "after_url" to afterUrl,
                            "after_title" to afterTitle,
                            "strategy" to "dom_click_fallback"
                        ) + clickOutcomeMetadata(beforeUrl, beforeTitle, pageChanged, beforePopup, popupVersion, effectObserved)
                    )
                } else if (directHref != null) {
                    directOpenHref(directHref, beforeUrl, beforeTitle, "dom_click_direct_open")
                } else {
                    suppressSoftKeyboard()
                    if (isFileInput) BrowserToolResponse.Failure(
                        "File selection pending",
                        recoverable = true,
                        metadata = mapOf("upload_required" to true)
                    ) else BrowserToolResponse.Failure("Click completed but no visible effect was observed")
                }
            }
            is BrowserToolResponse.Failure -> {
                if (directHref != null) {
                    directOpenHref(directHref, beforeUrl, beforeTitle, if (covered) "covered_direct_open" else "fallback_direct_open")
                } else {
                    suppressSoftKeyboard()
                    response
                }
            }
            else -> response
        }
    }

internal suspend fun AndroidBrowserController.focus(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        resolveCenterPoint(selector)?.let { onAgentTouch(it.first, it.second) }
        val json = evaluateJson(DomInspector.focusScript(selector))
        suppressSoftKeyboard()
        jsOkResponse(json, "Focused element")
    }

internal suspend fun AndroidBrowserController.typeText(selector: String?, text: String, append: Boolean): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        selector?.let { resolveCenterPoint(it)?.let { point -> onAgentTouch(point.first, point.second) } }
        val before = readElementState(selector)
        var json = evaluateJson(DomInspector.typeScript(selector, text, append))
        waitForDomReady(900)
        var response = jsOkResponse(json, "Typed ${text.length} characters")
        var after = readElementState(selector)
        var verified = textApplied(before, after, text, append)
        if (response is BrowserToolResponse.Success && !verified) {
            response = nativeTypeText(selector, text, append)
            waitForDomReady(900)
            after = readElementState(selector)
            verified = textApplied(before, after, text, append)
        }
        suppressSoftKeyboard()
        when (response) {
            is BrowserToolResponse.Success -> if (verified) response.copy(
                metadata = response.metadata + mapOf(
                    "verified" to true,
                    "before_state" to (before?.toString() ?: ""),
                    "after_state" to (after?.toString() ?: "")
                )
            ) else BrowserToolResponse.Failure(
                "Text input completed but the page did not reflect the change",
                recoverable = true,
                metadata = mapOf("before_state" to (before?.toString() ?: ""), "after_state" to (after?.toString() ?: ""))
            )
            else -> response
        }
    }

internal suspend fun AndroidBrowserController.pressKey(key: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val normalized = key.trim().uppercase().ifBlank { "ENTER" }
        val keyCode = when (normalized) {
            "ENTER", "RETURN" -> KeyEvent.KEYCODE_ENTER
            "TAB" -> KeyEvent.KEYCODE_TAB
            "BACKSPACE", "DEL", "DELETE" -> KeyEvent.KEYCODE_DEL
            "ESC", "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
            "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> KeyEvent.KEYCODE_ENTER
        }
        val beforeUrl = currentUrl()
        val beforeTitle = currentTitle()
        geckoView.requestFocus()
        val downTime = SystemClock.uptimeMillis()
        geckoView.dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        geckoView.dispatchKeyEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        // Native GeckoView key dispatch is authoritative; avoid a second submit from JS.
        val pageChanged = waitForInteractionSettle(beforeUrl, beforeTitle)
        suppressSoftKeyboard()
        BrowserToolResponse.Success(
            "Pressed key $normalized; page_changed=$pageChanged",
            currentMetadata() + mapOf(
                "key" to normalized,
                "page_changed" to pageChanged,
                "before_url" to beforeUrl,
                "before_title" to beforeTitle,
                "after_url" to currentUrl(),
                "after_title" to currentTitle()
            )
        )
    }

internal suspend fun AndroidBrowserController.tap(x: Int, y: Int, selector: String?): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        // Raw x/y from the AI are CSS viewport pixels (from DOM summary bounds/center).
        // resolveCenterPoint already applies scale, so only scale the raw-coordinate path.
        @Suppress("DEPRECATION")
        val scale = geckoView.resources.displayMetrics.density
        val point = if (x >= 0 && y >= 0) (x.toFloat() * scale) to (y.toFloat() * scale) else selector?.let { resolveCenterPoint(it) }
            ?: return@withContext BrowserToolResponse.Failure("Missing tap coordinates or resolvable element_id/selector")
        val beforeUrl = currentUrl()
        val beforeTitle = currentTitle()
        dispatchTap(point.first, point.second)
        val pageChanged = waitForInteractionSettle(beforeUrl, beforeTitle)
        suppressSoftKeyboard()
        BrowserToolResponse.Success(
            "Tapped at x=${point.first.toInt()} y=${point.second.toInt()}; page_changed=$pageChanged",
            currentMetadata() + mapOf(
                "x" to point.first,
                "y" to point.second,
                "page_changed" to pageChanged,
                "before_url" to beforeUrl,
                "before_title" to beforeTitle,
                "after_url" to currentUrl(),
                "after_title" to currentTitle()
            )
        )
    }

internal suspend fun AndroidBrowserController.swipe(direction: String?, distance: Float, startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val width = geckoView.width.coerceAtLeast(1).toFloat()
        val height = geckoView.height.coerceAtLeast(1).toFloat()
        val normalized = direction?.lowercase()?.trim()
        val clampedDistance = distance.coerceIn(0.16f, 0.55f)
        val verticalTravel = (height * clampedDistance).coerceIn(height * 0.14f, height * 0.42f)
        val horizontalTravel = (width * clampedDistance).coerceIn(width * 0.12f, width * 0.35f)

        val defaultStartX = when (normalized) {
            "left" -> width * 0.72f
            "right" -> width * 0.28f
            else -> width * 0.5f
        }
        val defaultStartY = when (normalized) {
            // direction = page movement, so finger moves opposite like a human swipe.
            "up" -> height * 0.36f
            "down", null, "" -> height * 0.64f
            else -> height * 0.5f
        }

        val sx = if (startX >= 0) startX.toFloat() else defaultStartX
        val sy = if (startY >= 0) startY.toFloat() else defaultStartY
        val ex = if (endX >= 0) endX.toFloat() else when (normalized) {
            "left" -> (sx + horizontalTravel).coerceIn(width * 0.16f, width * 0.84f)
            "right" -> (sx - horizontalTravel).coerceIn(width * 0.16f, width * 0.84f)
            else -> sx
        }
        val ey = if (endY >= 0) endY.toFloat() else when (normalized) {
            "up" -> (sy + verticalTravel).coerceIn(height * 0.18f, height * 0.82f)
            "down", null, "" -> (sy - verticalTravel).coerceIn(height * 0.18f, height * 0.82f)
            else -> sy
        }

        val before = currentScrollSnapshot()
        dispatchSwipe(sx, sy, ex, ey, durationMs.coerceIn(240, 700))
        waitForScrollSettled(900)
        val after = currentScrollSnapshot()
        BrowserToolResponse.Success(
            "Swiped ${normalized ?: "down"} from (${sx.toInt()},${sy.toInt()}) to (${ex.toInt()},${ey.toInt()})",
            currentMetadata() + mapOf(
                "direction" to (normalized ?: "down"),
                "before_scroll" to before,
                "after_scroll" to after,
                "scroll_changed" to (before != after)
            )
        )
    }

internal suspend fun AndroidBrowserController.search(text: String, selector: String?): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val target = selector ?: evaluateJson(DomInspector.findSearchInputScript()).takeIf { it != "null" && it.isNotBlank() }
        if (target == null) return@withContext BrowserToolResponse.Failure("Search input not found")
        resolveCenterPoint(target)?.let { onAgentTouch(it.first, it.second) }
        val focus = jsOkResponse(evaluateJson(DomInspector.focusScript(target)), "Focused search input")
        if (focus is BrowserToolResponse.Failure) return@withContext focus
        val typed = typeText(target, text, false)
        if (typed is BrowserToolResponse.Failure) return@withContext typed
        val submitted = runCatching { submitFromContext(target) }.getOrElse { error ->
            if (error.message == "Browser document navigated") waitForPage(30_000)
            else BrowserToolResponse.Failure("Search submit failed: ${error.message ?: "unknown error"}")
        }
        if (submitted is BrowserToolResponse.Failure && submitted.message == "Browser document navigated") waitForPage(30_000) else submitted
    }

internal suspend fun AndroidBrowserController.clearInput(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        var json = evaluateJson(DomInspector.clearScript(selector))
        waitForDomReady(700)
        var response = jsOkResponse(json, "Cleared input")
        var after = readElementState(selector)
        var cleared = isEffectivelyEmpty(after)
        if (response is BrowserToolResponse.Success && !cleared) {
            json = evaluateJson(DomInspector.clearScript(selector))
            waitForDomReady(700)
            response = jsOkResponse(json, "Cleared input")
            after = readElementState(selector)
            cleared = isEffectivelyEmpty(after)
        }
        suppressSoftKeyboard()
        when (response) {
            is BrowserToolResponse.Success -> if (cleared) response.copy(
                metadata = response.metadata + mapOf(
                    "verified" to true,
                    "after_state" to (after?.toString() ?: "")
                )
            ) else BrowserToolResponse.Failure(
                "Clear input completed but the field is still populated",
                recoverable = true,
                metadata = mapOf("after_state" to (after?.toString() ?: ""))
            )
            else -> response
        }
    }

internal suspend fun AndroidBrowserController.restoreScroll(x: Int, y: Int) = withContext(Dispatchers.Main.immediate) {
        evaluateString("window.scrollTo(${x.coerceIn(-100_000, 100_000)},${y.coerceIn(-100_000, 100_000)})")
    }

internal suspend fun AndroidBrowserController.scrollPage(deltaX: Int, deltaY: Int): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        try {
            scrollPageOnce(deltaX, deltaY)
        } catch (error: IllegalStateException) {
            if (error.message != "Browser document navigated") throw error
            waitForPage(30_000)
            scrollPageOnce(deltaX, deltaY)
        }
    }

internal suspend fun AndroidBrowserController.scrollPageOnce(deltaX: Int, deltaY: Int): BrowserToolResponse {
        val before = currentScrollSnapshot()
        val script = """
            (function() {
              var root = document.scrollingElement || document.documentElement || document.body;
              var target = root;
              var node = document.elementFromPoint(Math.max(1, innerWidth / 2), Math.max(1, innerHeight / 2));
              while (node && node !== document.body) {
                var style = getComputedStyle(node);
                var scrollable = /(auto|scroll)/.test((style.overflow || '') + (style.overflowX || '') + (style.overflowY || ''));
                var canMove = (${deltaY} > 0 && node.scrollTop < node.scrollHeight - node.clientHeight) ||
                  (${deltaY} < 0 && node.scrollTop > 0) || (${deltaX} > 0 && node.scrollLeft < node.scrollWidth - node.clientWidth) ||
                  (${deltaX} < 0 && node.scrollLeft > 0);
                if (scrollable && canMove) { target = node; break; }
                node = node.parentElement;
              }
              var before = {x:target.scrollLeft || 0, y:target.scrollTop || 0, width:target.scrollWidth || 0, height:target.scrollHeight || 0};
              target.scrollBy(${deltaX}, ${deltaY});
              if (target === root) window.scrollTo(root.scrollLeft, root.scrollTop);
              var after = {x:target.scrollLeft || 0, y:target.scrollTop || 0, width:target.scrollWidth || 0, height:target.scrollHeight || 0};
              return JSON.stringify({ok:true, target:target === root ? 'document' : (target.id || target.tagName || 'nested'), before:before, after:after, changed:before.x !== after.x || before.y !== after.y});
            })();
        """.trimIndent()
        val json = evaluateJson(script)
        waitForScrollSettled(900)
        val result = runCatching { JSONObject(json) }.getOrNull()
        val changed = result?.optBoolean("changed") == true
        return BrowserToolResponse.Success(
            "Scrolled page by x=$deltaX y=$deltaY",
            currentMetadata() + mapOf(
                "before_scroll" to before,
                "after_scroll" to currentScrollSnapshot(),
                "scroll_changed" to changed,
                "scroll_result" to json
            )
        )
    }

internal suspend fun AndroidBrowserController.getHtml(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val html = evaluateString("document.documentElement ? document.documentElement.outerHTML : ''")
        BrowserToolResponse.Success(html.take(200_000), currentMetadata() + mapOf("length" to html.length, "truncated" to (html.length > 200_000)))
    }

internal suspend fun AndroidBrowserController.getDom(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val json = evaluateJson(DomInspector.getDomScript(), 15_000)
                val viewport = evaluateString(
                    "JSON.stringify({innerWidth:innerWidth,innerHeight:innerHeight,outerWidth:outerWidth,outerHeight:outerHeight,devicePixelRatio:devicePixelRatio,scrollX:scrollX,scrollY:scrollY,visualViewport:visualViewport?{width:visualViewport.width,height:visualViewport.height,scale:visualViewport.scale,offsetTop:visualViewport.offsetTop,offsetLeft:visualViewport.offsetLeft}:null,documentWidth:document.documentElement?document.documentElement.clientWidth:0,documentHeight:document.documentElement?document.documentElement.clientHeight:0,bodyHeight:document.body?document.body.scrollHeight:0})",
                    5_000
                )
                return@withContext BrowserToolResponse.Success(
                    output = json.take(50_000),
                    metadata = currentMetadata() + mapOf("dom" to json, "viewport" to viewport, "attempt" to attempt + 1)
                )
            } catch (error: Throwable) {
                lastError = error
                if (attempt < 2 && isTransientBridgeError(error)) {
                    GeckoBrowserRuntime.awaitReady(session, 4_000)
                    delay(200L * (attempt + 1))
                } else throw error
            }
        }
        BrowserToolResponse.Failure("DOM unavailable: ${lastError?.message ?: "unknown error"}")
    }

internal suspend fun AndroidBrowserController.hover(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val json = evaluateJson(DomInspector.hoverScript(selector))
        jsOkResponse(json, "Hovered element")
    }

internal suspend fun AndroidBrowserController.selectOption(selector: String, value: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val json = evaluateJson(DomInspector.selectOptionScript(selector, value))
        jsOkResponse(json, "Selected option")
    }

internal suspend fun AndroidBrowserController.getVisibleText(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val text = evaluateString(DomInspector.getVisibleTextScript())
        BrowserToolResponse.Success(text, currentMetadata() + ("length" to text.length))
    }

    /** Scheme G: page fingerprint for post-action verification (see DomInspector.getFingerprintScript). */
    internal suspend fun AndroidBrowserController.pageFingerprint(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val json = runCatching { evaluateString(DomInspector.getFingerprintScript(), timeoutMs = 6_000) }
            .getOrElse { "{}" }
        BrowserToolResponse.Success(json)
    }

internal suspend fun AndroidBrowserController.findElement(query: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val json = evaluateJson(DomInspector.findElementScript(query))
        if (json == "null" || json.isBlank()) {
            BrowserToolResponse.Failure("Element not found for query: $query")
        } else {
            BrowserToolResponse.Success(json, currentMetadata() + ("element" to json))
        }
    }

internal suspend fun AndroidBrowserController.findText(query: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        var json = evaluateJson(DomInspector.findTextScript(query))
        var total = runCatching { JSONObject(json).optInt("total") }.getOrDefault(0)
        repeat(3) {
            if (total > 0) return@withContext BrowserToolResponse.Success(json, currentMetadata() + ("matches" to json))
            delay(180)
            json = evaluateJson(DomInspector.findTextScript(query))
            total = runCatching { JSONObject(json).optInt("total") }.getOrDefault(0)
        }
        BrowserToolResponse.Failure("Text not found: $query")
    }

internal suspend fun AndroidBrowserController.waitForNavigation(timeoutMs: Long): BrowserToolResponse = waitForPage(timeoutMs)

internal suspend fun AndroidBrowserController.waitForElement(query: String, timeoutMs: Long): BrowserToolResponse {
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (cancelled) return BrowserToolResponse.Failure("Action cancelled", recoverable = false)
            val found = if (query.trim().startsWith("#")) {
                val json = evaluateJson(DomInspector.selectorExistsScript(query.trim()))
                if (json == "null" || json.isBlank()) BrowserToolResponse.Failure("Element not found for selector: $query")
                else BrowserToolResponse.Success(json, currentMetadata() + ("element" to json))
            } else findElement(query)
            if (found is BrowserToolResponse.Success) return found.copy(output = "Element appeared: ${found.output}")
            delay(180)
        }
        return BrowserToolResponse.Failure("Timed out waiting for element: $query")
    }

internal suspend fun AndroidBrowserController.submitFromContext(selector: String?): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val beforeUrl = currentUrl()
        val beforeTitle = currentTitle()
        val json = evaluateJson(DomInspector.submitFromContextScript(selector))
        val obj = runCatching { JSONObject(json) }.getOrNull()
            ?: return@withContext BrowserToolResponse.Failure("Unexpected browser response: $json")
        if (!obj.optBoolean("ok", false)) {
            return@withContext BrowserToolResponse.Failure(obj.optString("error", "Submit action failed"))
        }
        val pageChanged = waitForInteractionSettle(beforeUrl, beforeTitle)
        BrowserToolResponse.Success(
            output = "Submitted from context; page_changed=$pageChanged",
            metadata = currentMetadata() + buildMap<String, Any> {
                put("page_changed", pageChanged)
                put("before_url", beforeUrl)
                put("before_title", beforeTitle)
                put("after_url", currentUrl())
                put("after_title", currentTitle())
                put("strategy", obj.optString("strategy", "unknown"))
                put("purpose", obj.optString("purpose", "submit"))
                obj.opt("button")?.takeIf { it != JSONObject.NULL }?.let { put("button", it.toString()) }
                obj.opt("anchor")?.takeIf { it != JSONObject.NULL }?.let { put("anchor", it.toString()) }
            }
        )
    }

    data class FileInputConstraints(val acceptTypes: List<String>, val multiple: Boolean)

internal suspend fun AndroidBrowserController.fileInputConstraints(selector: String): FileInputConstraints? = withContext(Dispatchers.Main.immediate) {
        val raw = evaluateJson(DomInspector.fileInputConstraintsScript(selector))
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
        if (!json.optBoolean("ok", false)) null else FileInputConstraints(
            acceptTypes = json.optJSONArray("accept")?.let { array -> (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank) } ?: emptyList(),
            multiple = json.optBoolean("multiple")
        )
    }

internal suspend fun AndroidBrowserController.beginFileInputAssignment(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        jsOkResponse(evaluateJson(DomInspector.fileInputAssignStartScript(selector)), "Prepared workspace files")
    }

internal suspend fun AndroidBrowserController.appendFileInputChunk(name: String, mime: String, lastModified: Long, chunkBase64: String, first: Boolean): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        jsOkResponse(evaluateJson(DomInspector.fileInputAssignChunkScript(name, mime, lastModified, chunkBase64, first)), "Read workspace file chunk")
    }

internal suspend fun AndroidBrowserController.finishFileInputAssignment(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        jsOkResponse(evaluateJson(DomInspector.fileInputAssignFinishScript()), "Selected workspace files")
    }

internal suspend fun AndroidBrowserController.uploadedFileNames(selector: String): List<String> = withContext(Dispatchers.Main.immediate) {
        val state = readElementState(selector)
        state?.optJSONArray("file_names")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        }.orEmpty()
    }

internal suspend fun AndroidBrowserController.openFileChooser(selector: String): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val obj = runCatching { JSONObject(evaluateJson(DomInspector.boundsScript(selector))) }.getOrNull()
        if (obj?.optBoolean("ok", false) == true) {
            val x = obj.optDouble("center_x").toFloat()
            val y = obj.optDouble("center_y").toFloat()
            if (isPointInsideView(x, y)) dispatchTap(x, y)
        }
        jsOkResponse(evaluateJson(DomInspector.fileInputClickScript(selector)), "File chooser opened")
    }

internal suspend fun AndroidBrowserController.goBack(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        if (!canGoBackValue) return@withContext BrowserToolResponse.Failure("No back history available")
        val beforeUrl = currentUrl()
        val beforeNavigation = navigationGeneration
        cancelled = false
        session.goBack()
        waitForHistoryNavigation(beforeUrl, beforeNavigation)
    }

internal suspend fun AndroidBrowserController.goForward(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        if (!canGoForwardValue) return@withContext BrowserToolResponse.Failure("No forward history available")
        val beforeUrl = currentUrl()
        val beforeNavigation = navigationGeneration
        cancelled = false
        session.goForward()
        waitForHistoryNavigation(beforeUrl, beforeNavigation)
    }

internal suspend fun AndroidBrowserController.reload(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        cancelled = false
        pageFinished = false
        session.reload()
        waitForPage(20_000)
    }

internal suspend fun AndroidBrowserController.evaluate(expression: String, timeoutMs: Long = 10_000): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val source = expression.trim()
        if (source.isBlank()) return@withContext BrowserToolResponse.Failure("Evaluate script is empty")
        val script = """
            (async function() {
              const source = ${JSONObject.quote(source)};
              const timeout = ${timeoutMs.coerceIn(250, 10_000)};
              const value = await Promise.race([
                Promise.resolve().then(() => (0, eval)(source)),
                new Promise((_, reject) => setTimeout(() => reject(new Error('evaluate timeout')), timeout))
              ]);
              if (value === undefined) return JSON.stringify({ok:true,value:null});
              return JSON.stringify({ok:true,value:value});
            })().catch(e => JSON.stringify({ok:false,error:String(e && e.message || e)}))
        """.trimIndent()
        val raw = withTimeout(timeoutMs.coerceIn(250, 10_000) + 500L) { evaluateString(script) }
        val result = runCatching { JSONObject(raw) }.getOrNull()
            ?: return@withContext BrowserToolResponse.Failure("Evaluate returned invalid JSON")
        if (!result.optBoolean("ok", false)) return@withContext BrowserToolResponse.Failure(result.optString("error", "Evaluate failed"))
        val value = result.opt("value")
        if (value is JSONObject || value is org.json.JSONArray) {
            val encoded = value.toString()
            if (encoded.length > 65_536) return@withContext BrowserToolResponse.Failure("Evaluate result exceeds 64KB", recoverable = true)
        }
        val output = JSONObject().put("ok", true).put("value", value ?: JSONObject.NULL).toString()
        if (output.length > 65_536) return@withContext BrowserToolResponse.Failure("Evaluate result exceeds 64KB", recoverable = true)
        BrowserToolResponse.Success("Evaluate completed", currentMetadata() + mapOf("evaluate_result" to output))
    }

internal suspend fun AndroidBrowserController.screenshot(): BrowserToolResponse = withContext(Dispatchers.Main.immediate) {
        val bitmap = geckoResult(capturePixels())
        val width = bitmap.width
        val height = bitmap.height
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        BrowserToolResponse.Success(
            output = "Screenshot captured (${width}x${height})",
            metadata = currentMetadata() + mapOf("image_base64" to b64, "mime_type" to "image/jpeg", "width" to width, "height" to height)
        )
    }

