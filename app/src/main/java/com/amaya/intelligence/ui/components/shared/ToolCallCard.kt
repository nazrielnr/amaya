package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── ToolCallCard ─────────────────────────────────────────────────────────────

@Composable
internal fun ToolCallAnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    initiallyVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibilityState = remember { MutableTransitionState(initiallyVisible) }
    LaunchedEffect(visible) {
        visibilityState.targetState = visible
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = ToolCallMotion.enter,
        exit = ToolCallMotion.exit,
        modifier = modifier
    ) {
        // Exactly one animation owns this section's height: AnimatedVisibility.
        //
        // An animateContentSize() here used to run alongside it. On a leaf tool body
        // that was invisible, but this same wrapper is what the work-summary card uses
        // to hold a whole timeline of other cards — so the container spring spent the
        // whole expand chasing children whose own springs were still running, and the
        // collapse compounded two curves into one that read slower and looser than a
        // plain tool card. Parent and child now share one spec and one animation.
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun ToolCallCard(
    execution: ToolExecution,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onClarify: ((String?) -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    embeddedText: String? = null,
    /**
     * Set when the card's wrapper is mounting at the same instant and already runs the
     * fade for it. Two alpha ramps stacked would render this card at roughly half the
     * opacity of every other one for the length of the animation.
     */
    parentOwnsMountFade: Boolean = false
) {
    // Decided once per mount, never re-read.
    //
    // "animateOnMount" is written into the execution's metadata when the tool starts and
    // is persisted with the message, so it stays true forever. Read straight from
    // metadata, the fade therefore replayed every single time the card was composed —
    // scrolled back into view, or held inside a work-summary card the user just
    // expanded, where a row of tools would each start from zero height under a spring
    // that was already running. Only a tool still in flight when its card first appears
    // has a mount to animate.
    val shouldAnimate = remember(execution.toolCallId) {
        !parentOwnsMountFade &&
            execution.metadata["animateOnMount"].equals("true", ignoreCase = true) &&
            (execution.status == ToolStatus.PENDING || execution.status == ToolStatus.RUNNING)
    }
    ToolCardContent(
        execution = execution,
        onAccept = onAccept,
        onDecline = onDecline,
        onClarify = onClarify,
        onLocalhostLinkClick = onLocalhostLinkClick,
        embeddedText = embeddedText,
        modifier = Modifier.mountFade(shouldAnimate)
    )
}

// ── ToolCardContent (internal) ───────────────────────────────────────────────

@Composable
internal fun ToolCardContent(
    execution: ToolExecution,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onClarify: ((String?) -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    embeddedText: String? = null,
    modifier: Modifier = Modifier
) {
    val isThinkingCard = execution.isSyntheticThinkingCard()
    val isLocal = execution.metadata["source"].equals("local", ignoreCase = true)
    var expanded by remember(execution.toolCallId) { mutableStateOf(false) }
    var approvalSubmitted by remember(execution.toolCallId, execution.metadata["approvalState"]) { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val isTerminalApprovalCandidate = execution.metadata["isTerminal"].equals("true", ignoreCase = true)
        || execution.isShellTool()
    val approvalRequired = execution.metadata["approvalRequired"].equals("true", ignoreCase = true)
        || (isTerminalApprovalCandidate && execution.status == ToolStatus.PENDING)
    val approvalPending = execution.metadata["approvalState"].equals("pending", ignoreCase = true)
        || (approvalRequired && execution.status == ToolStatus.PENDING)
    val showApprovalActions = approvalRequired && approvalPending && onAccept != null && onDecline != null

    // An ask_user question waiting on the user. Rendered the same way as an approval
    // prompt (auto-expanded, section pinned below the header) but answers with free text.
    val clarificationPending = execution.metadata["clarificationRequired"].equals("true", ignoreCase = true) &&
        execution.metadata["clarificationState"].equals("pending", ignoreCase = true)
    val showClarificationActions = clarificationPending && onClarify != null
    var clarificationAnswer by remember(execution.toolCallId, execution.metadata["clarificationState"]) { mutableStateOf("") }
    var clarificationSubmitted by remember(execution.toolCallId, execution.metadata["clarificationState"]) { mutableStateOf(false) }

    // Auto-expand for an approval prompt or a pending question, and — the half that was
    // missing — auto-collapse once, when the tool actually finishes. Both edges are
    // latched, so a card the user has since opened or closed by hand is never overridden.
    var autoExpandedForApproval by remember(execution.toolCallId) { mutableStateOf(false) }
    var autoCollapsedOnce by remember(execution.toolCallId) { mutableStateOf(false) }
    val isTerminalStatus = execution.status == ToolStatus.SUCCESS || execution.status == ToolStatus.ERROR
    LaunchedEffect(showApprovalActions, showClarificationActions) {
        if (showApprovalActions || showClarificationActions) {
            expanded = true
            autoExpandedForApproval = true
        }
    }
    LaunchedEffect(isTerminalStatus) {
        if (isTerminalStatus && autoExpandedForApproval && !autoCollapsedOnce) {
            autoCollapsedOnce = true
            expanded = false
        }
    }

    val iosGreen = Color(0xFF34C759)
    val iosBlue  = Color(0xFF007AFF)
    val iosRed   = MaterialTheme.colorScheme.error

    val isSubagent = execution.name == "invoke_subagents"
    val isWebSearch = execution.name == "web_search" || execution.name == "search_web" || execution.name == "websearch"
    val isMemoryManage = execution.name == "memory_manage"
    // A read has nothing worth opening: the file went to the model, and mirroring it in
    // the timeline turns one card into a wall of source. Only a failed read has a body,
    // and it holds the reason, not the file.
    val isFileRead = execution.name == "read_file"
    val hasLocalSemanticBody = execution.status == ToolStatus.ERROR ||
        showApprovalActions ||
        showClarificationActions ||
        (!isFileRead && localToolPath(execution.arguments) != null) ||
        (execution.metadata["syntheticBrowserStep"] == "true" && (!execution.result.isNullOrBlank() || execution.arguments.isNotEmpty())) ||
        when (execution.name) {
            "read_file" -> false
            "list_files", "find_files", "run_shell", "web_search", "update_memory", "agent_memory", "agent_memory_internal",
            "memory_manage", "skill_view", "skill_manage", "session_search", "invoke_subagents", "delegate_agent" ->
                !execution.result.isNullOrBlank()
            "edit_file" -> execution.hasCanonicalFileDiff() || !execution.result.isNullOrBlank()
            "write_file", "create_directory", "delete_file", "undo_change" ->
                !execution.result.isNullOrBlank() && !isNoisyLocalSuccess(execution.result.orEmpty())
            else -> false
        }
    val canExpand = when {
        isThinkingCard -> !execution.result.isNullOrBlank()
        isLocal -> hasLocalSemanticBody
        else -> ((execution.status == ToolStatus.SUCCESS || execution.status == ToolStatus.ERROR) &&
            (execution.result != null || execution.children.isNotEmpty() || execution.arguments.isNotEmpty())) ||
            (execution.status == ToolStatus.RUNNING && execution.arguments.isNotEmpty())
    }
    val showChildren = isSubagent && execution.children.isNotEmpty() && expanded

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
    val blockBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val shimmerProgress = rememberToolShimmerProgress(
        execution.status == ToolStatus.RUNNING || execution.children.any { it.status == ToolStatus.RUNNING }
    )

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
        (isWebSearch || !isGenericResult || hasInjectedPreview || isTerminal))

    // Gated on canExpand: a tool can lose its body as it reaches a terminal status
    // (a noisy generic success, for instance), and without this the body would stay
    // open with no toggle left to close it.
    val thinkingVisible = isThinkingCard && canExpand && !execution.result.isNullOrBlank() && expanded
    val hasResultDetails = expanded && canExpand && !isSubagent && !isThinkingCard &&
        (execution.result != null || execution.arguments.isNotEmpty())
    val hasSubagentResultDetails = expanded && canExpand && isSubagent &&
        execution.children.isEmpty() && execution.result != null
    val approvalSectionVisible = showApprovalActions && !approvalSubmitted
    val headerText = resolveToolCallHeaderText(execution, uiMeta, showApprovalActions, approvalPending)

    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        border   = toolCardBorder(),
        modifier = modifier.fillMaxWidth()
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
                ToolLeadIconPill(uiMeta?.actionIcon ?: ToolInfoIcon.TASK)

                Text(
                    text = ">",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )

                Text(
                    text       = headerText,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .toolHeaderShimmer(execution.status == ToolStatus.RUNNING, shimmerProgress)
                        .toolHeaderFade()
                )

                if (execution.status == ToolStatus.RUNNING || execution.status == ToolStatus.PENDING) {
                    Icon(statusIcon, null, modifier = Modifier.size(14.dp), tint = statusColor)
                }

                if (canExpand) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            ToolCallAnimatedSection(visible = thinkingVisible, initiallyVisible = thinkingVisible) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    border = BorderStroke(1.dp, blockBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    ToolScrollableBlock(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)) {
                        MarkdownText(
                            text = execution.result.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            compact = true,
                            modifier = Modifier.padding(10.dp),
                            onLocalhostLinkClick = onLocalhostLinkClick
                        )
                    }
                }
            }

            ToolCallAnimatedSection(visible = approvalSectionVisible, initiallyVisible = approvalSectionVisible) {
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
                        text = execution.metadata["approvalReason"]?.takeIf { it.isNotBlank() } ?: "Waiting for approval",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isLocal && (isTerminal || execution.uiMetadata?.category == ToolCategory.MEMORY || execution.uiMetadata?.category == ToolCategory.SKILL) && execution.arguments.isNotEmpty()) {
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
                        Button(
                            onClick = {
                                if (!approvalSubmitted && approvalSectionVisible) {
                                    approvalSubmitted = true
                                    onDecline?.invoke()
                                }
                            },
                            enabled = !approvalSubmitted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Decline")
                        }
                        Button(
                            onClick = {
                                if (!approvalSubmitted && approvalSectionVisible) {
                                    approvalSubmitted = true
                                    onAccept?.invoke()
                                }
                            },
                            enabled = !approvalSubmitted,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Approve")
                        }
                    }
                }
            }

            ToolCallAnimatedSection(visible = showClarificationActions && !clarificationSubmitted, initiallyVisible = showClarificationActions && !clarificationSubmitted) {
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
                        text = "The agent needs your input",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = execution.metadata["clarificationQuestion"]?.takeIf { it.isNotBlank() }
                            ?: "Answer to continue",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    execution.metadata["clarificationOptions"]?.split("\n")?.filter { it.isNotBlank() }?.forEach { option ->
                        val selected = clarificationAnswer == option
                        OutlinedButton(
                            onClick = { clarificationAnswer = option },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OutlinedTextField(
                        value = clarificationAnswer,
                        onValueChange = { clarificationAnswer = it },
                        placeholder = { Text("Type your answer…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 4
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (!clarificationSubmitted && showClarificationActions) {
                                    clarificationSubmitted = true
                                    onClarify?.invoke(null)
                                }
                            },
                            enabled = !clarificationSubmitted,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss")
                        }
                        Button(
                            onClick = {
                                if (!clarificationSubmitted && showClarificationActions) {
                                    clarificationSubmitted = true
                                    onClarify?.invoke(clarificationAnswer)
                                }
                            },
                            enabled = !clarificationSubmitted && clarificationAnswer.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send")
                        }
                    }
                }
            }

            ToolCallAnimatedSection(visible = showChildren, initiallyVisible = showChildren) {
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

            ToolCallAnimatedSection(visible = hasResultDetails, initiallyVisible = hasResultDetails) {
                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                if (isLocal) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        localToolPath(execution.arguments)?.let { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                softWrap = true
                            )
                        }
                        if (execution.metadata["syntheticBrowserStep"] == "true" && execution.arguments.isNotEmpty()) {
                            ToolArgumentsPreview(
                                toolName = execution.name,
                                arguments = execution.arguments,
                                isDark = isDark,
                                category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                                uiMetadata = execution.uiMetadata,
                                isBrowserStep = true
                            )
                        }
                        if (execution.name == "delegate_agent") {
                            ToolScrollableBlock(MaterialTheme.colorScheme.surfaceContainerLow) {
                                ToolArgumentsPreview(
                                    toolName = execution.name,
                                    arguments = execution.arguments,
                                    isDark = isDark,
                                    category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                                    uiMetadata = execution.uiMetadata,
                                    result = execution.result
                                )
                            }
                            HorizontalDivider(
                                color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                                thickness = 0.8.dp
                            )
                        }
                        ToolScrollableBlock(MaterialTheme.colorScheme.surfaceContainerLow) {
                            ToolResultPreview(
                                toolName = execution.name,
                                arguments = execution.arguments,
                                result = execution.result.orEmpty(),
                                isDark = isDark,
                                category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                                onLocalhostLinkClick = onLocalhostLinkClick,
                                uiMetadata = execution.uiMetadata,
                                isLocal = true,
                                isError = execution.status == ToolStatus.ERROR,
                                isBrowserStep = execution.metadata["syntheticBrowserStep"] == "true"
                            )
                        }
                    }
                } else {
                    if (execution.arguments.isNotEmpty() && !isWebSearch && !isMemoryManage) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = if (shouldShowResult) 8.dp else 12.dp)
                        ) {
                            ToolScrollableBlock(MaterialTheme.colorScheme.surfaceContainerLow) {
                                ToolArgumentsPreview(
                                    toolName = execution.name,
                                    arguments = execution.arguments,
                                    isDark = isDark,
                                    category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                                    uiMetadata = execution.uiMetadata,
                                    result = execution.result
                                )
                            }
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
                            ToolScrollableBlock(MaterialTheme.colorScheme.surfaceContainerLow) {
                                ToolResultPreview(
                                    toolName = execution.name,
                                    arguments = execution.arguments,
                                    result = execution.result ?: "",
                                    isDark = isDark,
                                    category = execution.uiMetadata?.category ?: ToolCategory.UNKNOWN,
                                    onLocalhostLinkClick = onLocalhostLinkClick,
                                    uiMetadata = execution.uiMetadata
                                )
                            }
                        }
                    }
                }
            }

            ToolCallAnimatedSection(visible = hasSubagentResultDetails, initiallyVisible = hasSubagentResultDetails) {
                HorizontalDivider(
                    color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    border   = BorderStroke(1.dp, blockBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    ToolScrollableBlock(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)) {
                        MarkdownText(
                            text     = execution.result.orEmpty(),
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
                        .toolHeaderShimmer(child.status == ToolStatus.RUNNING, shimmerProgress)
                        .toolHeaderFade()
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
                        border   = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val blockColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                        ToolScrollableBlock(blockColor) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                MarkdownText(
                                    text     = child.result.orEmpty(),
                                    color    = MaterialTheme.colorScheme.onSurface,
                                    compact  = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
