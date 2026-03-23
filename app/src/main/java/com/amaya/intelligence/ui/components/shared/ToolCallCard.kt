package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ── ToolCallCard ─────────────────────────────────────────────────────────────

private object ToolCallMotion {
    val motionSpec: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val mountFadeIn = fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))
    val enter = expandVertically(animationSpec = motionSpec) + fadeIn(tween(durationMillis = 180, easing = FastOutSlowInEasing))
    val exit = shrinkVertically(animationSpec = motionSpec) + fadeOut(tween(durationMillis = 140, easing = FastOutSlowInEasing))
}

private fun resolveToolCallHeaderText(
    execution: ToolExecution,
    uiMeta: ToolUiMetadata?,
    showApprovalActions: Boolean,
    approvalPending: Boolean
): String {
    if (!execution.isShellTool()) return uiMeta?.label ?: execution.name

    val command = execution.arguments["command"]?.toString()
        ?: execution.arguments["CommandLine"]?.toString()
        ?: execution.arguments["commandLine"]?.toString()
        ?: execution.arguments["submittedCommandLine"]?.toString()
        ?: execution.arguments["proposedCommandLine"]?.toString()
        ?: execution.arguments["cmd"]?.toString()

    return command
        ?.takeIf { it.isNotBlank() }
        ?.let { truncateTerminalHeaderCommand(it) }
        ?: uiMeta?.label
        ?: execution.name
}

private fun truncateTerminalHeaderCommand(command: String, maxLength: Int = 88): String {
    val cleaned = command.trim()
    if (cleaned.length <= maxLength) return cleaned
    return cleaned.take(maxLength - 1).trimEnd() + "…"
}

@Composable
private fun ToolCallAnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibilityState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        visibilityState.targetState = visible
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = ToolCallMotion.enter,
        exit = ToolCallMotion.exit,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun ToolCallCard(
    execution: ToolExecution,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    val shouldAnimate = execution.metadata["animateOnMount"].equals("true", ignoreCase = true)
    var visible by remember(execution.toolCallId) { mutableStateOf(!shouldAnimate) }

    if (shouldAnimate) {
        LaunchedEffect(execution.toolCallId) {
            visible = true
        }
    }

    if (shouldAnimate) {
        AnimatedVisibility(
            visible = visible,
            enter = ToolCallMotion.mountFadeIn
        ) {
            ToolCardContent(execution, onAccept, onDecline, onLocalhostLinkClick)
        }
    } else {
        ToolCardContent(execution, onAccept, onDecline, onLocalhostLinkClick)
    }
}

// ── ToolCardContent (internal) ───────────────────────────────────────────────

@Composable
internal fun ToolCardContent(
    execution: ToolExecution,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    val isThinkingCard = execution.isSyntheticThinkingCard()
    var expanded by remember(execution.toolCallId) { mutableStateOf(false) }
    var approvalDismissed by remember(execution.toolCallId) { mutableStateOf(false) }
    val approvalScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val isTerminalApprovalCandidate = execution.metadata["isTerminal"].equals("true", ignoreCase = true)
        || execution.isShellTool()
    val approvalRequired = execution.metadata["approvalRequired"].equals("true", ignoreCase = true)
        || (isTerminalApprovalCandidate && execution.status == ToolStatus.PENDING)
    val approvalPending = execution.metadata["approvalState"].equals("pending", ignoreCase = true)
        || (approvalRequired && execution.status == ToolStatus.PENDING)
    val showApprovalActions = approvalRequired && approvalPending && onAccept != null && onDecline != null

    val iosGreen = Color(0xFF34C759)
    val iosBlue  = Color(0xFF007AFF)
    val iosRed   = MaterialTheme.colorScheme.error

    val isSubagent = execution.name == "invoke_subagents"
    val canExpand  = (execution.status == ToolStatus.SUCCESS || execution.status == ToolStatus.ERROR) &&
        (execution.result != null || execution.children.isNotEmpty())
    val showChildren = isSubagent && execution.children.isNotEmpty() &&
        (execution.status == ToolStatus.RUNNING || expanded)

    val uiMeta = execution.uiMetadata
    
    val bgColor = when (execution.status) {
        ToolStatus.ERROR   -> if (isDark) iosRed.copy(alpha = 0.10f)  else iosRed.copy(alpha = 0.06f)
        ToolStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceContainerLow
        ToolStatus.RUNNING -> if (isDark) iosBlue.copy(alpha = 0.08f) else iosBlue.copy(alpha = 0.04f)
        ToolStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    }
    val statusColor = when (execution.status) {
        ToolStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ToolStatus.RUNNING -> iosBlue
        ToolStatus.SUCCESS -> iosGreen
        ToolStatus.ERROR   -> iosRed
    }
    val statusIcon = when (execution.status) {
        ToolStatus.PENDING -> Icons.Default.Pause
        ToolStatus.RUNNING -> Icons.Default.Autorenew
        ToolStatus.SUCCESS -> Icons.Default.Check
        ToolStatus.ERROR   -> Icons.Default.Close
    }
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val shouldShimmer = execution.status == ToolStatus.RUNNING ||
        execution.children.any { it.status == ToolStatus.RUNNING }
    val shimmerProgress = if (shouldShimmer) {
        val shimmerTransition = rememberInfiniteTransition(label = "tool_shimmer")
        val animated by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer_x"
        )
        animated
    } else {
        0f
    }

    val isTaskBoundary = execution.isTaskBoundaryTool()
    val hasTaskBoundaryArgs = isTaskBoundary && (
        execution.arguments["title"] != null ||
            execution.arguments["TaskName"] != null ||
            execution.arguments["TaskSummary"] != null ||
            execution.arguments["description"] != null
        )
    val genericResultStrings = setOf(
        "done", "success", "completed", "file updated", "file written",
        "directory listed", "search complete", "user notified",
        "file read", "file created", "read", "write", "written", "completed successfully"
    )
    val normalizedResult = execution.result?.trim()?.lowercase() ?: ""
    val isGenericResult = normalizedResult in genericResultStrings || normalizedResult.contains("success")
    
    val hasInjectedPreview = execution.hasCanonicalFileDiff()

    val isTerminal = execution.isShellTool()

    val shouldShowResult = !isThinkingCard && (execution.result != null && execution.result.isNotBlank() && 
        !isTaskBoundary && 
        execution.uiMetadata?.actionIcon != ToolInfoIcon.MESSAGE &&
        (!isGenericResult || hasInjectedPreview || isTerminal))

    val thinkingVisible = isThinkingCard && !execution.result.isNullOrBlank() && (execution.status == ToolStatus.RUNNING || expanded)
    val hasResultDetails = expanded && !isSubagent && !isThinkingCard && execution.result != null
    val hasSubagentResultDetails = expanded && isSubagent && execution.children.isEmpty() && execution.result != null
    val approvalSectionVisible = showApprovalActions && !approvalDismissed
    val headerText = resolveToolCallHeaderText(execution, uiMeta, showApprovalActions, approvalPending)

    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // HEADER ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(horizontal = 12.dp, vertical = if (isThinkingCard) 6.dp else 9.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ACTION ICON
                uiMeta?.actionIcon?.let { iconType ->
                    Icon(
                        imageVector = mapToolIcon(iconType),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // BADGES
                uiMeta?.badges?.forEach { badgeText ->
                    val badgeColor = when (badgeText) {
                        "PLANNING"    -> iosBlue
                        "EXECUTION"   -> iosGreen
                        "VERIFICATION" -> Color(0xFFAF52DE) // Purple
                        "OVERWRITE"   -> Color(0xFFF2994A) // Orange
                        "ERROR", "DELETE" -> iosRed
                        else -> MaterialTheme.colorScheme.primary
                    }
                    BadgeLabel(badgeText, badgeColor)
                }

                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                // TARGET ICON
                uiMeta?.targetIcon?.let { iconType ->
                    if (iconType == ToolInfoIcon.FILE) {
                        val fileTypePath = remember(execution.toolCallId, execution.arguments) {
                            resolveFileTypeSourcePath(execution)
                        }
                        val context = LocalContext.current
                        val fileTypeAssetNames = remember(context) { loadFileTypeIconAssetNames(context) }
                        val fileTypeAsset = remember(fileTypePath, fileTypeAssetNames) {
                            fileTypePath?.let { resolveFileTypeIconAssetName(it, fileTypeAssetNames) }
                        }

                        if (fileTypePath != null && fileTypeAsset != null) {
                            FileTypeHeaderIcon(
                                filePath = fileTypePath,
                                resolvedAssetName = fileTypeAsset,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = mapToolIcon(iconType),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = mapToolIcon(iconType),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text       = when {
                        execution.isShellTool() -> headerText
                        isThinkingCard -> headerText
                        else -> "'$headerText'"
                    },
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .then(
                            if (execution.status == ToolStatus.RUNNING)
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        val w = size.width
                                        val peakX = (shimmerProgress * (w * 3f)) - w
                                        val hw = w * 0.6f
                                        drawRect(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 1f),
                                                    Color.White.copy(alpha = 0.7f),
                                                    Color.White.copy(alpha = 0.3f),
                                                    Color.White.copy(alpha = 0f),
                                                    Color.White.copy(alpha = 0.3f),
                                                    Color.White.copy(alpha = 0.7f),
                                                    Color.White.copy(alpha = 1f)
                                                ),
                                                start = Offset(peakX - hw, 0f),
                                                end   = Offset(peakX + hw, 0f)
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            else Modifier
                        )
                )

                // STATUS ICON
                Icon(statusIcon, null, modifier = Modifier.size(14.dp), tint = statusColor)

                if (canExpand) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            ToolCallAnimatedSection(visible = thinkingVisible) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    MarkdownText(
                        text = execution.result.orEmpty().take(1500),
                        color = MaterialTheme.colorScheme.onSurface,
                        compact = true,
                        modifier = Modifier.padding(10.dp),
                        onLocalhostLinkClick = onLocalhostLinkClick
                    )
                }
            }

            ToolCallAnimatedSection(visible = approvalSectionVisible) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.15f),
                        thickness = 1.dp
                    )
                    Text(
                        text = "Waiting for approval",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isTerminal && execution.arguments.isNotEmpty()) {
                        ToolArgumentsPreview(
                            toolName = execution.name,
                            arguments = execution.arguments,
                            isDark = isDark,
                            category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                            uiMetadata = execution.uiMetadata
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (approvalSectionVisible) {
                                    approvalDismissed = true
                                    approvalScope.launch {
                                        delay(120)
                                        onDecline?.invoke()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Decline")
                        }
                        Button(
                            onClick = {
                                if (approvalSectionVisible) {
                                    approvalDismissed = true
                                    approvalScope.launch {
                                        delay(120)
                                        onAccept?.invoke()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Accept")
                        }
                    }
                }
            }

            ToolCallAnimatedSection(visible = showChildren) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    execution.children.forEach { child ->
                        key(child.index) {
                            SubagentChildCard(
                                child           = child,
                                isDark          = isDark,
                                iosGreen        = iosGreen,
                                iosBlue         = iosBlue,
                                iosRed          = iosRed,
                                shimmerProgress = shimmerProgress
                            )
                        }
                    }
                }
            }

            ToolCallAnimatedSection(visible = hasResultDetails) {
                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )

                if (execution.arguments.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = if (shouldShowResult) 8.dp else 12.dp)
                    ) {
                        ToolArgumentsPreview(
                            toolName  = execution.name,
                            arguments = execution.arguments,
                            isDark    = isDark,
                            category  = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                            uiMetadata = execution.uiMetadata
                        )
                    }
                    if (shouldShowResult) {
                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        )
                    }
                }

                if (shouldShowResult) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        ToolResultPreview(
                            toolName  = execution.name,
                            arguments = execution.arguments,
                            result    = execution.result ?: "",
                            isDark    = isDark,
                            category  = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                            onLocalhostLinkClick = onLocalhostLinkClick,
                            uiMetadata = execution.uiMetadata
                        )
                    }
                }
            }

            ToolCallAnimatedSection(visible = hasSubagentResultDetails) {
                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    MarkdownText(
                        text     = (execution.result ?: "").take(3000),
                        color    = MaterialTheme.colorScheme.onSurface,
                        compact  = true,
                        modifier = Modifier.padding(10.dp),
                        onLocalhostLinkClick = onLocalhostLinkClick
                    )
                }
            }
        }
    }
}

