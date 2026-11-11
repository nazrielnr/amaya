package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource


import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ComposerLayout(
    expansion: Float,
    modifier: Modifier = Modifier,
    attachment: @Composable () -> Unit,
    input: @Composable () -> Unit,
    model: @Composable () -> Unit,
    send: @Composable () -> Unit
) {
    val controlsProgress = expansion
    val inputProgress = expansion
    val modelProgress = expansion

    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - (0.1f * controlsProgress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            ) {
                attachment()
            }
            input()
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = modelProgress
                    val scale = 1f - (0.1f * controlsProgress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
            ) {
                model()
            }
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - (0.1f * controlsProgress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
            ) {
                send()
            }
        }
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val collapsedGap = 12.dp.roundToPx()
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val attachmentPlaceable = measurables[0].measure(looseConstraints)
        val sendPlaceable = measurables[3].measure(looseConstraints)
        val modelPlaceable = measurables[2].measure(
            looseConstraints.copy(
                maxWidth = (constraints.maxWidth - attachmentPlaceable.width - sendPlaceable.width - gap * 2)
                    .coerceAtLeast(0)
            )
        )
        val collapsedLeftInset = attachmentPlaceable.width + collapsedGap
        val collapsedRightInset = sendPlaceable.width + collapsedGap
        val expandedLeftInset = 8.dp.roundToPx()
        val inputLeftInset = (
            collapsedLeftInset + ((expandedLeftInset - collapsedLeftInset) * inputProgress)
        ).roundToInt()
        val inputRightInset = (collapsedRightInset * (1f - inputProgress)).roundToInt()
        val inputWidth = (constraints.maxWidth - inputLeftInset - inputRightInset).coerceAtLeast(0)
        val inputPlaceable = measurables[1].measure(
            constraints.copy(
                minWidth = inputWidth,
                maxWidth = inputWidth,
                minHeight = 0
            )
        )
        val topHeight = maxOf(inputPlaceable.height, attachmentPlaceable.height, sendPlaceable.height)
        val controlsRowY = topHeight + gap
        val controlsRowHeight = maxOf(
            attachmentPlaceable.height,
            modelPlaceable.height,
            sendPlaceable.height
        )
        val expandedHeight = controlsRowY + controlsRowHeight
        val layoutHeight = (
            topHeight + ((expandedHeight - topHeight) * controlsProgress)
        ).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val attachmentStartY = (topHeight - attachmentPlaceable.height) / 2
        val sendStartY = (topHeight - sendPlaceable.height) / 2
        val attachmentY = (
            attachmentStartY + ((controlsRowY - attachmentStartY) * controlsProgress)
        ).roundToInt()
        val sendY = (
            sendStartY + ((controlsRowY - sendStartY) * controlsProgress)
        ).roundToInt()
        val inputY = (topHeight - inputPlaceable.height) / 2
        val visualScale = 1f - (0.1f * controlsProgress)
        val modelRightVisualShift = sendPlaceable.width * (1f - visualScale)
        val modelXOffset = modelRightVisualShift.roundToInt()
        val modelX = constraints.maxWidth - sendPlaceable.width - gap - modelPlaceable.width + modelXOffset
        val modelY = controlsRowY + (controlsRowHeight - modelPlaceable.height) / 2

        layout(constraints.maxWidth, layoutHeight) {
            attachmentPlaceable.placeRelative(0, attachmentY)
            inputPlaceable.placeRelative(inputLeftInset, inputY)
            modelPlaceable.placeRelative(modelX, modelY)
            sendPlaceable.placeRelative(constraints.maxWidth - sendPlaceable.width, sendY)
        }
    }
}

@Composable
internal fun ComposerAttachmentButton(
    isStreaming: Boolean,
    onAttachFile: () -> Unit,
    onAttachImage: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isStreaming) 0.05f else 0.08f))
                .clickable(enabled = !isStreaming) { showMenu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.attach),
                modifier = Modifier.size(23.dp),
                tint = if (isStreaming) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
        // Anchored to the '+' button and forced upwards: the composer sits on the bottom edge,
        // so "below the anchor" never fits and Material's fallback would fling the menu to the
        // bottom of the window, behind the IME.
        AmayaDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            alignment = AmayaMenuAlignment.Start,
            placement = AmayaMenuPlacement.Above,
            focusable = false
        ) {
            AmayaDropdownMenuItem(
                text = stringResource(R.string.attach_file),
                icon = Icons.Default.AttachFile,
                onClick = {
                    showMenu = false
                    onAttachFile()
                }
            )
            if (onAttachImage != null) {
                AmayaDropdownMenuItem(
                    text = stringResource(R.string.attach_image),
                    icon = Icons.Default.Image,
                    onClick = {
                        showMenu = false
                        onAttachImage()
                    }
                )
            }
        }
    }
}

