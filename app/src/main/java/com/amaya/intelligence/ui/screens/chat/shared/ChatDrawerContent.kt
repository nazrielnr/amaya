package com.amaya.intelligence.ui.screens.chat.shared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.ai.displayName
import com.amaya.intelligence.data.local.db.entity.ConversationEntity
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.ui.res.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UNCATEGORIZED_WORKSPACE_KEY = "uncategorized"

private fun normalizeWorkspacePath(path: String?): String? {
    return path
        ?.replace("\\", "/")
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
}

private fun groupConversationsByWorkspace(conversations: List<ConversationEntity>): Map<String, List<ConversationEntity>> {
    return conversations.groupBy { conversation ->
        normalizeWorkspacePath(conversation.workspacePath) ?: UNCATEGORIZED_WORKSPACE_KEY
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDrawerContent(
    drawerState: DrawerState,
    isRemoteMode: Boolean,
    uiState: ChatUiState,
    connectionState: ConnectionState,
    conversations: List<ConversationEntity>,
    onLoadConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToRemoteSession: () -> Unit,
    onExit: () -> Unit,
    hasMoreConversations: () -> Boolean,
    loadMoreConversations: () -> Unit,
    scope: CoroutineScope
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val conversationListState = rememberLazyListState()
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }

    LaunchedEffect(conversations.firstOrNull()?.id) {
        if (conversations.isNotEmpty() && conversationListState.firstVisibleItemIndex > 0) {
            conversationListState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(conversationListState.layoutInfo) {
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

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val isDark = isSystemInDarkTheme()
    val drawerBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerLow
    else MaterialTheme.colorScheme.surface

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp).fillMaxHeight(),
        drawerContainerColor = drawerBg,
        drawerShape = RectangleShape
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
                        onLoadConversation(id)
                        searchQuery = ""
                        isSearchExpanded = false
                        scope.launch { drawerState.close() }
                    },
                    onCancel = {
                        searchQuery = ""
                        isSearchExpanded = false
                    }
                )
            }

            // Normal sidebar mode
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
                    uiState = uiState,
                    conversations = filteredConversations,
                    conversationListState = conversationListState,
                    onClearConversation = {
                        onClearConversation()
                        scope.launch { drawerState.close() }
                    },
                    onNavigateToWorkspace = {
                        onNavigateToWorkspace()
                        scope.launch { drawerState.close() }
                    },
                    onNavigateToRemoteSession = {
                        onNavigateToRemoteSession()
                        scope.launch { drawerState.close() }
                    },
                    onExit = {
                        scope.launch { drawerState.close() }
                        onExit()
                    },
                    onLoadConversation = { id ->
                        onLoadConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onConversationLongClick = { conversationToDelete = it },
                    onSearchClick = { isSearchExpanded = true },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    scope = scope
                )
            }

            // Footer actions (fixed at bottom)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = drawerBg
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 1.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                Surface(
                    onClick = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    }
                }

                if (isRemoteMode) {
                    Surface(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onExit()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Exit",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun DrawerSearchContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    conversations: List<ConversationEntity>,
    onLoadConversation: (Long) -> Unit,
    onCancel: () -> Unit
) {
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search conversations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .clickable { onSearchQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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

        if (searchQuery.isNotBlank() || conversations.isNotEmpty()) {
            Text(
                if (searchQuery.isBlank()) "Recent" else "Results",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            when {
                conversations.isEmpty() -> item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No conversations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    }
                }
                searchQuery.isNotBlank() && conversations.isEmpty() -> item(key = "no-results") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No results for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
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
                            Text(
                                conv.title.ifEmpty { "New Chat" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerNormalContent(
    isRemoteMode: Boolean,
    uiState: ChatUiState,
    conversations: List<ConversationEntity>,
    conversationListState: androidx.compose.foundation.lazy.LazyListState,
    onClearConversation: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToRemoteSession: () -> Unit,
    onExit: () -> Unit,
    onLoadConversation: (Long) -> Unit,
    onConversationLongClick: (ConversationEntity) -> Unit,
    onSearchClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    scope: CoroutineScope
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerTitle = uiState.sessionMode.displayName()
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable { onCloseDrawer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onClearConversation,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.surface)
                    Spacer(Modifier.width(7.dp))
                    Text("New chat", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.surface)
                }
            }
            Surface(
                onClick = onNavigateToWorkspace,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    Spacer(Modifier.width(7.dp))
                    Text("Project", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (!isRemoteMode) {
            // Remote Session button
            Surface(
                onClick = onNavigateToRemoteSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Cast, null, modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Text("Remote Session", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable { onSearchClick() }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.width(10.dp))
            Text(
                "Search conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        val wsPath = remember(uiState.workspacePath) {
            normalizeWorkspacePath(uiState.workspacePath)
        }
        val groupedConversations = remember(conversations) {
            groupConversationsByWorkspace(conversations)
        }
        val currentWsConvs = groupedConversations[wsPath].orEmpty()
        val noWsConvs = groupedConversations[UNCATEGORIZED_WORKSPACE_KEY].orEmpty()
        val otherWsGroups = groupedConversations.filterKeys { key ->
            key != wsPath && key != UNCATEGORIZED_WORKSPACE_KEY
        }

        // Section label and conversation list
        if (conversations.isNotEmpty()) {
            var currentSectionLabel by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(conversationListState, wsPath, currentWsConvs.size, noWsConvs.size, otherWsGroups.size) {
                snapshotFlow {
                    val layoutInfo = conversationListState.layoutInfo
                    val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0

                    when {
                        firstVisibleItem < currentWsConvs.size -> {
                            val folder = wsPath?.substringAfterLast("/") ?: ""
                            "${UiStrings.Session.RECENT_ON} $folder"
                        }
                        firstVisibleItem < currentWsConvs.size + noWsConvs.size -> UiStrings.Session.UNCATEGORIZED
                        otherWsGroups.isNotEmpty() -> UiStrings.Session.OTHER_WORKSPACES
                        else -> null
                    }
                }.collect { label ->
                    currentSectionLabel = label
                }
            }

            val label = currentSectionLabel ?: if (!wsPath.isNullOrBlank()) {
                val folder = wsPath.substringAfterLast("/")
                "${UiStrings.Session.RECENT_ON} $folder"
            } else {
                UiStrings.Session.RECENT
            }

            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(
            state = conversationListState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            when {
                uiState.isLoadingConversations -> {
                    items(5) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                )
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                )
                            }
                        }
                    }
                }
                conversations.isEmpty() -> item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ChatBubbleOutline, null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                            }
                            Text("No conversations yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                    }
                }
                else -> {
                    if (currentWsConvs.isNotEmpty()) {
                        items(items = currentWsConvs, key = { it.id }) { conv ->
                            ConversationDrawerItem(
                                conv = conv,
                                showWorkspaceBadge = false,
                                onClick = { onLoadConversation(conv.id) },
                                onLongClick = { onConversationLongClick(conv) }
                            )
                        }
                    }

                    if (noWsConvs.isNotEmpty()) {
                        item(key = "header-none") {
                            Text(UiStrings.Session.UNCATEGORIZED, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
                        }
                        items(items = noWsConvs, key = { it.id }) { conv ->
                            ConversationDrawerItem(
                                conv = conv,
                                showWorkspaceBadge = false,
                                onClick = { onLoadConversation(conv.id) },
                                onLongClick = { onConversationLongClick(conv) }
                            )
                        }
                    }

                    if (otherWsGroups.isNotEmpty()) {
                        item(key = "header-other") {
                            Text("OTHER WORKSPACES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp))
                        }
                        otherWsGroups.forEach { (wsPath, convs) ->
                            items(items = convs, key = { it.id }) { conv ->
                                ConversationDrawerItem(
                                    conv = conv,
                                    showWorkspaceBadge = true,
                                    onClick = { onLoadConversation(conv.id) },
                                    onLongClick = { onConversationLongClick(conv) }
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationDrawerItem(
    conv: ConversationEntity,
    showWorkspaceBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                conv.title.ifEmpty { "New Chat" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}
