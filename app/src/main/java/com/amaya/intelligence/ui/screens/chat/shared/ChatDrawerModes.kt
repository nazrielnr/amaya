package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.data.local.entity.ConversationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Composable
internal fun DrawerSearchContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    conversations: List<ConversationEntity>,
    onLoadConversation: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.groupSurface,
                border = BorderStroke(0.7.dp, colors.border),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(17.dp),
                        tint = colors.secondaryText)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryText
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search conversations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.secondaryText
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(colors.iconBackground)
                                .clickable { onSearchQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(11.dp),
                                tint = colors.secondaryText)
                        }
                    }
                }
            }
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onCancel() }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            when {
                conversations.isEmpty() && searchQuery.isBlank() -> item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No conversations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText)
                    }
                }
                searchQuery.isNotBlank() && conversations.isEmpty() -> item(key = "no-results") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No results for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryText)
                    }
                }
                else -> items(conversations, key = { it.id }) { conv ->
                    Surface(
                        onClick = { onLoadConversation(conv.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            HyperText(
                                text = conv.title.ifEmpty { "New Chat" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primaryText.copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth().fadingEdge()
                            )
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(300)
            searchFocusRequester.requestFocus()
        }
    }
}

// =============================================================================
// Normal Content - iOS Grouped Style
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerNormalContent(
    isRemoteMode: Boolean,
    sessionMode: IntelligenceSessionManager.SessionMode,
    workspacePath: String?,
    assistantMode: com.amaya.intelligence.domain.models.AssistantMode,
    ownerLabel: String,
    agentMembers: List<com.amaya.intelligence.data.local.entity.AgentEntity>,
    delegationTasks: List<com.amaya.intelligence.data.local.entity.DelegationTaskEntity>,
    projects: List<com.amaya.intelligence.data.local.entity.ProjectEntity>,
    agentGroups: List<com.amaya.intelligence.data.local.entity.AgentGroupEntity>,
    allAgents: List<com.amaya.intelligence.data.local.entity.AgentEntity>,
    allLocalConversations: List<ConversationEntity>,
    runningSessions: List<com.amaya.intelligence.domain.models.RunningSession>,
    isLoadingConversations: Boolean,
    conversations: List<ConversationEntity>,
    unreadSet: Set<Long>,
    activeConversationId: String?,
    conversationListState: androidx.compose.foundation.lazy.LazyListState,
    onClearConversation: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProject: (com.amaya.intelligence.data.local.entity.ProjectEntity, Long?) -> Unit,
    onOpenAgent: (com.amaya.intelligence.data.local.entity.AgentGroupEntity, com.amaya.intelligence.data.local.entity.AgentEntity, Long?) -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToRemoteSession: () -> Unit,
    onNavigateToOpencode: (() -> Unit)?,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    onLoadConversation: (Long) -> Unit,
    onConversationLongClick: (ConversationEntity) -> Unit,
    onSearchClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    scope: CoroutineScope
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    var expandedGroupId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var expandedProjectId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

    androidx.compose.runtime.LaunchedEffect(assistantMode, ownerLabel, agentGroups, projects) {
        if (assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
            expandedGroupId = agentGroups.firstOrNull { it.name == ownerLabel }?.id
        }
        if (assistantMode == com.amaya.intelligence.domain.models.AssistantMode.PROJECT) {
            expandedProjectId = projects.firstOrNull { it.name == ownerLabel }?.id
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amaya",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f)
            )

            // Search button
            Surface(
                onClick = onSearchClick,
                shape = CircleShape,
                color = colors.iconBackground,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Terminal button
            val context = LocalContext.current
            Surface(
                onClick = {
                    com.amaya.intelligence.ui.activities.terminal.TerminalActivity.start(context)
                },
                shape = CircleShape,
                color = colors.iconBackground,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        tint = colors.iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Settings button
            Surface(
                onClick = onNavigateToSettings,
                shape = CircleShape,
                color = colors.iconBackground,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val showLocalHierarchy = !isRemoteMode && sessionMode != IntelligenceSessionManager.SessionMode.WINDOWS_BRIDGE
        val showRemoteSessionRow = !isRemoteMode
        val showOpencodeRow = onNavigateToOpencode != null

        // Upper Section (Scrollable if too many projects/agents)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            if (showLocalHierarchy) {
                Text(
                    "AGENTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                IosGroupSurface(modifier = Modifier.fillMaxWidth()) {
                    if (agentGroups.isEmpty()) {
                        IosRowWithChevron(
                            icon = Icons.Default.SmartToy,
                            title = "Manage Agents",
                            showChevron = false,
                            onClick = onNavigateToAgents
                        )
                    } else {
                        agentGroups.forEachIndexed { index, group ->
                            val members = allAgents.filter { it.groupId == group.id }
                            val expanded = expandedGroupId == group.id
                            IosRowWithChevron(
                                icon = Icons.Default.Groups,
                                title = group.name,
                                subtitle = "${members.size} agents",
                                expanded = expanded,
                                selected = assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT && group.name == ownerLabel && !expanded,
                                onClick = { expandedGroupId = if (expanded) null else group.id }
                            )
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(animationSpec = spring()),
                                exit = shrinkVertically(animationSpec = spring())
                            ) {
                                Column {
                                    members.forEachIndexed { mIndex, agent ->
                                        val session = allLocalConversations.firstOrNull {
                                            it.assistantMode == "AGENT" && it.ownerId == group.id.toString() && it.agentId == agent.id
                                        }
                                        IosChildRow(
                                            icon = Icons.Default.SmartToy,
                                            title = agent.name,
                                            subtitle = agent.role.ifBlank { "Agent" },
                                            selected = assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT && session?.id?.toString() == activeConversationId,
                                            streaming = session?.id?.let { id -> runningSessions.any { it.conversationId == id } } == true,
                                            unread = session?.id?.let { id -> unreadSet.contains(id) } == true,
                                            isLastChild = mIndex == members.lastIndex,
                                            onClick = { onOpenAgent(group, agent, session?.id) }
                                        )
                                    }
                                }
                            }
                            if (index != agentGroups.lastIndex) IosRowSeparator()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "PROJECTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                IosGroupSurface(modifier = Modifier.fillMaxWidth()) {
                    if (projects.isEmpty()) {
                        IosRowWithChevron(
                            icon = Icons.Default.FolderOpen,
                            title = "Manage Projects",
                            showChevron = false,
                            onClick = onNavigateToWorkspace
                        )
                    } else {
                        projects.forEachIndexed { index, project ->
                            val sessions = allLocalConversations.filter { it.assistantMode == "PROJECT" && it.ownerId == project.id.toString() }
                                .sortedByDescending { it.updatedAt }
                            val expanded = expandedProjectId == project.id
                            IosRowWithChevron(
                                icon = Icons.Default.FolderOpen,
                                title = project.name,
                                subtitle = "${sessions.size} chats",
                                expanded = expanded,
                                selected = assistantMode == com.amaya.intelligence.domain.models.AssistantMode.PROJECT && project.name == ownerLabel && !expanded,
                                onClick = { expandedProjectId = if (expanded) null else project.id }
                            )
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(animationSpec = spring()),
                                exit = shrinkVertically(animationSpec = spring())
                            ) {
                                Column {
                                    // New Chat for this Project
                                    IosChildRow(
                                        icon = Icons.Default.Add,
                                        title = "New Chat",
                                        selected = false,
                                        isLastChild = sessions.isEmpty(),
                                        onClick = { onOpenProject(project, null) }
                                    )
                                    sessions.forEachIndexed { sIndex, session ->
                                        IosChildRow(
                                            title = session.title.ifBlank { "New chat" },
                                            selected = session.id.toString() == activeConversationId,
                                            streaming = runningSessions.any { it.conversationId == session.id },
                                            unread = unreadSet.contains(session.id),
                                            isLastChild = sIndex == sessions.lastIndex,
                                            onClick = { onOpenProject(project, session.id) }
                                        )
                                    }
                                }
                            }
                            if (index != projects.lastIndex) IosRowSeparator()
                        }
                    }
                }

                // CHATS
                val localChats = allLocalConversations.filter { it.assistantMode == "CHAT" && (it.ownerId.isNullOrEmpty() || it.ownerId == "null") }
                    .sortedByDescending { it.updatedAt }
                Spacer(Modifier.height(16.dp))
                Text(
                    "CHATS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                IosGroupSurface(modifier = Modifier.fillMaxWidth()) {
                    IosRowWithChevron(
                        icon = Icons.Default.Add,
                        title = "New Chat",
                        showChevron = false,
                        onClick = onOpenChat
                    )
                    localChats.forEach { conv ->
                        IosRowSeparator()
                        val isActive = conv.id.toString() == activeConversationId
                        IosRowWithChevron(
                            icon = null,
                            title = conv.title.ifBlank { "New chat" },
                            showChevron = false,
                            selected = isActive,
                            trailingProgress = runningSessions.any { it.conversationId == conv.id },
                            unread = unreadSet.contains(conv.id),
                            onClick = { onLoadConversation(conv.id) },
                            onLongClick = { onConversationLongClick(conv) }
                        )
                    }
                }
            } else if (showRemoteSessionRow || showOpencodeRow) {
                IosGroupSurface(Modifier.fillMaxWidth()) {
                    if (showOpencodeRow) IosRowWithChevron(Icons.Default.Terminal, "Opencode", onClick = { onNavigateToOpencode?.invoke() })
                    if (showOpencodeRow && showRemoteSessionRow) IosRowSeparator()
                    if (showRemoteSessionRow) IosRowWithChevron(Icons.Default.Devices, "Remote Session", onClick = onNavigateToRemoteSession)
                }
            }
        } // End Upper Section

        // Lower Section: Chats (LazyColumn with remaining weight)
        if (!showLocalHierarchy) {
            val chats = conversations
            if (chats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                IosGroupSurface(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = conversationListState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items = chats, key = { it.id }) { conv ->
                            val isActive = conv.id.toString() == activeConversationId
                            ConversationDrawerItem(
                                conv = conv,
                                isActive = isActive,
                                isLast = conv == chats.lastOrNull(),
                                streaming = runningSessions.any { it.conversationId == conv.id },
                                unread = unreadSet.contains(conv.id),
                                onClick = { onLoadConversation(conv.id) },
                                onLongClick = { onConversationLongClick(conv) }
                            )
                        }
                    }
                }
            } else if (isLoadingConversations) {
                Spacer(Modifier.height(16.dp))
                IosGroupSurface(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        repeat(3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.iconBackground)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.4f)
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.iconBackground.copy(alpha = 0.5f))
                                    )
                                }
                            }
                            if (it < 2) IosRowSeparator()
                        }
                    }
                }
            } else if (!isLoadingConversations && conversations.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                IosGroupSurface(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No conversations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}


// =============================================================================
// Conversation Item
// =============================================================================

private const val HYPER_TEXT_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val HYPER_TEXT_DURATION_MS = 900

internal fun hyperTextFrame(from: String, to: String, progress: Float): String {
    val fraction = progress.coerceIn(0f, 1f)
    if (fraction == 0f) return from
    if (fraction == 1f) return to

    val targetLen = to.length
    val frame = (fraction * 24).toInt()
    return buildString(targetLen) {
        repeat(targetLen) { index ->
            // Each character has its own resolve threshold, staggered evenly across the animation.
            // This makes characters lock in left-to-right smoothly, with the last one finishing
            // at fraction ~0.92 instead of 1.0, eliminating the "last char lag".
            val charThreshold = (index + 1).toFloat() / (targetLen + 1).toFloat()
            append(when {
                fraction >= charThreshold -> to[index]
                to[index].isWhitespace() -> to[index]
                index < from.length && from[index].isWhitespace() -> ' '
                else -> HYPER_TEXT_GLYPHS[Math.floorMod(to.hashCode() + index * 31 + frame * 17, HYPER_TEXT_GLYPHS.length)]
            })
        }
    }
}

@Composable
internal fun rememberHyperText(text: String): String {
    var target by remember { mutableStateOf(text) }
    var rendered by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        if (text == target) return@LaunchedEffect
        val from = rendered
        target = text
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = HYPER_TEXT_DURATION_MS, easing = FastOutSlowInEasing)
        ) {
            rendered = hyperTextFrame(from, text, value)
        }
        rendered = text
    }
    return rendered
}

