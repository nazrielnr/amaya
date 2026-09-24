package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.MessageStep
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.domain.models.ToolInfoIcon
import com.amaya.intelligence.domain.models.ToolStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ToolExecutionGroup(
    val key: String,
    val startIndex: Int,
    val endIndex: Int,
    val executions: List<ToolExecution>,
    val isActive: Boolean,
    val parentToolCallId: String? = null
)

/**
 * Contiguous local tool calls sharing [toolGroupKey].
 *
 * A group is emitted from the *first* groupable execution, not from the second. The
 * threshold used to be two, which meant a lone running tool rendered as a standalone
 * [ToolCallCard] and was then re-parented into a [ToolExecutionGroupCard] — under a
 * different key — the moment a sibling arrived. That unmount/remount threw away the
 * card's expansion state and its transition state, so its collapse never animated: the
 * card just blinked shut. Emitting from one keeps every groupable tool inside the same
 * wrapper for its whole life; [ToolExecutionGroupCard] renders that wrapper invisibly
 * until there really is more than one child.
 */
internal fun buildToolExecutionGroups(steps: List<MessageStep>): List<ToolExecutionGroup> {
    val groups = mutableListOf<ToolExecutionGroup>()
    var index = 0
    while (index < steps.size) {
        val execution = (steps[index] as? MessageStep.ToolCall)?.execution

        if (execution != null && execution.name == "browser") {
            val browserGroup = synthesizeBrowserGroup(execution, index)
            if (browserGroup != null) {
                groups += browserGroup
            }
            index++
            continue
        }

        // A batched read is one execution holding many files; it becomes one card per
        // file inside its own wrapper, exactly like a batched browser step.
        if (execution != null && execution.name == "read_file") {
            val batchReadGroup = synthesizeBatchReadGroup(execution, index)
            if (batchReadGroup != null) {
                groups += batchReadGroup
                index++
                continue
            }
        }

        val key = execution?.toolGroupKey()
        if (key == null) {
            index++
            continue
        }

        val start = index
        val children = mutableListOf(execution)
        while (index + 1 < steps.size) {
            val next = (steps[index + 1] as? MessageStep.ToolCall)?.execution ?: break
            if (next.toolGroupKey() != key) break
            children += next
            index++
        }
        groups += ToolExecutionGroup(
            key = key,
            startIndex = start,
            endIndex = index,
            executions = children,
            isActive = children.any {
                it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING
            }
        )
        index++
    }
    return groups
}

/** Mirrors the approval gate [ToolCardContent] applies to the child card itself. */
private fun ToolExecution.awaitsApproval(): Boolean {
    val required = metadata["approvalRequired"].equals("true", ignoreCase = true) ||
        ((metadata["isTerminal"].equals("true", ignoreCase = true) || isShellTool()) &&
            status == ToolStatus.PENDING)
    val pending = metadata["approvalState"].equals("pending", ignoreCase = true) ||
        (required && status == ToolStatus.PENDING)
    return required && pending
}

private fun ToolExecution.toolGroupKey(): String? {
    if (!metadata["source"].equals("local", ignoreCase = true)) return null
    return when (name) {
        "read_file", "write_file", "edit_file", "create_directory", "delete_file", "undo_change",
        "list_files", "find_files", "run_shell", "web_search", "create_reminder", "update_memory",
        "memory_manage", "skill_view", "skill_manage", "session_search", "browser" -> name
        else -> null
    }
}

/**
 * Wrapper holding collapsible child [ToolCallCard]s.
 *
 * The wrapper exists from the first groupable execution so children never change parent
 * (see [buildToolExecutionGroups]). With a single child it renders as pure structure —
 * no header, no tint, no border, no inset — and grows its chrome in when a second child
 * arrives. Every visual difference between the two states is animated, so nothing in the
 * tree is added or removed except the header row itself.
 */