// ── SubagentChildCard ────────────────────────────────────────────────────────

@Composable
internal fun SubagentChildCard(
    child: SubagentExecution,
    isDark: Boolean,
    iosGreen: Color,
    iosBlue: Color,
    iosRed: Color,
    shimmerProgress: Float
) {
    var expanded by remember(child.index) { mutableStateOf(false) }

    val statusColor = when (child.status) {
        ToolStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ToolStatus.RUNNING -> iosBlue
        ToolStatus.SUCCESS -> iosGreen
        ToolStatus.ERROR   -> iosRed
    }
    val bgColor = when (child.status) {
        ToolStatus.SUCCESS -> if (isDark) iosGreen.copy(alpha = 0.07f) else iosGreen.copy(alpha = 0.04f)
        ToolStatus.ERROR   -> if (isDark) iosRed.copy(alpha = 0.10f)  else iosRed.copy(alpha = 0.06f)
        ToolStatus.RUNNING -> if (isDark) iosBlue.copy(alpha = 0.10f) else iosBlue.copy(alpha = 0.05f)
        ToolStatus.PENDING -> if (isDark) Color(0xFF2C2C2E)           else MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val canExpand = child.result != null &&
        (child.status == ToolStatus.SUCCESS || child.status == ToolStatus.ERROR)

    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // HEADER ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape    = CircleShape,
                    color    = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (child.status == ToolStatus.RUNNING) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color       = iosBlue
                            )
                        } else {
                            Text(
                                text       = "${child.index + 1}",
                                style      = MaterialTheme.typography.labelSmall,
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = statusColor
                            )
                        }
                    }
                }

                Text(
                    text       = child.taskName,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color      = if (child.status == ToolStatus.PENDING)
                                     MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                 else MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .then(
                            if (child.status == ToolStatus.RUNNING)
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        val w = size.width
                                        val peakX = (shimmerProgress * (w * 3f)) - w
                                        val hw = w * 0.6f
                                        drawRect(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 1f),
                                                    Color.White.copy(alpha = 0.7f),
                                                    Color.White.copy(alpha = 0.3f),
                                                    Color.White.copy(alpha = 0f),
                                                    Color.White.copy(alpha = 0.3f),
                                                    Color.White.copy(alpha = 0.7f),
                                                    Color.White.copy(alpha = 1f)
                                                ),
                                                start = Offset(peakX - hw, 0f),
                                                end   = Offset(peakX + hw, 0f)
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                            else Modifier
                        )
                )

                when (child.status) {
                    ToolStatus.SUCCESS -> Surface(
                        shape = RoundedCornerShape(20.dp), color = iosGreen.copy(alpha = 0.15f)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelSmall,
                            color = iosGreen, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    ToolStatus.ERROR -> Surface(
                        shape = RoundedCornerShape(20.dp), color = iosRed.copy(alpha = 0.15f)
                    ) {
                        Text("Failed", style = MaterialTheme.typography.labelSmall,
                            color = iosRed, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    ToolStatus.RUNNING -> Icon(
                        Icons.Default.Autorenew,
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = iosBlue
                    )
                    ToolStatus.PENDING -> Text(
                        "Pending", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                if (canExpand) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // EXPANDABLE CONTENT
            AnimatedVisibility(
                visible = expanded && child.result != null,
                enter = ToolCallMotion.enter,
                exit = ToolCallMotion.exit
            ) {
                var showFull by remember(child.index) { mutableStateOf(false) }
                val truncateAt    = 2000
                val isTruncatable = (child.result?.length ?: 0) > truncateAt
                val displayText   = if (showFull || !isTruncatable) child.result ?: ""
                                    else child.result!!.take(truncateAt)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    HorizontalDivider(
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        shape    = RoundedCornerShape(8.dp),
                        color    = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            MarkdownText(
                                text     = displayText,
                                color    = MaterialTheme.colorScheme.onSurface,
                                compact  = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isTruncatable) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text       = if (showFull) "Show less"
                                                 else "\u2026 Show ${(child.result?.length ?: 0) - truncateAt} more chars",
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = iosBlue,
                                    fontWeight = FontWeight.Medium,
                                    modifier   = Modifier
                                        .clickable { showFull = !showFull }
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun mapToolIcon(icon: ToolInfoIcon): ImageVector {
    return when (icon) {
        ToolInfoIcon.EDIT      -> Icons.Default.Edit
        ToolInfoIcon.READ      -> Icons.Default.Visibility
        ToolInfoIcon.WRITE     -> Icons.Default.Add
        ToolInfoIcon.RUN       -> Icons.Default.Terminal
        ToolInfoIcon.CHECK     -> Icons.Default.CheckCircle
        ToolInfoIcon.SEARCH    -> Icons.Default.Search
        ToolInfoIcon.WEB_READ  -> Icons.Default.Language
        ToolInfoIcon.MESSAGE   -> Icons.Default.ChatBubble
        ToolInfoIcon.LIST      -> Icons.Default.FormatListBulleted
        ToolInfoIcon.FIND      -> Icons.Default.ManageSearch
        ToolInfoIcon.TASK      -> Icons.Default.Flag
        ToolInfoIcon.BROWSER   -> Icons.Default.Language
        ToolInfoIcon.DOCS      -> Icons.Default.MenuBook
        ToolInfoIcon.GENERATE  -> Icons.Default.AutoAwesome
        ToolInfoIcon.FILE      -> Icons.Default.Description
        ToolInfoIcon.FOLDER    -> Icons.Default.Folder
        ToolInfoIcon.COMMAND   -> Icons.Default.PlayArrow
        ToolInfoIcon.TERMINAL  -> Icons.Default.Terminal
        ToolInfoIcon.WORLD     -> Icons.Default.Public
        ToolInfoIcon.LINK      -> Icons.Default.Link
        ToolInfoIcon.PERSON    -> Icons.Default.Person
        ToolInfoIcon.CHUNK     -> Icons.Default.Extension
        ToolInfoIcon.ROCKET    -> Icons.Default.RocketLaunch
        ToolInfoIcon.MOUSE     -> Icons.Default.Mouse
        ToolInfoIcon.BOOK      -> Icons.Default.Book
        ToolInfoIcon.IMAGE     -> Icons.Default.Image
        ToolInfoIcon.DELETE    -> Icons.Default.Delete
        ToolInfoIcon.BRAIN     -> Icons.Default.Psychology
    }
}
