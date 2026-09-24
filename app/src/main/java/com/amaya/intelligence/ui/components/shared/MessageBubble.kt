package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.remote.api.MessageRole
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: UiMessage,
    hideThinkingHeader: Boolean = false,
    onToolAccept: ((ToolExecution) -> Unit)? = null,
    onToolDecline: ((ToolExecution) -> Unit)? = null,
    onClarify: ((ToolExecution, String?) -> Unit)? = null,
    onCopyMessage: ((String) -> Unit)? = null,
    onEditUserMessage: ((id: String, content: String) -> Unit)? = null,
    onResendUserMessage: ((id: String) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    // Host continuations are provider input, never transcript content. Hide legacy persisted
    // copies too; older builds accidentally materialized this SYSTEM prompt as a blue user bubble.
    if (message.metadata["internalContinuation"] == "true") return
    val conversationEvent = message.conversationEvent()
    if (conversationEvent != null) {
        SharedConversationEvent(conversationEvent)
        return
    }
    val isUser = message.role == MessageRole.USER
    if (isUser) {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val maxBubbleWidthDp = (screenWidth * 0.75f).dp
        val hPad = 14.dp
        val vPad = 10.dp

        var userMenuOpen by remember(message.id) { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { userMenuOpen = true }),
            horizontalAlignment = Alignment.End
        ) {
            // Display image attachments
            if (message.attachments.isNotEmpty()) {
                val imageAttachments = message.attachments.filter { it.mimeType.startsWith("image/") }
                if (imageAttachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidthDp)
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        imageAttachments.forEach { attachment ->
                            val bitmap = remember(attachment.dataBase64) {
                                try {
                                    val bytes = Base64.decode(attachment.dataBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) { null }
                            }
                            
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = attachment.fileName.ifBlank { "Attached image" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }
            
            val delegationSource = message.metadata["sourceAgentName"]
                ?.takeIf { message.metadata["delegation"] == "incoming" }
            val bubbleColor = if (delegationSource == null) Color(0xFF0A84FF) else Color(0xFF6D5BD0)
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(21.dp, 21.dp, 6.dp, 21.dp),
                modifier = Modifier.widthIn(max = maxBubbleWidthDp)
            ) {
                Column {
                    if (delegationSource != null) {
                        Text(
                            text = "Delegation from $delegationSource",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = hPad, top = 9.dp, end = hPad, bottom = 7.dp)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.22f), thickness = 1.dp)
                    }
                    MarkdownText(
                        text = message.formattedContent ?: message.content,
                        color = Color.White,
                        modifier = Modifier.padding(
                            start = hPad,
                            top = if (delegationSource == null) vPad else 9.dp,
                            end = hPad,
                            bottom = if (message.isEdited) 2.dp else vPad
                        ),
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    if (message.isEdited) {
                        Text(
                            text = "edited",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = hPad, bottom = 6.dp)
                        )
                    }
                }
            }
            // Quick actions: always-visible icons, so copy/edit/resend don't depend on discovering the
            // long-press menu. Both entry points invoke the same callbacks wired by the chat host.
            Row(
                modifier = Modifier.padding(top = 4.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageQuickAction(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy message",
                    onClick = onCopyMessage?.let { callback ->
                        { callback(message.formattedContent ?: message.content) }
                    }
                )
                MessageQuickAction(
                    icon = Icons.Default.Edit,
                    label = "Edit message",
                    onClick = onEditUserMessage?.let { callback ->
                        { callback(message.id, message.formattedContent ?: message.content) }
                    }
                )
                MessageQuickAction(
                    icon = Icons.Default.Refresh,
                    label = "Resend message",
                    onClick = onResendUserMessage?.let { callback ->
                        { callback(message.id) }
                    }
                )
            }
            DropdownMenu(
                expanded = userMenuOpen,
                onDismissRequest = { userMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        userMenuOpen = false
                        onCopyMessage?.invoke(message.formattedContent ?: message.content)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        userMenuOpen = false
                        onEditUserMessage?.invoke(message.id, message.formattedContent ?: message.content)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Resend") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = {
                        userMenuOpen = false
                        onResendUserMessage?.invoke(message.id)
                    }
                )
            }
        }
        }
    } else {
        var assistantMenuOpen by remember(message.id) { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { assistantMenuOpen = true })
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (message.steps.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // The split is deliberately independent of whether the turn has
                        // finished. It used to be gated on completedAt, which meant the
                        // whole live timeline was destroyed and replaced by a collapsed
                        // summary card in a single frame the moment the turn ended — an
                        // unmount can't play an exit animation, so the work "just
                        // disappeared" instead of folding away. Now the same card exists
                        // for the whole turn and completion changes exactly one thing:
                        // its expanded flag.
                        val split = remember(message.steps, message.metadata) {
                            splitAssistantTurn(message)
                        }
                        if (split.wrapInSummary) {
                            WorkSummaryCard(message = message, steps = split.workSteps) {
                                StepTimeline(
                                    steps = split.workSteps,
                                    hideThinkingHeader = hideThinkingHeader,
                                    onToolAccept = onToolAccept,
                                    onToolDecline = onToolDecline,
                                    onClarify = onClarify,
                                    onLocalhostLinkClick = onLocalhostLinkClick,
                                    insideCard = true
                                )
                            }
                            if (split.answerSteps.isNotEmpty()) {
                                split.answerSteps.forEach { answerStep ->
                                    val answerText = if (
                                        message.metadata["source"].equals("remote", ignoreCase = true) &&
                                        message.content.isNotBlank() &&
                                        split.answerSteps.size == 1
                                    ) {
                                        message.formattedContent ?: message.content
                                    } else {
                                        answerStep.formattedContent ?: answerStep.content
                                    }
                                    if (answerText.isNotBlank()) {
                                        key(answerStep.id) {
                                            AssistantTextWithThinking(
                                                text = answerText,
                                                onLocalhostLinkClick = onLocalhostLinkClick
                                            )
                                        }
                                    }
                                }
                            } else if (message.content.isNotBlank()) {
                                val fallbackText = message.formattedContent ?: message.content
                                if (fallbackText.isNotBlank()) {
                                    AssistantTextWithThinking(
                                        text = fallbackText,
                                        onLocalhostLinkClick = onLocalhostLinkClick
                                    )
                                }
                            }
                            split.trailingEvents.forEach { eventStep ->
                                key(eventStep.id) { SharedConversationEvent(eventStep.event) }
                            }
                        } else {
                            // Field reasoning is legacy fallback only. New
                            // reasoning lives in ordered MessageStep.Thinking.
                            if (message.steps.none { it is MessageStep.Thinking }) {
                                MessageThinkingBlock(
                                    message = message,
                                    hideWhenDuplicate = hideThinkingHeader,
                                    onLocalhostLinkClick = onLocalhostLinkClick
                                )
                            }
                            StepTimeline(
                                steps = message.steps,
                                hideThinkingHeader = hideThinkingHeader,
                                onToolAccept = onToolAccept,
                                onToolDecline = onToolDecline,
                                onClarify = onClarify,
                                onLocalhostLinkClick = onLocalhostLinkClick
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (message.toolExecutions.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val visibleExecutions = message.toolExecutions.filter { it.name != "update_todo" }
                                val groups = buildToolExecutionGroups(
                                    visibleExecutions.map { MessageStep.ToolCall(execution = it) }
                                )

                                visibleExecutions.forEach { execution ->
                                    val group = groups.find {
                                        it.executions.contains(execution) || it.parentToolCallId == execution.toolCallId
                                    }
                                    when {
                                        group != null && (
                                            execution == group.executions.first() || group.parentToolCallId == execution.toolCallId
                                        ) -> {
                                            key(group.key + "_" + execution.toolCallId) {
                                                ToolExecutionGroupCard(
                                                    group = group,
                                                    onToolAccept = onToolAccept,
                                                    onToolDecline = onToolDecline,
                                                    onClarify = onClarify,
                                                    onLocalhostLinkClick = onLocalhostLinkClick
                                                )
                                            }
                                        }
                                        group != null -> Unit // Skip other group members
                                        else -> {
                                            key(execution.toolCallId) {
                                                ToolCallCard(
                                                    execution = execution,
                                                    onAccept = onToolAccept?.let { callback -> { callback(execution) } },
                                                    onDecline = onToolDecline?.let { callback -> { callback(execution) } },
                                                    onClarify = onClarify?.let { callback -> { answer -> callback(execution, answer) } },
                                                    onLocalhostLinkClick = onLocalhostLinkClick
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Reasoning accumulated from provider thinking deltas (e.g.
                        // DeepSeek reasoning_content). Rendered via the dedicated
                        // ThinkingCard — single source of truth for both
                        // reasoning_delta and inline <think> tags.
                        MessageThinkingBlock(
                            message = message,
                            hideWhenDuplicate = hideThinkingHeader,
                            onLocalhostLinkClick = onLocalhostLinkClick
                        )

                        if (message.content.isNotBlank()) {
                            val content = message.formattedContent ?: message.content
                            AssistantTextWithThinking(
                                text = content,
                                onLocalhostLinkClick = onLocalhostLinkClick
                            )
                        }
                    }
                }
                // Quick actions for assistant responses (same callbacks as the long-press menu).
                // Rendered inside the content column so they flow below the message instead of
                // overlaying it (a Box sibling would sit on top of the first card).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MessageQuickAction(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy response",
                        onClick = onCopyMessage?.let { callback ->
                            { callback(message.formattedContent ?: message.content) }
                        }
                    )
                    if (message.content.isNotBlank()) {
                        MessageQuickAction(
                            icon = Icons.Default.Refresh,
                            label = "Regenerate response",
                            onClick = onRegenerate
                        )
                    }
                }
            }
        }
            DropdownMenu(
                expanded = assistantMenuOpen,
                onDismissRequest = { assistantMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        assistantMenuOpen = false
                        onCopyMessage?.invoke(message.formattedContent ?: message.content)
                    }
                )
                if (message.content.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Regenerate response") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            assistantMenuOpen = false
                            onRegenerate?.invoke()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageQuickAction(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?
) {
    if (onClick == null) return
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(30.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StepTimeline(
    steps: List<MessageStep>,
    hideThinkingHeader: Boolean,
    onToolAccept: ((ToolExecution) -> Unit)?,
    onToolDecline: ((ToolExecution) -> Unit)?,
    onClarify: ((ToolExecution, String?) -> Unit)?,
    onLocalhostLinkClick: ((String) -> Unit)?,
    /**
     * True when this timeline is rendered inside the work-summary card. Tool and
     * thinking blocks already sit in the card's tight vertical rhythm; a bare text step
     * did not, so it kept the conversation's 12dp block gaps and read noticeably looser
     * than the blocks either side of it. Type size is unaffected.
     */
    insideCard: Boolean = false
) {
    val groups = buildToolExecutionGroups(steps)

    steps.forEachIndexed { idx, step ->
        when (step) {
            is MessageStep.Thinking -> {
                key(step.id) {
                    ThinkingCard(
                        text = step.text,
                        isStreaming = step.isStreaming,
                        startedAt = step.startedAt,
                        durationMs = step.durationMs,
                        onLocalhostLinkClick = onLocalhostLinkClick
                    )
                }
            }
            is MessageStep.Event -> {
                SharedConversationEvent(step.event)
            }
            is MessageStep.ToolCall -> {
                when {
                    step.execution.name == "update_todo" -> Unit
                    isThinkingExecution(step.execution) -> {
                        key(step.execution.toolCallId) {
                            ThinkingCard(
                                text = step.execution.result.orEmpty(),
                                isStreaming = step.execution.status == ToolStatus.RUNNING
                            )
                        }
                    }
                    else -> {
                        val group = groups.find {
                            it.executions.contains(step.execution) || it.parentToolCallId == step.execution.toolCallId
                        }

                        when {
                            group != null && (
                                step.execution == group.executions.first() || group.parentToolCallId == step.execution.toolCallId
                            ) -> {
                                key(group.key + "_" + step.execution.toolCallId) {
                                    ToolExecutionGroupCard(
                                        group = group,
                                        onToolAccept = onToolAccept,
                                        onToolDecline = onToolDecline,
                                        onClarify = onClarify,
                                        onLocalhostLinkClick = onLocalhostLinkClick
                                    )
                                }
                            }
                            group != null -> Unit // Skip other group members
                            else -> {
                                key(step.execution.toolCallId) {
                                    ToolCallCard(
                                        execution = step.execution,
                                        onAccept = onToolAccept?.let { callback -> { callback(step.execution) } },
                                        onDecline = onToolDecline?.let { callback -> { callback(step.execution) } },
                                        onClarify = onClarify?.let { callback -> { answer -> callback(step.execution, answer) } },
                                        onLocalhostLinkClick = onLocalhostLinkClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is MessageStep.Text -> {
                val textContent = step.formattedContent ?: step.content
                if (textContent.isNotBlank()) {
                    key(step.id) {
                        AssistantTextWithThinking(
                            text = textContent,
                            onLocalhostLinkClick = onLocalhostLinkClick,
                            // Same type size as anywhere else — only the vertical gaps
                            // tighten to the card's rhythm.
                            blockSpacing = if (insideCard) 4.dp else null
                        )
                    }
                }
            }
        }
    }
}

/**
 * The turn's work, wrapped in a card that folds shut when the turn ends.
 *
 * The collapse is a one-way latch modelled on [ThinkingLifecycle], seeded from the
 * turn's state at mount: a live turn starts open and springs closed exactly once when
 * `completedAt` lands, while a turn read back from history starts closed with no
 * animation at all. A manual tap after that is never overridden.
 */
@Composable
private fun WorkSummaryCard(
    message: UiMessage,
    steps: List<MessageStep>,
    content: @Composable ColumnScope.() -> Unit
) {
    if (steps.isEmpty()) return
    val completedAt = message.metadata["completedAt"]?.toLongOrNull()
    val isLive = completedAt == null

    var expanded by remember(message.id) { mutableStateOf(isLive) }
    var collapsedOnce by remember(message.id) { mutableStateOf(!isLive) }
    LaunchedEffect(isLive) {
        if (!isLive && !collapsedOnce) {
            collapsedOnce = true
            expanded = false
        }
    }

    // Height cap on the body while the turn runs.
    //
    // Deliberately *not* just `isLive`: it stays on through the collapse at turn end, so
    // the exit shrinks from the capped height instead of snapping to full first. It is
    // dropped only when the user opens the finished card by hand, which is when they
    // actually want the whole timeline.
    var bounded by remember(message.id) { mutableStateOf(isLive) }
    LaunchedEffect(isLive) { if (isLive) bounded = true }

    val toolCount = steps.count {
        it is MessageStep.ToolCall && it.execution.name != "update_todo" && !isThinkingExecution(it.execution)
    }
    val toolSuffix = if (toolCount > 0) " · $toolCount tool${if (toolCount == 1) "" else "s"}" else ""
    val subtitle = if (isLive) {
        "Working$toolSuffix"
    } else {
        "Worked for ${formatWorkedDuration(message.timestamp, completedAt)}$toolSuffix"
    }
    val shimmerProgress = rememberToolShimmerProgress(isLive)

    val shape = RoundedCornerShape(14.dp)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    // Same live/idle tone convention as ToolCallCard and ThinkingCard.
    val iosBlue = Color(0xFF007AFF)
    val background by animateColorAsState(
        if (isLive) {
            if (isDark) iosBlue.copy(alpha = 0.08f) else iosBlue.copy(alpha = 0.04f)
        } else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "work_summary_background"
    )
    Surface(
        shape = shape,
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        // Same mount fade as the cards it holds — this card appears mid-turn, the moment
        // the first tool or reasoning step lands, so it is as much a new event as they are.
        modifier = Modifier.fillMaxWidth().clip(shape).mountFade(remember(message.id) { isLive })
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable {
                        // Opening a finished card by hand means "show me everything".
                        if (!expanded && !isLive) bounded = false
                        expanded = !expanded
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolLeadIconPill(ToolInfoIcon.TASK)
                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .toolHeaderShimmer(isLive, shimmerProgress)
                        .toolHeaderFade()
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            ToolCallAnimatedSection(visible = expanded, initiallyVisible = isLive) {
                Column {
                    HorizontalDivider(
                        color = borderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                    )
                    // While the turn runs the body is pinned to a maximum height and
                    // scrolls internally, sticking to the newest event. Past that height
                    // the card stops growing altogether, so a tool or reasoning card
                    // arriving slides into view inside the card instead of pushing the
                    // answer text below it down the screen.
                    ToolFollowingScrollBlock(
                        fadeColor = background.compositeOver(MaterialTheme.colorScheme.background),
                        maxHeight = workCardLiveBodyMaxHeight(),
                        bounded = bounded,
                        // Every new tool or reasoning event re-arms the follow, the same
                        // way a new message re-arms the chat list's.
                        followKey = steps.size,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/**
 * How an assistant turn is divided between the work card and the answer below it.
 *
 * [workSteps] are the ordered tool/reasoning events; [answerStep] is the trailing text
 * step, rendered outside the card so the answer stays put when the work folds away.
 * The rule is the same during and after the turn, which is what lets completion be a
 * pure state change rather than a re-layout.
 */
internal data class AssistantTurnSplit(
    val workSteps: List<MessageStep>,
    val answerSteps: List<MessageStep.Text>,
    val trailingEvents: List<MessageStep.Event>,
    val wrapInSummary: Boolean
)

internal fun isHousekeepingTool(name: String): Boolean {
    return name in setOf(
        "memory_manage", "update_memory", "agent_memory", "agent_memory_internal",
        "skill_manage", "skill_view", "update_todo", "recommendation_manage", "session_search"
    )
}

internal fun splitAssistantTurn(message: UiMessage): AssistantTurnSplit {
    val steps = message.steps
    val isRemote = message.metadata["source"].equals("remote", ignoreCase = true)
    val browserRanges = browserToolRanges(steps)

    // All valid non-blank Text steps outside browser ranges
    val candidateTextIndices = steps.indices.filter { index ->
        val text = steps[index] as? MessageStep.Text ?: return@filter false
        (text.formattedContent ?: text.content).isNotBlank() && browserRanges.none { index in it }
    }

    // Index of the last substantive (non-housekeeping) work tool
    val lastActionToolIndex = steps.indexOfLast { step ->
        step is MessageStep.ToolCall && !isThinkingExecution(step.execution) && !isHousekeepingTool(step.execution.name)
    }

    // Determine which text steps constitute the final answer:
    // 1. Text steps that occur AFTER the last substantive work tool are post-work answer steps.
    // 2. If there are multiple post-work text steps (e.g. substantive answer + trailing memory/note),
    //    all post-work text steps are surfaced in answerSteps so no substantive text is hidden in the work card.
    // 3. If there are no text steps after the last action tool, fall back to the last candidate text step.
    val answerIndices: Set<Int> = if (candidateTextIndices.isEmpty()) {
        emptySet()
    } else if (lastActionToolIndex < 0) {
        candidateTextIndices.toSet()
    } else {
        val postWorkTextIndices = candidateTextIndices.filter { it > lastActionToolIndex }
        if (postWorkTextIndices.isNotEmpty()) {
            postWorkTextIndices.toSet()
        } else {
            setOf(candidateTextIndices.last())
        }
    }

    val answerSteps = answerIndices.sorted().mapNotNull { steps[it] as? MessageStep.Text }
    val firstAnswerIndex = answerIndices.minOrNull()
    val trailingEvents = if (firstAnswerIndex != null) {
        steps.drop(firstAnswerIndex + 1).filterIsInstance<MessageStep.Event>()
    } else emptyList()

    val workSteps = steps.filterIndexed { index, step ->
        index !in answerIndices && when (step) {
            is MessageStep.Thinking -> true
            is MessageStep.ToolCall -> step.execution.name != "update_todo"
            // An event after the answer is a conversation-level update, not work belonging
            // to the completed answer. Keeping it outside avoids a one-event work card.
            is MessageStep.Event -> firstAnswerIndex == null || index < firstAnswerIndex
            // Remote keeps only tools and reasoning inside the card; local preserves its
            // interleaved intermediate text so the timeline still reads in order.
            is MessageStep.Text -> !isRemote && (step.formattedContent ?: step.content).isNotBlank()
        }
    }

    // A turn of plain text or trailing events is not "work" — don't hide an answer behind
    // a summary header that contains only a separator.
    val wrapInSummary = workSteps.any {
        it is MessageStep.Thinking || it is MessageStep.ToolCall
    }
    return AssistantTurnSplit(workSteps, answerSteps, trailingEvents, wrapInSummary)
}

private fun browserToolRanges(steps: List<MessageStep>): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var currentStart: Int? = null
    steps.forEachIndexed { index, step ->
        val isBrowser = (step as? MessageStep.ToolCall)?.execution?.name == "browser"
        if (isBrowser) {
            val start = currentStart
            if (start != null && index > start) {
                ranges += (start + 1) until index
            }
            currentStart = index
        } else if (step is MessageStep.ToolCall && !isThinkingExecution(step.execution)) {
            currentStart = null
        }
    }
    return ranges
}

private fun isThinkingExecution(execution: ToolExecution): Boolean {
    return execution.name.equals("thinking", ignoreCase = true) ||
        execution.metadata["syntheticThinking"].equals("true", ignoreCase = true) ||
        execution.metadata["thinkingTool"].equals("true", ignoreCase = true)
}

private fun formatWorkedDuration(startedAt: Long, completedAt: Long?): String =
    formatCompactDuration((completedAt ?: System.currentTimeMillis()) - startedAt, minimumSeconds = 1L)

/**
 * Render the visible (non-thinking) answer body. Thinking is intentionally
 * NOT rendered here — it is owned by [MessageThinkingBlock] at message level
 * so there is a single source of truth. Any inline <think> tags still present
 * in providers that do not strip them are dropped here to avoid duplication.
 */
@Composable
fun AssistantTextWithThinking(
    text: String,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * Tightens the gap between markdown blocks without changing type size. Set for text
     * sitting *inside* a card, where its vertical rhythm has to match the tool and
     * thinking blocks around it rather than the conversation outside.
     */
    blockSpacing: Dp? = null
) {
    val visible = remember(text) { stripThinkingTags(text) }
    if (visible.isBlank()) return
    MarkdownText(
        text = visible,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
        onLocalhostLinkClick = onLocalhostLinkClick,
        blockSpacing = blockSpacing
    )
}


