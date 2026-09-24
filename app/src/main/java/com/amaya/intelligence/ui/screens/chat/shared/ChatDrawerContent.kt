package com.amaya.intelligence.ui.screens.chat.shared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.domain.models.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// =============================================================================
// Color Tokens - iOS Grouped Style
// =============================================================================

// =============================================================================
// Main Composable
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDrawerContent(
    drawerState: DrawerState,
    activeConversationId: String?,
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
    connectionState: ConnectionState,
    conversations: List<ConversationEntity>,
    onLoadConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToAgents: () -> Unit = {},
    onOpenChat: () -> Unit = onClearConversation,
    onOpenProject: (com.amaya.intelligence.data.local.entity.ProjectEntity, Long?) -> Unit = { _, _ -> },
    onOpenAgent: (com.amaya.intelligence.data.local.entity.AgentGroupEntity, com.amaya.intelligence.data.local.entity.AgentEntity, Long?) -> Unit = { _, _, _ -> },
    onNavigateToRemoteSession: () -> Unit,
    onNavigateToOpencode: (() -> Unit)? = null,
    onExit: () -> Unit,
    hasMoreConversations: () -> Boolean,
    loadMoreConversations: () -> Unit,
    scope: CoroutineScope,
    sessionDisconnectVisible: Boolean = false,
    onRequestSessionDisconnect: () -> Unit = {}
) {
    var unreadSet by remember { mutableStateOf(setOf<Long>()) }
    var lastUpdatedMap by remember { mutableStateOf(mapOf<Long, Long>()) }

    LaunchedEffect(allLocalConversations) {
        val newMap = mutableMapOf<Long, Long>()
        val newUnread = unreadSet.toMutableSet()
        for (conv in allLocalConversations) {
            val oldTime = lastUpdatedMap[conv.id]
            if (oldTime != null && conv.updatedAt > oldTime && conv.id.toString() != activeConversationId) {
                newUnread.add(conv.id)
            }
            newMap[conv.id] = conv.updatedAt
        }
        activeConversationId?.toLongOrNull()?.let { newUnread.remove(it) }

        lastUpdatedMap = newMap
        unreadSet = newUnread
    }

    LaunchedEffect(activeConversationId) {
        activeConversationId?.toLongOrNull()?.let { activeId ->
            val newUnread = unreadSet.toMutableSet()
            newUnread.remove(activeId)
            unreadSet = newUnread
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val conversationListState = rememberLazyListState()
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    val closeDrawerThen: ((() -> Unit) -> Unit) = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    LaunchedEffect(conversations.firstOrNull()?.id) {
        if (conversations.isNotEmpty() && conversationListState.firstVisibleItemIndex > 0) {
            conversationListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            val layoutInfo = conversationListState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5
        }.collect { shouldLoad ->
            if (shouldLoad && hasMoreConversations()) {
                loadMoreConversations()
            }
        }
    }

    BackHandler(enabled = isSearchExpanded) {
        searchQuery = ""
        isSearchExpanded = false
    }
    BackHandler(enabled = drawerState.isOpen && !isSearchExpanded) {
        scope.launch { drawerState.close() }
    }

    val filteredConversations = remember(conversations, allLocalConversations, searchQuery, isRemoteMode) {
        val source = if (isRemoteMode) conversations else allLocalConversations
        if (searchQuery.isBlank()) source
        else source.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.groupedBackground,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Expanded search mode
            androidx.compose.animation.AnimatedVisibility(
                visible = isSearchExpanded,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it },
                exit = fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                       slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { it }
            ) {
                DrawerSearchContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchFocusRequester = searchFocusRequester,
                    conversations = filteredConversations,
                    onLoadConversation = { id ->
                        searchQuery = ""
                        isSearchExpanded = false
                        closeDrawerThen { onLoadConversation(id) }
                    },
                    onCancel = {
                        searchQuery = ""
                        isSearchExpanded = false
                    }
                )
            }

            // Normal sidebar mode - iOS grouped style
            androidx.compose.animation.AnimatedVisibility(
                visible = !isSearchExpanded,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it },
                exit = fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                       slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -it }
            ) {
                DrawerNormalContent(
                    isRemoteMode = isRemoteMode,
                    sessionMode = sessionMode,
                    workspacePath = workspacePath,
                    assistantMode = assistantMode,
                    ownerLabel = ownerLabel,
                    agentMembers = agentMembers,
                    delegationTasks = delegationTasks,
                    projects = projects,
                    agentGroups = agentGroups,
                    allAgents = allAgents,
                    allLocalConversations = allLocalConversations,
                    runningSessions = runningSessions,
                    isLoadingConversations = isLoadingConversations,
                    conversations = filteredConversations,
                    unreadSet = unreadSet,
                    activeConversationId = activeConversationId,
                    conversationListState = conversationListState,
                    onClearConversation = { closeDrawerThen(onClearConversation) },
                    onOpenChat = { closeDrawerThen(onOpenChat) },
                    onOpenProject = { project, conversationId -> closeDrawerThen { onOpenProject(project, conversationId) } },
                    onOpenAgent = { group, agent, conversationId -> closeDrawerThen { onOpenAgent(group, agent, conversationId) } },
                    onNavigateToWorkspace = { closeDrawerThen(onNavigateToWorkspace) },
                    onNavigateToAgents = { closeDrawerThen(onNavigateToAgents) },
                    onNavigateToRemoteSession = { closeDrawerThen(onNavigateToRemoteSession) },
                    onNavigateToOpencode = onNavigateToOpencode?.let { callback ->
                        { closeDrawerThen(callback) }
                    },
                    onNavigateToSettings = { closeDrawerThen(onNavigateToSettings) },
                    onExit = { closeDrawerThen(onExit) },
                    onLoadConversation = { id -> closeDrawerThen { onLoadConversation(id) } },
                    onConversationLongClick = { conversationToDelete = it },
                    onSearchClick = { isSearchExpanded = true },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    scope = scope
                )
            }

            if (!isSearchExpanded) {
                if (sessionDisconnectVisible) {
                    androidx.compose.material3.SmallFloatingActionButton(
                        onClick = {
                            closeDrawerThen(onRequestSessionDisconnect)
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 140.dp, bottom = 26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Disconnect session",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (assistantMode != com.amaya.intelligence.domain.models.AssistantMode.AGENT) ExtendedFloatingActionButton(
                    onClick = { closeDrawerThen(onClearConversation) },
                    containerColor = Color(0xFF0A84FF),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (assistantMode) {
                            com.amaya.intelligence.domain.models.AssistantMode.CHAT -> "Chat"
                            com.amaya.intelligence.domain.models.AssistantMode.PROJECT -> "Project Chat"
                            com.amaya.intelligence.domain.models.AssistantMode.AGENT -> "Agent"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Right outline
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(0.7.dp)
                    .background(colors.border)
            )
        }
    }

    // Delete confirmation dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation?") },
            text = { Text("\"${conv.title.ifEmpty { "New Chat" }}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            onDeleteConversation(conv.id)
                            conversationToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =============================================================================
// Search Content
// =============================================================================

@Composable
internal fun HyperText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val rendered = rememberHyperText(text)
    Text(
        text = rendered,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
        color = color,
        modifier = modifier.clearAndSetSemantics { contentDescription = text }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationDrawerItem(
    conv: ConversationEntity,
    isActive: Boolean,
    isLast: Boolean,
    streaming: Boolean = false,
    unread: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isActive) colors.activeBackground else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HyperText(
                text = conv.title.ifEmpty { "New Chat" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 15.sp,
                    lineHeight = 19.sp
                ),
                color = colors.primaryText,
                modifier = Modifier.weight(1f).fadingEdge().shimmeringText(streaming, colors.primaryText)
            )

            if (streaming) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }

            if (unread) {
                UnreadDot()
            }

        }
    }

    // Add separator if not last
    if (!isLast) {
        IosRowSeparator()
    }
}

internal fun Modifier.fadingEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0.85f to Color.Black,
                1.0f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }
