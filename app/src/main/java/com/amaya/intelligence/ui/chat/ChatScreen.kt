package com.amaya.intelligence.ui.chat

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoStatus
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
    val hasTodayMemory by viewModel.hasTodayMemory.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showModelSelector by remember { mutableStateOf(false) }
    var showSessionInfo by remember { mutableStateOf(false) }

    // Only show enabled agents in dropdown
    val agentConfigs   = uiState.agentConfigs.filter { it.enabled }
    val activeAgentId  = uiState.activeAgentId
    val activeAgent    = agentConfigs.find { it.id == activeAgentId } ?: agentConfigs.firstOrNull()
    val selectedModel  = uiState.selectedModel.ifBlank { activeAgent?.modelId ?: "" }

    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter { it.content.isNotBlank() || it.toolExecutions.isNotEmpty() }
    }

    // reverseLayout=true: index 0 = latest message at visual bottom
    // Scroll to 0 when user sends — latest always visible just below header
    val userMsgCount = remember(displayMessages) { displayMessages.count { it.role == MessageRole.USER } }
    LaunchedEffect(userMsgCount) { if (displayMessages.isNotEmpty()) listState.scrollToItem(0) }


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
                    // ── Header ──────────────────────────────────────────────
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

                    // ── Action buttons ──────────────────────────────────────
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

                    // ── Search bar ──────────────────────────────────────────
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

                    // ── Section label ───────────────────────────────────────
                    if (conversations.isNotEmpty()) {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    // ── Conversation list ────────────────────────────────────
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

                    // ── Divider ─────────────────────────────────────────────
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // ── Footer: Settings ────────────────────────────────────
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

            // ── 1. Content ────────────────────────────────────────────────────
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
                // reverseLayout=true: latest msg at visual bottom (index 0 rendered last = bottom)
                // contentPadding.bottom = headerDp so latest msg sits just below header
                // contentPadding.top = bottomDp so AI response has space above input bar
                val reversedMessages = remember(displayMessages) { displayMessages.reversed() }
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier.fillMaxSize(),
                    reverseLayout       = true,
                    contentPadding      = PaddingValues(
                        start  = 16.dp,
                        end    = 16.dp,
                        top    = bottomDp + 8.dp,   // space above input bar (visual top)
                        bottom = headerDp + 8.dp    // space below header (visual bottom)
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.isLoading) {
                        item(key = "loading") { LoadingIndicator() }
                    }
                    items(reversedMessages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            // ── 2. Gradient scrim — covers status bar area ────────────────────
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

            // ── 3. Floating header ────────────────────────────────────────────
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
                                                Text("Enable agents in Settings - AI Agents", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(agent.name.ifBlank { "Unnamed Agent" },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                    if (agent.modelId.isNotBlank()) {
                                                        Text(agent.modelId, style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.SmartToy,
                                                    null, modifier = Modifier.size(16.dp),
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
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
                            hasTodayMemory      = hasTodayMemory,
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
                ) { TodoBar(items = todoItems) }
            }

            // ── 4. Floating bottom input ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .imePadding()
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


    // ─── Session Info bottom sheet ───
    if (showSessionInfo) {
        SessionInfoSheet(
            totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens,
            activeModel = selectedModel,
            activeReminderCount = activeReminderCount,
            hasTodayMemory = hasTodayMemory,
            onDismiss = { showSessionInfo = false }
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  Session Info Button (replaces WorkspaceTokenChip)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoButton(
    totalTokens: Int,
    activeModel: String,
    activeReminderCount: Int,
    hasTodayMemory: Boolean,
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
            androidx.compose.ui.platform.LocalDensity
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
    hasTodayMemory: Boolean,
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
            SessionInfoRow(
                icon = Icons.Default.Psychology,
                label = "Memory today",
                value = if (hasTodayMemory) "Entries recorded" else "No entries yet",
                valueColor = if (hasTodayMemory) MaterialTheme.colorScheme.tertiary
                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
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

// ─────────────────────────────────────────────────────────────────────────────
//  TodoBar — collapsible task list shown below TopAppBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TodoBar(items: List<TodoItem>) {
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

    // ── Shimmer — identical technique to LoadingIndicator / Thinking.. ────
    // Key: teks HARUS warna solid (onSurface), shimmer di-overlay via SrcAtop.
    // baseShimmer = warna redup (teks saat tidak kena sorot)
    // peakShimmer = warna terang bergerak
    val isDark = isSystemInDarkTheme()
    val baseShimmer = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    val peakShimmer = if (isDark) Color.White       else Color.Black

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

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseShimmer, peakShimmer, peakShimmer, baseShimmer),
        start  = Offset(shimmerOffset, 0f),
        end    = Offset(shimmerOffset + 300f, 0f)
    )

    // Surface background — sedikit berbeda dari surface biasa agar terlihat sebagai banner
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ── Collapsed row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Step number pill ─────────────────────────────────────────
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

                // ── Label — shimmer if running, muted if not ─────────────────
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
                            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(brush = shimmerBrush, blendMode = BlendMode.SrcAtop)
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

                // ── Progress fraction ─────────────────────────────────────────
                Text(
                    text = "$completed/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.width(6.dp))

                // ── Chevron ───────────────────────────────────────────────────
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // ── Expanded list ─────────────────────────────────────────────────
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
        // ── Status icon ────────────────────────────────────────────────────
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

        // ── Label ───────────────────────────────────────────────────────────
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
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = shimmerBrush, blendMode = BlendMode.SrcAtop)
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

        // ── Step number — right aligned ────────────────────────────────────
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
        // ── User bubble: measure actual text width, then shrink-wrap ──
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
        // ── AI message: full-width markdown, no bubble ──
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
                    message.toolExecutions.forEach { execution ->
                        key(execution.toolCallId) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(500)) + expandVertically()
                            ) {
                                ToolCallCard(execution)
                            }
                        }
                    }
                }
            }
        }
    } // close else
}

