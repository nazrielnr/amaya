package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.amaya.intelligence.domain.models.ToolInfoIcon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.UiMessage

// ── ThinkingCard ─────────────────────────────────────────────────────────────
//
// Single, dedicated renderer for model reasoning/thinking. Mirrors the
// ToolCallCard block UX exactly (header pill + ">" + shimmering label +
// status icon + expand arrow, and the same expand/shrink motion for the body)
// but is decoupled from the tool/approval machinery so reasoning never rides
// on ToolExecution.
//
// One card instance = one contiguous reasoning segment. Callers decide whether
// to show a single accumulated segment (the reasoning_content stream) or split
// interleaved <think> blocks from the content body via MessageThinkingBlock.

// Collapse/expand timing reuses ToolCallCard's ToolCallMotion +
// ToolCallAnimatedSection so the motion is byte-for-byte identical to a tool
// block. The lead icon reuses ToolLeadIconPill with a fixed non-green tint
// (iOS blue) so reasoning never reads as a "success/tool" at a glance.
private fun formatThinkingDuration(durationMs: Long): String =
    formatCompactDuration(durationMs, minimumSeconds = if (durationMs > 0L) 1L else 0L)

/** Two-state machine driving the auto-collapse rule for ThinkingCard.
 *  PROCESSING = reasoning segment still in progress (body visible),
 *  DONE = reasoning finished and the body should default to collapsed.
 *  Transitions are one-way (PROCESSING → DONE) and latched: the first
 *  isStreaming=false after tokens were arriving freezes the segment as
 *  DONE, so transient isStreaming blips (reasoning interleaved with
 *  text/tool calls) can't re-collapse the body. Seeded from isStreaming
 *  at mount so historical messages start DONE without a mount animation. */
internal enum class ThinkingLifecycle { PROCESSING, DONE }

/**
 * @param text        The reasoning text for this segment.
 * @param isStreaming  True while reasoning tokens are still arriving for
 *                    this segment. Drives the one-way PROCESSING → DONE
 *                    latch: the first false after tokens were arriving
 *                    freezes the segment as done and auto-collapses once.
 *                    Visuals (shimmer, label, status icon) follow the
 *                    latched lifecycle, not this flag, so transient blips
 *                    (interleaved reasoning + text/tools) don't flicker
 *                    the card or re-trigger collapse.
 * @param startedAt   Optional epoch-ms when this segment began. Used as the
 *                    fallback for the duration label when [durationMs] is
 *                    not persisted.
 * @param completedAt Optional epoch-ms when this segment finished.
 * @param durationMs  Optional persisted duration in ms. Preferred over a
 *                    recomputation from startedAt/completedAt so the label
 *                    stays correct across reloads.
 */