@Composable
internal fun ComposerSendButton(
    isStreaming: Boolean,
    isCompressing: Boolean,
    canSend: Boolean,
    onClick: () -> Unit,
    onEmptyClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isStreaming) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                else if (canSend) Color(0xFF0A84FF)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            .clickable {
                if (isStreaming || canSend) onClick() else onEmptyClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isStreaming) {
            Icon(
                Icons.Default.Stop,
                contentDescription = if (isCompressing) stringResource(R.string.cancel_compression) else stringResource(R.string.stop),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.send),
                modifier = Modifier
                    .size(18.dp)
                    .offset(x = 1.dp), // optical alignment
                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
internal fun CompactProgressPill(modifier: Modifier = Modifier, isDone: Boolean = false, isCanceled: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "compactShimmer")
    val shimmer by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "compactShimmerOffset"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayText = when {
            isCanceled && isDone -> stringResource(R.string.compact_canceled)
            isDone -> stringResource(R.string.compacting_done)
            else -> stringResource(R.string.compacting)
        }
        Text(
            displayText,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val peakX = shimmer * w
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to onSurface.copy(alpha = 0.4f),
                            0.5f to primary,
                            1f to onSurface.copy(alpha = 0.4f),
                            startX = peakX - (w * 0.4f),
                            endX = peakX + (w * 0.4f)
                        ),
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
    }
}

internal class ComposerReferenceTransformation(private val referenceColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val matches = COMPOSER_REFERENCE_LINK.findAll(text.text).toList()
        if (matches.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val rendered = AnnotatedString.Builder()
        val originalToTransformed = IntArray(text.length + 1)
        val transformedToOriginal = mutableListOf<Int>()
        var originalIndex = 0
        var transformedIndex = 0
        matches.forEach { match ->
            while (originalIndex < match.range.first) {
                originalToTransformed[originalIndex] = transformedIndex
                rendered.append(text[originalIndex])
                transformedToOriginal += originalIndex
                originalIndex++
                transformedIndex++
            }
            val label = match.groupValues[1]
            val labelStart = match.range.first
            val labelContentStart = labelStart + 1
            val transformedStart = transformedIndex
            rendered.pushStyle(SpanStyle(color = referenceColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
            label.forEachIndexed { offset, char ->
                rendered.append(char)
                transformedToOriginal += labelContentStart + offset
                transformedIndex++
            }
            rendered.pop()
            for (index in match.range) originalToTransformed[index] = transformedStart
            originalToTransformed[labelContentStart.coerceAtMost(text.length)] = transformedStart
            originalToTransformed[(labelContentStart + label.length).coerceAtMost(text.length)] = transformedIndex
            originalIndex = match.range.last + 1
        }
        while (originalIndex < text.length) {
            originalToTransformed[originalIndex] = transformedIndex
            rendered.append(text[originalIndex])
            transformedToOriginal += originalIndex
            originalIndex++
            transformedIndex++
        }
        originalToTransformed[text.length] = transformedIndex
        transformedToOriginal += text.length
        return TransformedText(rendered.toAnnotatedString(), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, text.length)].coerceIn(0, transformedIndex)

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)].coerceIn(0, text.length)
        })
    }
}

private val COMPOSER_REFERENCE_LINK = Regex("\\[([^]\\n]+)]\\((?:agent|workspace|command):[^)]+\\)")
internal val COMPACT_COMMAND_PREFIX = Regex("^\\s*(?:/compact|\\[/compact]\\(command:compact\\))\\s*", RegexOption.IGNORE_CASE)

internal enum class ComposerCommand { MENTIONS, ACTIONS }

private data class ComposerSuggestion(val label: String, val detail: String, val value: String)

/** Snapshot of everything the command card draws, so it can outlive its own exit animation. */
internal data class ComposerPillState(
    val mode: ComposerCommand?,
    val query: String,
    val agents: List<ChatMentionAgent>,
    val files: List<com.amaya.intelligence.domain.models.ProjectFileEntry>,
    val isSearching: Boolean,
    val showCompact: Boolean,
    val compactDone: Boolean,
    val compactCanceled: Boolean
)