@Composable
fun ToolCallCard(execution: ToolExecution) {
    val isExit0 = execution.result?.trim() == "exit 0"
    val isExit1 = execution.result?.trim() == "exit 1"

    if (isExit0 || isExit1) {
        val bg = if (isExit0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        val fg = if (isExit0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
        Surface(
            color = bg,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BuildCircle, null, modifier = Modifier.size(14.dp), tint = fg)
                Spacer(Modifier.width(6.dp))
                Text(execution.result!!.trim(), style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (execution.status) {
        ToolStatus.PENDING -> MaterialTheme.colorScheme.outline
        ToolStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ToolStatus.SUCCESS -> Color(0xFF4CAF50)
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    val containerColor = if (execution.status == ToolStatus.ERROR) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (execution.status == ToolStatus.ERROR) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(MaterialTheme.shapes.small),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { if (execution.result != null) expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BuildCircle, null, modifier = Modifier.size(16.dp), tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatToolName(execution.name, execution.arguments),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                if (execution.result != null) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = contentColor)
                }
                if (execution.status == ToolStatus.RUNNING) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
            AnimatedVisibility(visible = expanded) {
                execution.result?.let { result ->
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(color = contentColor.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = statusColor.copy(alpha = 0.8f))
                            Spacer(Modifier.width(6.dp))
                            Text("Execution Result", style = MaterialTheme.typography.labelSmall, color = contentColor)
                        }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                result.take(3000),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        if (result.length > 3000) {
                            Text("... (${result.length - 3000} more chars)", style = MaterialTheme.typography.labelSmall, color = contentColor, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatToolName(name: String, args: Map<String, Any?>?): String {
    val readable = name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    val preview = when (name) {
        "list_files", "read_file", "get_file_info" -> args?.get("path")?.toString()?.let { val short = if (it.length > 35) "..." + it.takeLast(32) else it; " - $short" } ?: ""
        "find_files" -> " - ${args?.get("pattern")}"
        "search_files" -> " - \"${args?.get("query")?.toString()?.take(25)}\""
        "run_shell" -> " - ${args?.get("command")?.toString()?.take(35)}"
        else -> ""
    }
    return readable + preview
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

    // Use Theme-aware colors instead of checking system theme
    val baseTextColor = MaterialTheme.colorScheme.onSurface 
    
    // Base shimmer is dim, moving shimmer is bright and colorful
    val baseShimmer = baseTextColor.copy(alpha = 0.3f)
    val movingShimmer = MaterialTheme.colorScheme.primary

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            baseShimmer,
            movingShimmer,
            baseShimmer
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f)
    )

    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            "Thinking..",
            style = MaterialTheme.typography.bodyLarge.copy(color = baseTextColor),
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f) // Allows blend modes to work correctly
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = shimmerBrush,
                        blendMode = BlendMode.SrcIn
                    )
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