@Composable
internal fun ToolExecutionGroupCard(
    group: ToolExecutionGroup,
    onToolAccept: ((ToolExecution) -> Unit)? = null,
    onToolDecline: ((ToolExecution) -> Unit)? = null,
    onClarify: ((ToolExecution, String?) -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    val hasError = group.executions.any { it.status == ToolStatus.ERROR }
    val isRunning = group.executions.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING }
    // Synthesised children (browser steps, batched reads) stand in for a single parent
    // execution; approval, when there is any, belongs to that parent's own card.
    val isSyntheticGroup = group.parentToolCallId != null
    val isGroup = group.executions.size >= 2

    // Never opens on its own. The wrapper used to spring open while its tools were still
    // pending or running and fold shut again when they finished, so every turn played a
    // burst of detail nobody asked for and then took it away. It stays closed until the
    // header is tapped.
    //
    // The one exception is an approval prompt: Approve and Decline live on the child
    // card, so a group holding an unanswered one has to be open or the turn cannot move.
    val awaitingApproval = group.executions.any { it.awaitsApproval() }
    var expanded by remember(group.key, group.executions.first().toolCallId) {
        mutableStateOf(awaitingApproval)
    }
    LaunchedEffect(awaitingApproval) {
        if (awaitingApproval) expanded = true
    }
    // A lone child is never hidden — there is no header to reopen it from.
    val childrenVisible = !isGroup || expanded

    // Same mount fade as every other timeline card, gated the same way: the group is new
    // if any of its tools was still in flight when the wrapper first composed. History,
    // or a re-open of a collapsed work card, appears instantly.
    val animateMount = remember { group.isActive }

    val isDark = isSystemInDarkTheme()
    val tint = when {
        hasError -> MaterialTheme.colorScheme.error
        isRunning -> Color(0xFF007AFF)
        else -> MaterialTheme.colorScheme.primary
    }
    val targetBackground = when {
        !isGroup -> Color.Transparent
        hasError -> tint.copy(alpha = if (isDark) 0.10f else 0.06f)
        isRunning -> tint.copy(alpha = if (isDark) 0.08f else 0.04f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val background by animateColorAsState(targetBackground, label = "group_background")
    // Same tone as toolCardBorder(), but as a plain colour so it can animate to
    // transparent when the wrapper is holding a single child.
    val borderColor by animateColorAsState(
        when {
            !isGroup -> Color.Transparent
            isDark -> Color.White.copy(alpha = 0.08f)
            else -> Color.Black.copy(alpha = 0.08f)
        },
        label = "group_border"
    )
    val childInset by animateDpAsState(if (isGroup) 6.dp else 0.dp, label = "group_inset")
    // Surface clips to its shape. With a lone child sitting flush inside it, the wrapper
    // has to match the child's own 14dp radius or it would shave its corners; it eases
    // to the group's 12dp as the chrome grows in.
    val cornerRadius by animateDpAsState(if (isGroup) 12.dp else 14.dp, label = "group_corner")

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = background,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().mountFade(animateMount)
    ) {
        Column(Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = isGroup,
                enter = ToolCallMotion.enter,
                exit = ToolCallMotion.exit
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolLeadIconPill(groupIcon(group.key), tint)
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        text = groupTitle(group),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).toolHeaderFade()
                    )
                    if (isRunning) {
                        Icon(Icons.Default.Autorenew, null, modifier = Modifier.size(14.dp), tint = tint)
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            AnimatedVisibility(
                visible = childrenVisible,
                enter = ToolCallMotion.enter,
                exit = ToolCallMotion.exit
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = childInset, end = childInset, bottom = childInset),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    group.executions.forEachIndexed { index, execution ->
                        key(execution.toolCallId) {
                            ToolCallCard(
                                // Only a real group shortens its children's headers to
                                // bare targets; a lone child keeps its full "Read x.kt".
                                execution = if (isGroup) {
                                    execution.copy(metadata = execution.metadata + ("groupedChild" to "true"))
                                } else execution,
                                onAccept = if (isSyntheticGroup) null else onToolAccept?.let { callback -> { callback(execution) } },
                                onDecline = if (isSyntheticGroup) null else onToolDecline?.let { callback -> { callback(execution) } },
                                onClarify = if (isSyntheticGroup) null else onClarify?.let { callback -> { answer -> callback(execution, answer) } },
                                onLocalhostLinkClick = onLocalhostLinkClick,
                                // The first child is the one the wrapper appeared with, so
                                // the wrapper's fade already covers it. Later siblings
                                // mount on their own and run their own.
                                parentOwnsMountFade = animateMount && index == 0
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun groupTitle(group: ToolExecutionGroup): String {
    val count = group.executions.size
    val successful = group.executions.all { it.status == ToolStatus.SUCCESS }
    return when (group.key) {
        "read_file" -> "Read $count files"
        "write_file" -> "${if (successful) "Wrote" else "Write"} $count files"
        "edit_file" -> "${if (successful) "Edited" else "Edit"} $count files"
        "create_directory" -> "${if (successful) "Created" else "Create"} $count directories"
        "delete_file" -> "${if (successful) "Deleted" else "Delete"} $count items"
        "undo_change" -> "${if (successful) "Restored" else "Restore"} $count files"
        "list_files" -> "${if (successful) "Listed" else "List"} $count directories"
        "find_files" -> "${if (successful) "Ran" else "Run"} $count file searches"
        "run_shell" -> "${if (successful) "Ran" else "Run"} $count commands"
        "web_search" -> "${if (successful) "Ran" else "Run"} $count web searches"
        "create_reminder" -> "${if (successful) "Scheduled" else "Schedule"} $count reminders"
        "update_memory" -> "${if (successful) "Saved" else "Save"} $count memories"
        "memory_manage" -> "Manage $count memories"
        "skill_view" -> "Read $count skills"
        "skill_manage" -> "Manage $count skills"
        "session_search" -> "Search $count previous chats"
        "browser" -> "${if (successful) "Performed" else "Perform"} $count browser actions"
        else -> if (group.parentToolCallId != null) {
            "${if (successful) "Performed" else "Perform"} $count browser actions"
        } else "$count tools"
    }
}

private fun groupIcon(key: String): ToolInfoIcon = when {
    key.startsWith("browser_") -> ToolInfoIcon.BROWSER
    else -> when (key) {
        "read_file" -> ToolInfoIcon.READ
        "write_file" -> ToolInfoIcon.WRITE
        "edit_file" -> ToolInfoIcon.EDIT
        "create_directory" -> ToolInfoIcon.FOLDER
        "delete_file" -> ToolInfoIcon.DELETE
        "undo_change" -> ToolInfoIcon.EDIT
        "list_files" -> ToolInfoIcon.LIST
        "find_files", "session_search" -> ToolInfoIcon.SEARCH
        "run_shell" -> ToolInfoIcon.RUN
        "web_search" -> ToolInfoIcon.WORLD
        "ask_user", "notify_user" -> ToolInfoIcon.MESSAGE
        "create_reminder" -> ToolInfoIcon.TASK
        "update_memory", "memory_manage", "agent_memory", "agent_memory_internal" -> ToolInfoIcon.BRAIN
        "skill_view", "skill_manage" -> ToolInfoIcon.BOOK
        "browser" -> ToolInfoIcon.BROWSER
        else -> ToolInfoIcon.TASK
    }
}