@Composable
internal fun ComposerCommandPill(
    mode: ComposerCommand?,
    query: String,
    agents: List<ChatMentionAgent>,
    files: List<com.amaya.intelligence.domain.models.ProjectFileEntry>,
    isSearching: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    if (mode == null) return
    val actions = listOf(
            ComposerSuggestion(stringResource(R.string.compact), stringResource(R.string.compress_context_optional_focus_after_command), "/compact"),
            ComposerSuggestion(stringResource(R.string.explain), stringResource(R.string.explain_selected_context), "/explain"),
            ComposerSuggestion(stringResource(R.string.review), stringResource(R.string.review_code_or_text), "/review"),
            ComposerSuggestion(stringResource(R.string.plan), stringResource(R.string.draft_a_concise_implementation_plan), "/plan")
    )
    val filteredActions = actions.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val filteredAgents = agents.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.role.contains(query, ignoreCase = true)
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 240.dp)) {
            // Both modes resolve their empty state the same way. Previously only MENTIONS had one,
            // so '/zzz' collapsed the card to a bare sliver while '@zzz' showed a message.
            val isEmpty = when (mode) {
                ComposerCommand.MENTIONS -> filteredAgents.isEmpty() && files.isEmpty() && !isSearching
                ComposerCommand.ACTIONS -> filteredActions.isEmpty()
            }
            val emptyLabel = when (mode) {
                ComposerCommand.MENTIONS ->
                    if (query.isBlank()) stringResource(R.string.no_agents_or_workspace_files) else stringResource(R.string.no_matching_agents_or_files)
                ComposerCommand.ACTIONS -> stringResource(R.string.no_matching_commands)
            }
            // Deliberately a plain swap, not Crossfade: Crossfade keeps both branches composed, so
            // the card would measure max(list, placeholder) for the whole transition and only then
            // snap down. Height is owned by the caller's animateContentSize instead.
            if (isEmpty) {
                Text(
                    emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 230.dp),
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                ) {
                    if (mode == ComposerCommand.MENTIONS) {
                        if (filteredAgents.isNotEmpty()) {
                            items(filteredAgents, key = { "agent:${it.groupName}:${it.name}" }) { agent ->
                                ComposerSuggestionRow(
                                    icon = Icons.Default.SmartToy,
                                    label = agent.name,
                                    detail = listOf(agent.groupName, agent.role).filter(String::isNotBlank).joinToString(" · "),
                                    onClick = { onSelect(com.amaya.intelligence.domain.models.agentMentionMarkdown(agent.id, agent.name)) }
                                )
                            }
                        }
                        if (files.isNotEmpty()) {
                            items(files, key = { "file:${it.path}" }) { file ->
                                ComposerSuggestionRow(
                                    icon = if (file.type == "directory") Icons.Default.Folder else Icons.Default.Description,
                                    label = file.name,
                                    detail = file.path,
                                    onClick = { onSelect(com.amaya.intelligence.domain.models.workspaceMentionMarkdown(file.path)) }
                                )
                            }
                        }
                        // Only when there is nothing else to show. Appending a spinner row below
                        // existing results grows then shrinks the card on every keystroke.
                        if (isSearching && filteredAgents.isEmpty() && files.isEmpty()) {
                            item(key = "searching") {
                                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    } else {
                        items(filteredActions, key = { it.value }) { action ->
                            ComposerSuggestionRow(Icons.Default.Terminal, action.label, action.detail) {
                                onSelect(com.amaya.intelligence.domain.models.commandMarkdown(action.value))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ReasoningEffortButton(
    effort: com.amaya.intelligence.data.remote.api.ThinkingEffort,
    onEffortChange: (com.amaya.intelligence.data.remote.api.ThinkingEffort) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = effort != com.amaya.intelligence.data.remote.api.ThinkingEffort.NONE

    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = stringResource(R.string.reasoning_effort),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActive) {
                    Text(
                        text = effort.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        // Opens to the right of the button and upwards; the window-edge clamp in the position
        // provider keeps it on screen if the button sits close to the right margin.
        AmayaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            alignment = AmayaMenuAlignment.Start,
            placement = AmayaMenuPlacement.Above,
            minWidth = 180.dp,
            focusable = false
        ) {
            com.amaya.intelligence.data.remote.api.ThinkingEffort.entries.forEach { level ->
                AmayaDropdownMenuItem(
                    text = level.label(),
                    selected = level == effort,
                    trailing = if (level == effort) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onEffortChange(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun com.amaya.intelligence.data.remote.api.ThinkingEffort.label(): String = when (this) {
    com.amaya.intelligence.data.remote.api.ThinkingEffort.NONE -> stringResource(R.string.off)
    com.amaya.intelligence.data.remote.api.ThinkingEffort.LOW -> stringResource(R.string.low)
    com.amaya.intelligence.data.remote.api.ThinkingEffort.MEDIUM -> stringResource(R.string.medium)
    com.amaya.intelligence.data.remote.api.ThinkingEffort.HIGH -> stringResource(R.string.high)
}

internal fun Modifier.pillShadowOverlay(topShadowAlpha: () -> Float): Modifier = this
    .drawWithContent {
        drawContent()
        val gradientHeight = 16.dp.toPx()

        val top = topShadowAlpha()
        if (top > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = top),
                    1.0f to Color.Transparent,
                    startY = 0f,
                    endY = gradientHeight
                )
            )
        }

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.35f),
                startY = (size.height - gradientHeight).coerceAtLeast(0f),
                endY = size.height
            )
        )
    }
