package com.amaya.intelligence.ui.chat

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    activeReminderCount: Int = -1, // -1 = use viewModel local value
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val confirmationRequest by viewModel.confirmationRequest.collectAsState()
    val todoItems by viewModel.todoItems.collectAsState()
    // Use global count from AppViewModel (passed in) if available; else fall back to local
    val localReminderCount by viewModel.activeReminderCount.collectAsState()
    val effectiveReminderCount = if (activeReminderCount >= 0) activeReminderCount else localReminderCount
    // FIX 1.8: hasTodayMemory removed entirely — was only used to show a dot indicator
    // on the session info button. Removed rather than kept as dead UI state.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showModelSelector by remember { mutableStateOf(false) }
    var showSessionInfo by remember { mutableStateOf(false) }
    
    // Track TodoBar height for dynamic padding (prevent chat overlap)
    var todoBarHeight by remember { mutableStateOf(0) }

    // Track input bar height for accurate bottom padding (avoids overlap with ChatInput)
    var inputBarHeight by remember { mutableStateOf(0) }

    // Only show enabled agents in dropdown
    val agentConfigs   = uiState.agentConfigs.filter { it.enabled }
    val activeAgentId  = uiState.activeAgentId
    val activeAgent    = agentConfigs.find { it.id == activeAgentId } ?: agentConfigs.firstOrNull()
    val selectedModel  = uiState.selectedModel.ifBlank { activeAgent?.modelId ?: "" }

    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter { it.content.isNotBlank() || it.toolExecutions.isNotEmpty() }
    }

    // --- Auto-scroll logic ---
    // "At bottom" = last item visible AND fully scrolled (no more content below)
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount
            // Consider "at bottom" if last item is visible and not cut off significantly
            lastVisible != null &&
                lastVisible.index >= totalItems - 1 &&
                lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset + 8
        }
    }

    // userAutoScroll = user has NOT manually scrolled away from bottom
    // Resets to true when: new user message sent OR user scrolls back to bottom
    var userAutoScroll by remember { mutableStateOf(true) }

    // Detect manual upward scroll: if user is scrolling and not at bottom → disable auto-scroll
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userAutoScroll = false
        }
    }

    // Re-enable auto-scroll when user scrolls back to bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) userAutoScroll = true
    }

    // Auto-scroll to bottom when new user message is sent (always)
    val userMsgCount = remember(displayMessages) { displayMessages.count { it.role == MessageRole.USER } }
    LaunchedEffect(userMsgCount) {
        if (displayMessages.isNotEmpty()) {
            userAutoScroll = true
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    // Auto-scroll during streaming: follow text if userAutoScroll is active
    val totalMsgSize = remember(displayMessages) { displayMessages.sumOf { it.content.length } }
    LaunchedEffect(totalMsgSize, uiState.isLoading) {
        if (userAutoScroll && displayMessages.isNotEmpty()) {
            // Scroll to last item (covers loading indicator + last message)
            val target = if (uiState.isLoading)
                displayMessages.size  // loading indicator is appended after messages
            else
                displayMessages.size - 1
            listState.animateScrollToItem(target)
        }
    }


    confirmationRequest?.let { request ->
        ConfirmationDialog(
            request = request,
            onConfirm = { viewModel.respondToConfirmation(true) },
            onDismiss = { viewModel.respondToConfirmation(false) }
        )
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val conversations by viewModel.conversations.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            var searchQuery by remember { mutableStateOf("") }
            val filteredConversations = remember(conversations, searchQuery) {
                if (searchQuery.isBlank()) conversations
                else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }

            ModalDrawerSheet(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // -- Header ----------------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Amaya",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // -- Action buttons --------------------------------------
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // New Chat
                        Surface(
                            onClick = {
                                viewModel.clearConversation()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "New chat",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // New Project
                        Surface(
                            onClick = {
                                onNavigateToWorkspace()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Project",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // -- Search bar ------------------------------------------
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search conversations…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // -- Section label ---------------------------------------
                    if (conversations.isNotEmpty()) {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    // -- Conversation list ------------------------------------
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        if (conversations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "No conversations yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        } else if (filteredConversations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No results for \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        } else {
                            items(filteredConversations.size) { index ->
                                val conv = filteredConversations[index]
                                Surface(
                                    onClick = {
                                        viewModel.loadConversation(conv.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            conv.title.ifEmpty { "New Chat" },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // -- Divider ---------------------------------------------
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // -- Footer: Settings ------------------------------------
                    Surface(
                        onClick = {
                            onNavigateToSettings()
                            scope.launch { drawerState.close() }
                        },
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    ) {
        // Use WindowInsets for accurate status bar height on any device/notch
        val statusBarInsets = WindowInsets.statusBars.asPaddingValues()
        val statusBarHeight = statusBarInsets.calculateTopPadding()
        val navBarHeight    = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val headerDp = statusBarHeight + 64.dp  // status bar + TopAppBar height
        val bottomDp = 80.dp + navBarHeight     // ChatInput + nav bar
        val bgColor  = MaterialTheme.colorScheme.background

        Box(modifier = Modifier.fillMaxSize()) {

            // -- 1. Content ----------------------------------------------------
            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerDp, bottom = bottomDp)
                        .imePadding(),
                    contentAlignment = Alignment.Center
                ) {
                    WelcomeScreen(
                        onPromptClick     = { viewModel.sendMessage(it) },
                        currentWorkspace  = uiState.workspacePath,
                        onNewProjectClick = onNavigateToWorkspace
                    )
                }
            } else {
                val density = LocalDensity.current
                // Normal LazyColumn: latest message at bottom
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier
                        .fillMaxSize()
                        .imePadding(),  // Auto-adjust for keyboard
                    contentPadding      = PaddingValues(
                        start  = 16.dp,
                        end    = 16.dp,
                        top    = headerDp + with(density) { todoBarHeight.toDp() } + 8.dp,
                        // Dynamic: matches actual input bar height so last message is never hidden
                        bottom = with(density) { inputBarHeight.toDp() } + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayMessages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                    if (uiState.isLoading) {
                        item(key = "loading") {
                            LoadingIndicator()
                        }
                    }
                }

                // Scroll-to-bottom FAB — appears when user scrolled away from bottom
                if (!isAtBottom) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .padding(
                                bottom = with(density) { inputBarHeight.toDp() } + 12.dp,
                                end = 16.dp
                            ),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    userAutoScroll = true
                                    listState.animateScrollToItem(
                                        if (uiState.isLoading) displayMessages.size
                                        else (displayMessages.size - 1).coerceAtLeast(0)
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // -- 2. Gradient scrim — covers status bar area --------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopStart)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f  to bgColor.copy(alpha = 0.98f),
                                    0.55f to bgColor.copy(alpha = 0.80f),
                                    1.0f  to bgColor.copy(alpha = 0.0f)
                                )
                            )
                        )
                    }
            )

            // -- 3. Floating header --------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                TopAppBar(
                    title = {
                        Box {
                            Surface(
                                onClick  = { showModelSelector = true },
                                shape    = CircleShape,
                                color    = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text  = (activeAgent?.name ?: selectedModel).ifBlank { "Select Agent" }
                                            .let { if (it.length > 20) it.take(18) + "..." else it },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, "Select Model",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(
                                expanded         = showModelSelector,
                                onDismissRequest = { showModelSelector = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                if (agentConfigs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("No active agents", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Enable agents in Settings → AI Agents", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            }
                                        },
                                        onClick = { showModelSelector = false }, enabled = false
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Select Agent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = {}, enabled = false
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                    agentConfigs.forEach { agent ->
                                        val isSelected = agent.id == activeAgentId ||
                                            (activeAgentId.isBlank() && agent == agentConfigs.firstOrNull())
                                        // Warn if model ID is missing
                                        val missingModel = agent.modelId.isBlank()
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(agent.name.ifBlank { "Unnamed Agent" },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                    if (missingModel) {
                                                        Text("⚠ No model ID — edit in Settings",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error)
                                                    } else {
                                                        Text(agent.modelId, style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (missingModel) Icons.Default.Warning
                                                    else if (isSelected) Icons.Default.CheckCircle
                                                    else Icons.Default.SmartToy,
                                                    null, modifier = Modifier.size(16.dp),
                                                    tint = if (missingModel) MaterialTheme.colorScheme.error
                                                           else if (isSelected) MaterialTheme.colorScheme.primary
                                                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                            },
                                            onClick = { viewModel.setSelectedAgent(agent); showModelSelector = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton({ scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        SessionInfoButton(
                            totalTokens         = uiState.totalInputTokens + uiState.totalOutputTokens,
                            activeModel         = selectedModel,
                            activeReminderCount = activeReminderCount,
                            onClick             = { showSessionInfo = true }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor             = Color.Transparent,
                        scrolledContainerColor     = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor          = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor     = MaterialTheme.colorScheme.onSurface
                    )
                )
                AnimatedVisibility(
                    visible = todoItems.isNotEmpty(),
                    enter   = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit    = shrinkVertically(tween(200)) + fadeOut(tween(200))
                ) { 
                    TodoBar(
                        items = todoItems,
                        modifier = Modifier.onSizeChanged { todoBarHeight = it.height }
                    )
                }
            }

            // -- 4. Floating bottom input --------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .imePadding()
                    .onSizeChanged { inputBarHeight = it.height }
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f  to bgColor.copy(alpha = 0.0f),
                                    0.40f to bgColor.copy(alpha = 0.85f),
                                    1.0f  to bgColor.copy(alpha = 0.98f)
                                )
                            )
                        )
                    }
            ) {
                AnimatedVisibility(visible = uiState.error != null) {
                    Surface(
                        color    = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape    = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(uiState.error ?: "", color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                if (uiState.messages.isEmpty()) {
                    ScrollablePills(onPromptClick = { viewModel.sendMessage(it) })
                }
                ChatInput(
                    onSend        = { viewModel.sendMessage(it) },
                    onStop        = { viewModel.stopGeneration() },
                    isLoading     = uiState.isLoading,
                    workspacePath = uiState.workspacePath
                )
            }
        }
    }


    // --- Session Info bottom sheet ---
    if (showSessionInfo) {
        SessionInfoSheet(
            totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens,
            activeModel = selectedModel,
            activeReminderCount = activeReminderCount,
            onDismiss = { showSessionInfo = false }
        )
    }
}


// -----------------------------------------------------------------------------
//  Session Info Button (replaces WorkspaceTokenChip)
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoButton(
    totalTokens: Int,
    activeModel: String,
    activeReminderCount: Int,
    // FIX 1.8: hasTodayMemory removed — PersonaRepository.hasTodayLog() was the only source
    // and personaRepository was removed from ChatViewModel to eliminate unused dependency.
    onClick: () -> Unit
) {
    val hasAlert = activeReminderCount > 0
    val tokenColor = when {
        totalTokens > 100_000 -> MaterialTheme.colorScheme.error
        totalTokens > 50_000  -> Color(0xFFFF9800)
        else                  -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Estimated context window based on model name
    val contextWindow = when {
        activeModel.contains("gpt-4o", ignoreCase = true)        -> "128K"
        activeModel.contains("gpt-4", ignoreCase = true)         -> "128K"
        activeModel.contains("gpt-3.5", ignoreCase = true)       -> "16K"
        activeModel.contains("claude-3-5", ignoreCase = true)    -> "200K"
        activeModel.contains("claude", ignoreCase = true)        -> "200K"
        activeModel.contains("gemini-1.5", ignoreCase = true)    -> "1M"
        activeModel.contains("gemini", ignoreCase = true)        -> "128K"
        activeModel.contains("mistral", ignoreCase = true)       -> "32K"
        activeModel.contains("deepseek", ignoreCase = true)      -> "64K"
        activeModel.contains("llama", ignoreCase = true)         -> "128K"
        else                                                     -> "—"
    }

    Box(modifier = Modifier.padding(end = 8.dp)) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Session Info",
                tint = if (hasAlert) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Badge for active reminders
        if (hasAlert) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoSheet(
    totalTokens: Int,
    activeModel: String,
    activeReminderCount: Int,
    // FIX 1.8: hasTodayMemory removed
    onDismiss: () -> Unit
) {
    // Estimated context window
    val contextWindow = when {
        activeModel.contains("gpt-4o", ignoreCase = true)        -> "128K"
        activeModel.contains("gpt-4", ignoreCase = true)         -> "128K"
        activeModel.contains("gpt-3.5", ignoreCase = true)       -> "16K"
        activeModel.contains("claude-3-5", ignoreCase = true)    -> "200K"
        activeModel.contains("claude", ignoreCase = true)        -> "200K"
        activeModel.contains("gemini-1.5", ignoreCase = true)    -> "1M"
        activeModel.contains("gemini", ignoreCase = true)        -> "128K"
        activeModel.contains("mistral", ignoreCase = true)       -> "32K"
        activeModel.contains("deepseek", ignoreCase = true)      -> "64K"
        activeModel.contains("llama", ignoreCase = true)         -> "128K"
        else                                                     -> "—"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Session Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            HorizontalDivider()

            // Tokens
            SessionInfoRow(
                icon = Icons.Default.AutoAwesome,
                label = "Tokens used",
                value = if (totalTokens > 0) formatTokenCount(totalTokens) else "0",
                valueColor = when {
                    totalTokens > 100_000 -> MaterialTheme.colorScheme.error
                    totalTokens > 50_000  -> Color(0xFFFF9800)
                    else                  -> MaterialTheme.colorScheme.onSurface
                }
            )

            // Context Window
            SessionInfoRow(
                icon = Icons.Default.DataUsage,
                label = "Context window",
                value = contextWindow
            )

            // Active Reminders
            SessionInfoRow(
                icon = Icons.Default.Alarm,
                label = "Active reminders",
                value = if (activeReminderCount > 0) "$activeReminderCount active" else "None",
                valueColor = if (activeReminderCount > 0) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            // Today's Memory
            // FIX 1.8: Memory today row removed — hasTodayMemory source (PersonaRepository) removed from ViewModel
        }
    }
}

@Composable
private fun SessionInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

private fun formatTokenCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000     -> String.format("%.1fK", count / 1_000.0)
    else               -> count.toString()
}

// -----------------------------------------------------------------------------
//  TodoBar — collapsible task list shown below TopAppBar
// -----------------------------------------------------------------------------

@Composable
fun TodoBar(
    items: List<TodoItem>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val completed  = items.count { it.status == TodoStatus.COMPLETED }
    val total      = items.size
    val current    = items.firstOrNull { it.status == TodoStatus.IN_PROGRESS }
    val isRunning  = current != null

    val collapsedLabel = current?.activeForm
        ?: current?.content
        ?: items.firstOrNull { it.status == TodoStatus.PENDING }?.content
        ?: "Tasks"

    val pillNumber = current?.id
        ?: items.firstOrNull { it.status == TodoStatus.PENDING }?.id
        ?: items.lastOrNull()?.id
        ?: 1

    // -- Shimmer — centered gradient for symmetrical fade ----
    val transition = rememberInfiniteTransition(label = "todo_shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -400f,
        targetValue  = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "todo_shimmer_x"
    )

    val gradientWidth = 500f
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 1f),    // Edge left: visible
            Color.White.copy(alpha = 0.7f),  // Fade in
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0f),    // Center: invisible
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.7f),  // Fade out
            Color.White.copy(alpha = 1f)     // Edge right: visible
        ),
        start  = Offset(shimmerOffset - gradientWidth / 2, 0f),
        end    = Offset(shimmerOffset + gradientWidth / 2, 0f)
    )

    // Glassmorphism effect - matches header gradient scrim
    val bgColor = MaterialTheme.colorScheme.surface
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Gradient scrim: solid top → transparent bottom (same as header)
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bgColor.copy(alpha = 0.98f),  // Top: nearly solid
                            1.0f to bgColor.copy(alpha = 0.85f)   // Bottom: more transparent
                        )
                    )
                )
            }
    ) {
        Column {
            // -- Collapsed row ------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -- Step number pill -----------------------------------------
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                ) {
                    Text(
                        text = "$pillNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(10.dp))

                // -- Label — shimmer if running, muted if not -----------------
                if (isRunning) {
                    // Shimmer text: warna solid onSurface, lalu SrcAtop overlay shimmerBrush
                    Text(
                        text = collapsedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface, // solid base agar SrcAtop bisa baca alpha
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn)
                            }
                    )
                } else {
                    Text(
                        text = collapsedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // -- Progress fraction -----------------------------------------
                Text(
                    text = "$completed/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.width(6.dp))

                // -- Chevron ---------------------------------------------------
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // -- Expanded list -------------------------------------------------
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit  = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    items.forEach { item ->
                        TodoItemRow(item = item, shimmerBrush = shimmerBrush)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun TodoItemRow(item: TodoItem, shimmerBrush: Brush) {
    val isActive    = item.status == TodoStatus.IN_PROGRESS
    val isCompleted = item.status == TodoStatus.COMPLETED

    val label = if (isActive) item.activeForm ?: item.content ?: "Task ${item.id}"
                else item.content ?: "Task ${item.id}"

    val iconTint = when (item.status) {
        TodoStatus.COMPLETED   -> Color(0xFF4CAF50)
        TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        TodoStatus.PENDING     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        // -- Status icon ----------------------------------------------------
        Icon(
            imageVector = when (item.status) {
                TodoStatus.COMPLETED   -> Icons.Default.CheckCircle
                TodoStatus.IN_PROGRESS -> Icons.Default.RadioButtonChecked
                TodoStatus.PENDING     -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = iconTint
        )

        Spacer(Modifier.width(10.dp))

        // -- Label -----------------------------------------------------------
        if (isActive) {
            // Shimmer: solid onSurface + SrcAtop shimmerBrush — persis teknik Thinking..
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn)
                    }
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // -- Step number — right aligned ------------------------------------
        Text(
            text = "${item.id}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun MessageBubble(message: UiMessage) {
    val isUser = message.role == MessageRole.USER
    if (isUser) {
        // -- User bubble: measure actual text width, then shrink-wrap --
        val density = LocalDensity.current
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val maxBubbleWidthDp = (screenWidth * 0.7f).dp
        val hPad = 14.dp
        val vPad = 10.dp

        // Actual rendered content width — measured via onTextLayout
        var measuredWidth by remember(message.content) { mutableStateOf<Int?>(null) }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                modifier = if (measuredWidth != null) {
                    // Second pass: exact width
                    Modifier.width(with(density) { measuredWidth!!.toDp() } + hPad * 2)
                } else {
                    // First pass: max constraint
                    Modifier.widthIn(max = maxBubbleWidthDp)
                }
            ) {
                Text(
                    message.content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    onTextLayout = { result ->
                        val maxLineW = (0 until result.lineCount).maxOf {
                            result.getLineRight(it) - result.getLineLeft(it)
                        }
                        val rounded = kotlin.math.ceil(maxLineW).toInt()
                        if (measuredWidth == null || measuredWidth != rounded) {
                            measuredWidth = rounded
                        }
                    }
                )
            }
        }
    } else {
        // -- AI message: full-width markdown, no bubble --
        Column(modifier = Modifier.fillMaxWidth()) {
            if (message.content.isNotBlank()) {
                MarkdownText(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            if (message.toolExecutions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.toolExecutions.filter { it.name != "update_todo" }.forEach { execution ->
                        key(execution.toolCallId) {
                            ToolCallCard(execution)
                        }
                    }
                }
            }
        }
    } // close else
}
@Composable
fun ToolCallCard(execution: ToolExecution) {
    var expanded by remember(execution.toolCallId) { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val iosGreen = Color(0xFF34C759)
    val iosBlue  = Color(0xFF007AFF)
    val iosRed   = MaterialTheme.colorScheme.error

    val isSubagent = execution.name == "invoke_subagents"
    val canExpand  = (execution.status == ToolStatus.SUCCESS || execution.status == ToolStatus.ERROR) &&
        (execution.result != null || execution.children.isNotEmpty())
    val showChildren = isSubagent && execution.children.isNotEmpty() &&
        (execution.status == ToolStatus.RUNNING || expanded)

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
        ToolStatus.PENDING -> Icons.Default.RadioButtonUnchecked
        ToolStatus.RUNNING -> Icons.Default.Autorenew
        ToolStatus.SUCCESS -> Icons.Default.CheckCircle
        ToolStatus.ERROR   -> Icons.Default.Cancel
    }
    val toolIcon = when {
        execution.name == "invoke_subagents"                   -> Icons.Default.AccountTree
        execution.name == "update_memory"                      -> Icons.Default.Psychology
        execution.name == "create_reminder"                    -> Icons.Default.Alarm
        execution.name == "undo_change"                        -> Icons.Default.Undo
        execution.name.contains("read",     ignoreCase = true) -> Icons.Default.Description
        execution.name.contains("write",    ignoreCase = true) -> Icons.Default.Edit
        execution.name.contains("list",     ignoreCase = true) -> Icons.Default.FolderOpen
        execution.name.contains("find",     ignoreCase = true) -> Icons.Default.FindInPage
        execution.name.contains("shell",    ignoreCase = true) -> Icons.Default.Terminal
        execution.name.contains("delete",   ignoreCase = true) -> Icons.Default.Delete
        execution.name.contains("create",   ignoreCase = true) -> Icons.Default.CreateNewFolder
        execution.name.contains("transfer", ignoreCase = true) -> Icons.Default.ContentCopy
        execution.name.startsWith(com.amaya.intelligence.data.remote.mcp.McpClientManager.TOOL_PREFIX) -> Icons.Default.Extension
        else                                                    -> Icons.Default.Terminal
    }

    val shimmerTransition = rememberInfiniteTransition(label = "tool_shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue  = -500f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_x"
    )
    // Shimmer: centered gradient for symmetrical fade (alpha mask via DstIn)
    val gradientWidth = 600f
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 1f),    // Edge left: visible
            Color.White.copy(alpha = 0.7f),  // Fade in
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0f),    // Center: invisible
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.7f),  // Fade out
            Color.White.copy(alpha = 1f)     // Edge right: visible
        ),
        start = Offset(shimmerOffset - gradientWidth / 2, 0f),
        end   = Offset(shimmerOffset + gradientWidth / 2, 0f)
    )

    // STICKY HEADER PATTERN: Surface wraps Column, header always visible, expandable content below
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // HEADER ROW — always visible, fixed size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(toolIcon, null, modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                val toolLabel = formatToolName(execution.name, execution.arguments)
                Text(
                    text       = toolLabel,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .weight(1f)
                        .then(
                            if (execution.status == ToolStatus.RUNNING)
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent { drawContent(); drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn) }
                            else Modifier
                        )
                )

                Icon(statusIcon, null, modifier = Modifier.size(14.dp), tint = statusColor)

                if (canExpand) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // EXPANDABLE CONTENT - AnimatedVisibility for smooth expand/collapse
            AnimatedVisibility(
                visible = showChildren,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    execution.children.forEach { child ->
                        key(child.index) {
                            SubagentChildCard(
                                child        = child,
                                isDark       = isDark,
                                iosGreen     = iosGreen,
                                iosBlue      = iosBlue,
                                iosRed       = iosRed,
                                shimmerBrush = shimmerBrush
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && !isSubagent && execution.result != null,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(150))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        ToolResultPreview(
                            toolName  = execution.name,
                            arguments = execution.arguments,
                            result    = execution.result ?: "",
                            isDark    = isDark
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && isSubagent && execution.children.isEmpty() && execution.result != null,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(150))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
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
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubagentChildCard(
    child: SubagentExecution,
    isDark: Boolean,
    iosGreen: Color,
    iosBlue: Color,
    iosRed: Color,
    shimmerBrush: Brush
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

    // STICKY HEADER PATTERN: Surface + Column, header fixed, content expandable with AnimatedVisibility
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // HEADER ROW — always visible, fixed size
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

                val taskScrollState = rememberScrollState()
                LaunchedEffect(child.index) {
                    delay(600)
                    while (true) {
                        if (taskScrollState.maxValue > 0) {
                            taskScrollState.animateScrollTo(
                                taskScrollState.maxValue,
                                animationSpec = tween((taskScrollState.maxValue * 6).coerceIn(2000, 8000), easing = LinearEasing)
                            )
                            delay(1000)
                            taskScrollState.animateScrollTo(0, animationSpec = tween(300))
                            delay(800)
                        } else {
                            delay(500)
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
                    softWrap   = false,
                    modifier   = Modifier
                        .weight(1f)
                        .horizontalScroll(taskScrollState, enabled = false)
                        .then(
                            if (child.status == ToolStatus.RUNNING)
                                Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent { drawContent(); drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn) }
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
                    ToolStatus.RUNNING -> Text(
                        "Running\u2026", style = MaterialTheme.typography.labelSmall,
                        color = iosBlue, fontWeight = FontWeight.Medium
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

            // EXPANDABLE CONTENT - AnimatedVisibility for smooth expand/collapse
            AnimatedVisibility(
                visible = expanded && child.result != null,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(150))
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
private fun ToolResultPreview(
    toolName: String,
    arguments: Map<String, Any?>,
    result: String,
    isDark: Boolean
) {
    val codeBlockBg   = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val codeTextColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
    val metaColor     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    @Suppress("UNCHECKED_CAST")
    val args = arguments

    when (toolName) {
        "read_file" -> {
            val infoOnly = args["info_only"] as? Boolean ?: false
            val paths    = args["paths"] as? List<*>
            when {
                infoOnly || (paths == null && result.trim().startsWith("Path:")) -> {
                    result.lines().filter { it.contains(":") }.forEach { line ->
                        val idx = line.indexOf(":")
                        val k = line.substring(0, idx).trim()
                        val v = line.substring(idx + 1).trim()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(k, style = MaterialTheme.typography.labelSmall,
                                color = metaColor, modifier = Modifier.width(90.dp))
                            Text(v, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                paths != null -> {
                    val sections = result.split(Regex("(?=^=== )"), limit = 50)
                    sections.filter { it.isNotBlank() }.forEach { section ->
                        val lines = section.lines()
                        val name  = lines.firstOrNull()?.removePrefix("===")?.removeSuffix("===")?.trim() ?: ""
                        val count = lines.drop(1).count { it.isNotBlank() }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null,
                                modifier = Modifier.size(12.dp), tint = metaColor)
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                            Text("$count lines", style = MaterialTheme.typography.labelSmall,
                                color = metaColor, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                else -> {
                    val path  = args["path"]?.toString() ?: ""
                    val lines = result.lines()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(path.substringAfterLast("/"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f))
                        Text("${lines.size} lines", style = MaterialTheme.typography.labelSmall,
                            color = metaColor, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            lines.take(12).forEachIndexed { idx, line ->
                                Row {
                                    Text("${idx + 1}".padStart(3), fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp, color = metaColor,
                                        modifier = Modifier.width(28.dp))
                                    Text(line, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp, color = codeTextColor,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (lines.size > 12) {
                                Spacer(Modifier.height(4.dp))
                                Text("  ⋯  ${lines.size - 12} more lines",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metaColor, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        "write_file", "edit_file" -> {
            val path     = args["path"]?.toString() ?: ""
            val filename = path.substringAfterLast("/")
            val lines    = result.lines()
            val backupLine = lines.firstOrNull { it.contains("Backup") || it.contains(".bak") }
            val sizeLine   = lines.firstOrNull { it.contains("KB") || it.contains("MB") || it.contains("chars") }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(filename, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                sizeLine?.let { Text(it.trim(), style = MaterialTheme.typography.labelSmall, color = metaColor) }
                backupLine?.let { Text(it.trim(), style = MaterialTheme.typography.labelSmall, color = metaColor) }
            }
        }

        "run_shell" -> {
            val command = args["command"]?.toString() ?: ""
            Column {
                Surface(shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("$ $command", style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(result.trim().let { if (it.length > 1500) it.take(1500) + "\n⋯" else it },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, lineHeight = 18.sp, fontSize = 11.sp),
                        color = codeTextColor,
                        modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState()))
                }
            }
        }

        "find_files" -> {
            val lines    = result.lines().filter { it.isNotBlank() }
            val isSearch = args["content"] != null
            Column {
                Text("${lines.size} ${if (isSearch) "matches" else "files"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                lines.take(10).forEach { line ->
                    Row(modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSearch) Icons.Default.Search else Icons.Default.Description,
                            null, modifier = Modifier.size(11.dp), tint = metaColor)
                        Spacer(Modifier.width(6.dp))
                        Text(line.trim(), style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (lines.size > 10) Text("  ⋯  ${lines.size - 10} more",
                    style = MaterialTheme.typography.labelSmall, color = metaColor)
            }
        }

        "list_files" -> {
            val lines = result.lines().filter { it.isNotBlank() }
            Column {
                Text("${lines.size} items", style = MaterialTheme.typography.labelSmall,
                    color = metaColor, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                lines.take(8).forEach { line ->
                    val isDir = line.trim().endsWith("/")
                    Row(modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description,
                            null, modifier = Modifier.size(12.dp),
                            tint = if (isDir) Color(0xFF007AFF) else metaColor)
                        Spacer(Modifier.width(6.dp))
                        Text(line.trim(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (lines.size > 8) Text("  ⋯  ${lines.size - 8} more",
                    style = MaterialTheme.typography.labelSmall, color = metaColor)
            }
        }


        "create_reminder" -> {
            val title    = args["title"]?.toString() ?: ""
            val datetime = args["datetime"]?.toString() ?: ""
            val repeat   = args["repeat"]?.toString() ?: "once"
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("$datetime  ·  $repeat", style = MaterialTheme.typography.labelSmall, color = metaColor)
            }
        }

        "update_memory" -> {
            val content = args["content"]?.toString() ?: ""
            val target  = args["target"]?.toString() ?: "daily"
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null,
                        modifier = Modifier.size(13.dp), tint = metaColor)
                    Spacer(Modifier.width(4.dp))
                    Text("Saved to $target", style = MaterialTheme.typography.labelSmall, color = metaColor)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = codeBlockBg,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(content.take(200), style = MaterialTheme.typography.bodySmall,
                        color = codeTextColor.copy(alpha = 0.8f),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(8.dp))
                }
            }
        }

        else -> {
            Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg,
                modifier = Modifier.fillMaxWidth()) {
                Text(result.trim().let { if (it.length > 1500) it.take(1500) + "\n⋯" else it },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, lineHeight = 18.sp, fontSize = 11.sp),
                    color = codeTextColor,
                    modifier = Modifier.padding(10.dp).horizontalScroll(rememberScrollState()))
            }
        }
    }
}

private fun formatToolName(name: String, args: Map<String, Any?>?): String {
    // Helper: extract just the filename from a full path
    fun fileName(key: String) = args?.get(key)?.toString()?.substringAfterLast("/")?.take(30) ?: ""
    fun filePath(key: String) = args?.get(key)?.toString()?.let {
        if (it.length > 28) "…" + it.takeLast(26) else it
    } ?: ""

    @Suppress("UNCHECKED_CAST")
    return when (name) {
        // ── File tools ─────────────────────────────────────────────────────
        "read_file"  -> {
            val paths = args?.get("paths") as? List<*>
            val infoOnly = args?.get("info_only") as? Boolean ?: false
            when {
                paths != null      -> "Read  ${paths.size} files"
                infoOnly           -> "Stat  ${fileName("path")}"
                else               -> "Read  ${fileName("path")}"
            }
        }
        "write_file"        -> "Write  ${fileName("path")}"
        "edit_file"         -> {
            val hasDiff = args?.get("diff") != null
            if (hasDiff) "Patch  ${fileName("path")}"
            else         "Edit  ${fileName("path")}"
        }
        "delete_file"       -> "Delete  ${fileName("path")}"
        "transfer_file"     -> {
            val src  = fileName("source")
            val dst  = fileName("destination")
            val mode = args?.get("mode")?.toString() ?: "copy"
            if (mode == "move") "Move  $src → $dst" else "Copy  $src → $dst"
        }
        "create_directory"  -> "Mkdir  ${fileName("path")}/"
        "list_files"        -> "List  ${filePath("path")}"
        "find_files"        -> {
            val content = args?.get("content")?.toString()
            val pattern = args?.get("pattern")?.toString()
            when {
                content != null -> "Search  \"${content.take(22)}\""
                pattern != null -> "Find  $pattern"
                else            -> "Find  files"
            }
        }
        "undo_change"       -> "Undo  ${fileName("path")}"
        // ── Shell ───────────────────────────────────────────────────────────
        "run_shell"         -> "$  ${args?.get("command")?.toString()?.take(32) ?: ""}"
        // ── AI tools ───────────────────────────────────────────────────────
        "update_memory"     -> "Memory  ${args?.get("target")?.toString() ?: "daily"}"
        "create_reminder"   -> "Remind  ${args?.get("title")?.toString()?.take(20) ?: ""}"
        "invoke_subagents"  -> {
            @Suppress("UNCHECKED_CAST")
            val subagents = args?.get("subagents") as? List<Map<String, Any?>>
            if (subagents.isNullOrEmpty()) {
                "Subagents"
            } else {
                // Show ALL task names — MarqueeText handles overflow via auto-scroll
                val names = subagents.mapNotNull { it["task_name"]?.toString() }
                names.joinToString("  ·  ")
            }
        }
        // ── MCP tools ──────────────────────────────────────────────────────
        else -> if (name.startsWith(com.amaya.intelligence.data.remote.mcp.McpClientManager.TOOL_PREFIX)) {
            val parts = name.split("__")
            val server = parts.getOrNull(1) ?: ""
            val tool   = parts.getOrNull(2) ?: name
            "[$server]  $tool"
        } else {
            // Fallback: Title Case from snake_case
            name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}

@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    workspacePath: String? = null
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val placeholder = if (!workspacePath.isNullOrBlank()) {
        val name = workspacePath.substringAfterLast("/")
        "Ask anything on $name..."
    } else {
        "Ask anything..."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            inner()
                        }
                    }
                )

                Spacer(Modifier.width(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (isLoading) { onStop() }
                        else if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (isLoading) MaterialTheme.colorScheme.errorContainer else if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isLoading) MaterialTheme.colorScheme.onErrorContainer else if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    shape = CircleShape
                ) {
                    if (isLoading) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onPromptClick: (String) -> Unit,
    currentWorkspace: String?,
    onNewProjectClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // 7 rotating greetings
    val greetings = listOf(
        "What's on your mind?",
        "Ready when you are",
        "Let's get started",
        "How can I help?",
        "What should we tackle?",
        "Ask me anything",
        "Let's figure it out"
    )
    val now = remember { LocalDateTime.now() }
    val greeting = greetings[(now.dayOfYear + now.hour) % greetings.size]

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(24.dp))

        // Workspace dropdown
        Box(contentAlignment = Alignment.TopCenter) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        currentWorkspace?.substringAfterLast("/") ?: "Select Workspace",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (currentWorkspace != null) {
                    DropdownMenuItem(
                        text = { Text(currentWorkspace.substringAfterLast("/"), style = MaterialTheme.typography.bodyMedium) },
                        onClick = { expanded = false },
                        leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
                DropdownMenuItem(
                    text = { Text("New Project", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        onNewProjectClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
fun ScrollablePills(
    onPromptClick: (String) -> Unit
) {
    data class PillItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val prompt: String)

    val pills = listOf(
        PillItem(Icons.Default.Description, "Summarize", "Summarize this document"),
        PillItem(Icons.Default.Email, "Draft email", "Draft an email"),
        PillItem(Icons.Default.Lightbulb, "Explain", "Explain this concept"),
        PillItem(Icons.Default.Code, "Write code", "Write a script"),
        PillItem(Icons.Default.Edit, "Rewrite", "Rewrite this text"),
        PillItem(Icons.Default.Search, "Research", "Research this topic"),
        PillItem(Icons.Default.Translate, "Translate", "Translate this text"),
        PillItem(Icons.Default.CheckCircle, "Review", "Review this code")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pills.forEach { pill ->
                Surface(
                    onClick = { onPromptClick(pill.prompt) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(pill.icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(pill.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Left gradient shadow
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(20.dp)
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            Color.Transparent
                        )
                    )
                )
        )

        // Right gradient shadow
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(20.dp)
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }
}

@Composable
fun LoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    // Shimmer: centered gradient for symmetrical fade (alpha mask via DstIn)
    val gradientWidth = 600f
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 1f),    // Edge left: visible
            Color.White.copy(alpha = 0.7f),  // Fade in
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0f),    // Center: invisible
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.7f),  // Fade out
            Color.White.copy(alpha = 1f)     // Edge right: visible
        ),
        start  = Offset(translateAnim - gradientWidth / 2, 0f),
        end    = Offset(translateAnim + gradientWidth / 2, 0f)
    )

    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            "Thinking..",
            style      = MaterialTheme.typography.bodyLarge,
            fontStyle  = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface, // MUST be solid, not alpha
            modifier   = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn)
                }
        )
    }
}

@Composable
fun ConfirmationDialog(request: ConfirmationRequest, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp)) },
        title = { Text("Approval Required", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                Text(request.reason, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        request.details,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = CircleShape) { Text("Allow Action") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Deny") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