@Composable
fun ThinkingCard(
    text: String,
    isStreaming: Boolean,
    startedAt: Long? = null,
    completedAt: Long? = null,
    durationMs: Long? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    val isDark = isSystemInDarkTheme()

    // ── Lifecycle: PROCESSING → DONE (latched) ──────────────────────
    //
    // isStreaming here is the stable turn-active signal threaded in by
    // MessageThinkingBlock (completedAt == null for the field segment;
    // turnActive && tag-open for inline <think> blocks) — NOT the blippy
    // message.isThinking. Local's isThinking flips false on every
    // TextDelta/ToolCallStart mid-turn (finalizeThinkingIfActive), so an
    // earlier version that derived lifecycle straight from isStreaming
    // collapsed the body mid-stream the instant answer text began — while
    // the mount-expand animation was still playing, so the enter
    // animation reversed and the collapse read as forced/snapped instead
    // of smooth like a manual card press. With a stable turn-active
    // signal the body stays expanded for the whole turn.
    //
    // The latch fires PROCESSING → DONE exactly once, when isStreaming
    // first goes false (turn end for the field segment, tag-close for an
    // inline block). It is seeded from isStreaming at mount: a live segment
    // starts PROCESSING; a historical/reloaded segment starts DONE. Only a
    // real lifecycle transition may trigger the spring exit.
    var lifecycle by remember {
        mutableStateOf(if (isStreaming) ThinkingLifecycle.PROCESSING else ThinkingLifecycle.DONE)
    }
    LaunchedEffect(isStreaming) {
        if (!isStreaming && lifecycle == ThinkingLifecycle.PROCESSING) {
            lifecycle = ThinkingLifecycle.DONE
        }
    }

    // User-controlled override. PROCESSING → body visible, DONE → body
    // hidden. A click on the header toggles userExpanded; the
    // PROCESSING → DONE transition re-arms collapse (auto-collapse).
    // Never auto-expand — opening a finished block is a user gesture.
    // remember() is keyed on nothing because the caller wraps each segment
    // in a stable key(); keying on text.hashCode() reset this on every
    // streaming token, collapsing any user expansion immediately.
    var userExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        if (lifecycle == ThinkingLifecycle.DONE) userExpanded = false
    }
    val bodyVisible = lifecycle == ThinkingLifecycle.PROCESSING || userExpanded
    val isProcessing = lifecycle == ThinkingLifecycle.PROCESSING

    // Standard lead-icon tint matching tool blocks (Material primary). Do
    // NOT override the lead tint — reasoning should look like a tool block,
    // just without the success/tool approval machinery. The status icon keeps
    // the same running/done colour convention as tool blocks.
    val leadTint = MaterialTheme.colorScheme.primary
    val iosBlue = Color(0xFF007AFF)
    val iosGreen = Color(0xFF34C759)
    // Parent block tint while processing matches ToolCallCard RUNNING
    // tint (iosBlue at low alpha) so reasoning reads as a live timeline
    // event. The lead pill itself uses [leadTint] so we don't paint
    // reasoning as a success/tool; only the block-level tone becomes blue
    // while the segment is still processing, matching ToolCallCard exactly.
    // Tied to the latched [isProcessing] (not isStreaming) so a transient
    // isStreaming blip doesn't flicker the tint.
    val bgColor = if (isProcessing) {
        if (isDark) iosBlue.copy(alpha = 0.08f) else iosBlue.copy(alpha = 0.04f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val statusColor = if (isProcessing) iosBlue else iosGreen
    val statusIcon = Icons.Default.Autorenew
    val blockBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val bodyColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

    val canExpand = text.isNotBlank()
    // Kept independent of streaming text so a user scroll position never resets
    // when the next token recomposes this card.
    val bodyScrollState = rememberScrollState()
    val maxBodyHeight = toolCardBodyMaxHeight()
    val fadeHeight = 24.dp
    val followLatest = remember { mutableStateOf(true) }
    LaunchedEffect(isProcessing, followLatest.value) {
        if (!isProcessing || !followLatest.value) return@LaunchedEffect
        snapshotFlow { bodyScrollState.maxValue }.collect { maxValue ->
            bodyScrollState.scrollTo(maxValue)
        }
    }
    val bodyScrollableState = rememberScrollableState { delta ->
        followLatest.value = false
        bodyScrollState.dispatchRawDelta(-delta)
        if (!bodyScrollState.canScrollForward) followLatest.value = true
        // Claim the full delta, including at either edge. This preserves native
        // fling inside the block without handing residual momentum to the chat.
        delta
    }

    // Label mapping: processing → "Thinking", done → "Thought for {duration}".
    // Follows the latched lifecycle so the label is stable across isStreaming
    // blips (a blip must not flip "Thought for Xs" back to "Thinking").
    val headerText = if (isProcessing) {
        "Thinking"
    } else {
        val ms = durationMs
            ?: startedAt?.let { ((completedAt ?: System.currentTimeMillis()) - it).coerceAtLeast(0L) }
        ms?.let { "Thought for ${formatThinkingDuration(it)}" } ?: "Thought"
    }

    val shimmerProgress = rememberToolShimmerProgress(isProcessing)

    // Same mount fade as a tool card, gated the same way: a segment that is live when
    // its card first appears has something to animate; one read back from history, or
    // re-composed because a collapsed work card was re-opened, does not.
    val animateMount = remember { isStreaming }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = toolCardBorder(),
        modifier = modifier.fillMaxWidth().mountFade(animateMount)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header row (same layout as ToolCallCard) ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canExpand) Modifier.clickable { userExpanded = !userExpanded } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolLeadIconPill(icon = ToolInfoIcon.BRAIN)

                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )

                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .toolHeaderShimmer(isProcessing, shimmerProgress)
                        .toolHeaderFade()
                )

                // Status icon — only while processing (spinner). Hide once done.
                if (isProcessing) {
                    Icon(statusIcon, null, modifier = Modifier.size(14.dp), tint = statusColor)
                }

                if (canExpand) {
                    Icon(
                        if (bodyVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // A live card starts visible, so only explicit expand/collapse
            // transitions animate; streaming text no longer restarts an enter motion.
            ToolCallAnimatedSection(visible = bodyVisible, initiallyVisible = isProcessing) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bodyColor,
                    border = BorderStroke(1.dp, blockBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxBodyHeight)
                            .scrollable(
                                state = bodyScrollableState,
                                orientation = Orientation.Vertical
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(bodyScrollState, enabled = false)
                        ) {
                            MarkdownText(
                                text = text,
                                color = MaterialTheme.colorScheme.onSurface,
                                compact = true,
                                modifier = Modifier.padding(10.dp),
                                onLocalhostLinkClick = onLocalhostLinkClick
                            )
                        }
                        if (bodyScrollState.canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(fadeHeight)
                                    .background(Brush.verticalGradient(listOf(bodyColor, bodyColor.copy(alpha = 0f))))
                            )
                        }
                        if (bodyScrollState.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(fadeHeight)
                                    .background(Brush.verticalGradient(listOf(bodyColor.copy(alpha = 0f), bodyColor)))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Segmentation: one source of truth for every thinking source ─────────────
//
// Two sources feed reasoning into the UI:
//   1. message.thinking  — accumulated from provider reasoning_content /
//      ThinkingDelta events (DeepSeek/vLLM/GLM/Kimi/Anthropic/Gemini/OpenAI
//      Responses). Inline <think> tags are stripped out of the content body
//      at the provider layer and routed here.
//   2. inline <think> tags still present in message.content / step text —
//      happens for providers that don't strip (raw Ollama passthrough).
//
// We merge both into an ordered list of segments and render each through a
// single ThinkingCard, so there is exactly one thinking renderer in the app.

internal data class ThinkingSegment(
    val text: String,
    val isStreaming: Boolean
)

internal data class ThinkingTextPart(
    val text: String,
    val isThinking: Boolean,
    val isOpen: Boolean = false
)

internal fun parseThinkingTags(raw: String): List<ThinkingTextPart> {
    if (raw.isBlank()) return emptyList()
    val parts = mutableListOf<ThinkingTextPart>()
    var cursor = 0
    val tagRegex = Regex("</?think>", RegexOption.IGNORE_CASE)
    var inThinking = false

    tagRegex.findAll(raw).forEach { match ->
        raw.substring(cursor, match.range.first).takeIf { it.isNotBlank() }?.let {
            parts += ThinkingTextPart(it.trim(), isThinking = inThinking)
        }
        inThinking = !match.value.startsWith("</", ignoreCase = true)
        cursor = match.range.last + 1
    }

    raw.substring(cursor).takeIf { it.isNotBlank() }?.let {
        parts += ThinkingTextPart(it.trim(), isThinking = inThinking, isOpen = inThinking)
    }
    return parts.ifEmpty { listOf(ThinkingTextPart(raw, isThinking = false)) }
}

/** Strip <think> tags, keeping only visible text. Used where a caller needs the
 *  pure answer body (e.g. work-summary line counts). */
fun stripThinkingTags(raw: String): String = parseThinkingTags(raw)
    .filterNot { it.isThinking }
    .joinToString("\n\n") { it.text }
    .ifBlank { raw.replace(Regex("</?think>", RegexOption.IGNORE_CASE), "").trim() }

/**
 * Build the ordered list of thinking segments for a message: first the
 * accumulated [UiMessage.thinking] field (the reasoning_content stream), then
 * any inline <think> blocks still embedded in [content].
 *
 * [fieldStreaming] drives the field segment: true while reasoning tokens are
 * still arriving, false once reasoning is finalised. The caller also treats
 * ordinary visible text or a real tool call as terminal fallback signals.
 *
 * [inlineStreaming] drives inline <think> blocks still embedded in content
 * (providers that don't strip tags). Live only while the whole message is
 * still streaming AND the tag is still open, so a closed </think> block
 * collapses immediately even mid-turn.
 */
internal fun collectThinkingSegments(
    thinkingField: String?,
    content: String,
    fieldStreaming: Boolean,
    inlineStreaming: Boolean
): List<ThinkingSegment> {
    val segments = mutableListOf<ThinkingSegment>()

    thinkingField?.takeIf { it.isNotBlank() }?.let {
        segments += ThinkingSegment(text = it.trim(), isStreaming = fieldStreaming)
    }

    parseThinkingTags(content)
        .filter { it.isThinking && it.text.isNotBlank() }
        .forEach { part ->
            segments += ThinkingSegment(text = part.text, isStreaming = inlineStreaming && part.isOpen)
        }

    return segments
}

/**
 * Render all thinking for a message as one stacked column of ThinkingCards.
 * Use at the top of the assistant bubble so reasoning always shows — in both
 * the steps (completed) and non-steps (streaming) branches. Single source of
 * truth: merges the accumulated reasoning field with any inline <think> tags
 * still embedded in the visible content.
 *
 * @param hideWhenDuplicate Suppress rendering when the previous assistant
 *                          message already showed the same reasoning.
 */
@Composable
fun MessageThinkingBlock(
    message: UiMessage,
    hideWhenDuplicate: Boolean = false,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (hideWhenDuplicate) return
    val content = message.formattedContent ?: message.content
    val completedAt = message.metadata["completedAt"]?.toLongOrNull()
    val turnActive = completedAt == null
    val segments = collectThinkingSegments(
        thinkingField = message.thinking,
        content = content,
        fieldStreaming = message.isThinking,
        inlineStreaming = turnActive
    )
    if (segments.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEachIndexed { index, segment ->
            // Stable key: index scoped to this message. Keying on the text
            // hash remounted the card on every streaming token (text grows
            // → hash changes → dispose/recreate), which reset
            // ToolCallAnimatedSection's visibilityState and re-ran the expand
            // animation each token — the visible "auto-collapse repeatedly
            // during streaming". A stable key keeps one card instance per
            // segment for the whole turn, matching ToolCallCard's
            // remember(execution.toolCallId) contract.
            key("thinking_${message.id}_$index") {
                ThinkingCard(
                    text = segment.text,
                    isStreaming = segment.isStreaming,
                    startedAt = message.thinkingStartedAt,
                    completedAt = completedAt,
                    durationMs = message.thinkingDurationMs,
                    onLocalhostLinkClick = onLocalhostLinkClick
                )
            }
        }
    }
}
