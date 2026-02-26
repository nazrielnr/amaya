package com.opencode.mobile.ui.chat

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
import com.opencode.mobile.data.remote.api.MessageRole
import com.opencode.mobile.tools.ConfirmationRequest
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val confirmationRequest by viewModel.confirmationRequest.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showModelSelector by remember { mutableStateOf(false) }

    // Active agent derived from settings
    val agentConfigs  = uiState.agentConfigs
    val activeAgentId = uiState.activeAgentId
    val activeAgent   = agentConfigs.find { it.id == activeAgentId } ?: agentConfigs.firstOrNull()
    val selectedModel = uiState.selectedModel.ifBlank { activeAgent?.modelId ?: "" }

    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter { it.content.isNotBlank() || it.toolExecutions.isNotEmpty() }
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.lastIndex)
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
            var isSearchExpanded by remember { mutableStateOf(false) }
            val sidebarWidth = if (isSearchExpanded) Modifier.fillMaxWidth() else Modifier.width(280.dp)

            ModalDrawerSheet(
                modifier = sidebarWidth.fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = androidx.compose.ui.graphics.RectangleShape
            ) {
                if (isSearchExpanded) {
                    BackHandler { isSearchExpanded = false }
                    // ── Fullscreen Search Mode ──
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var searchQuery by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search history...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton({ searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                                    }
                                },
                                shape = CircleShape,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                            TextButton(onClick = { isSearchExpanded = false }, modifier = Modifier.padding(start = 8.dp)) {
                                Text("Cancel")
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Your chat history search will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // ── Normal Sidebar Mode ──
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Spacer(Modifier.height(16.dp))

                        // Search bar trigger + quick actions
                        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                            Surface(
                                onClick = { isSearchExpanded = true },
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Search", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("New chat", style = MaterialTheme.typography.bodyLarge) },
                                selected = false,
                                onClick = { viewModel.clearConversation(); scope.launch { drawerState.close() } },
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                                modifier = Modifier.height(48.dp)
                            )
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("New project", style = MaterialTheme.typography.bodyLarge) },
                                selected = false,
                                onClick = { onNavigateToWorkspace(); scope.launch { drawerState.close() } },
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                                modifier = Modifier.height(48.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Recent conversations
                        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            if (conversations.isEmpty()) {
                                item {
                                    Text(
                                        "No conversations yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(conversations.size) { index ->
                                    val conv = conversations[index]
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                conv.title.ifEmpty { "New Chat" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        selected = false,
                                        onClick = { viewModel.loadConversation(conv.id); scope.launch { drawerState.close() } },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(48.dp),
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        // Settings footer
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()) {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) },
                                label = { Text("Settings", style = MaterialTheme.typography.bodyLarge) },
                                selected = false,
                                onClick = { onNavigateToSettings(); scope.launch { drawerState.close() } },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(48.dp),
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Box {
                                Surface(
                                    onClick = { showModelSelector = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            (activeAgent?.name ?: selectedModel).ifBlank { "Select Agent" }
                                                .let { if (it.length > 20) it.take(18) + "…" else it },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Select Model",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                            // ── Model / Agent Selector Dropdown ──────────────────────────
                                DropdownMenu(
                                    expanded = showModelSelector,
                                    onDismissRequest = { showModelSelector = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (agentConfigs.isEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "No agents configured",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            },
                                            onClick = { showModelSelector = false }
                                        )
                                    } else {
                                        agentConfigs.forEach { agent ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            agent.name.ifBlank { "Unnamed Agent" },
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            agent.modelId,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    if (agent.id == activeAgentId) {
                                                        Icon(
                                                            androidx.compose.material.icons.Icons.Default.CheckCircle,
                                                            null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setSelectedAgent(agent)
                                                    showModelSelector = false
                                                }
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
                            WorkspaceTokenChip(
                                workspacePath = uiState.workspacePath,
                                totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
                    AnimatedVisibility(visible = uiState.error != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text(uiState.error ?: "", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }

                    // Scrollable pills — only on welcome screen
                    if (uiState.messages.isEmpty()) {
                        ScrollablePills(
                            onPromptClick = { viewModel.sendMessage(it) }
                        )
                    }

                    ChatInput(
                        onSend = { viewModel.sendMessage(it) },
                        onStop = { viewModel.stopGeneration() },
                        isLoading = uiState.isLoading
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (uiState.messages.isEmpty()) {
                    WelcomeScreen(
                        onPromptClick = { viewModel.sendMessage(it) },
                        currentWorkspace = uiState.workspacePath,
                        onNewProjectClick = onNavigateToWorkspace
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayMessages) { message -> MessageBubble(message) }

                        val lastIsUser = displayMessages.lastOrNull()?.role == MessageRole.USER
                        val lastIsEmptyAi = displayMessages.lastOrNull()?.let {
                            it.role == MessageRole.ASSISTANT && it.content.isBlank() && it.toolExecutions.isEmpty()
                        } ?: false

                        if (uiState.isLoading && (lastIsUser || lastIsEmptyAi)) {
                            item { LoadingIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceTokenChip(
    workspacePath: String?,
    totalTokens: Int
) {
    val tokenColor = when {
        totalTokens > 100_000 -> MaterialTheme.colorScheme.error
        totalTokens > 50_000 -> Color(0xFFFF9800) // amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val workspaceName = workspacePath?.substringAfterLast("/") ?: "No workspace"
    val tokenDisplay = if (totalTokens > 0) " · ${formatTokenCount(totalTokens)}" else ""

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$workspaceName$tokenDisplay",
                style = MaterialTheme.typography.labelSmall,
                color = tokenColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTokenCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
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
        val bg = if (isExit0) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
        val fg = if (isExit0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
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
fun ChatInput(onSend: (String) -> Unit, onStop: () -> Unit, isLoading: Boolean) {
    var text by remember { mutableStateOf("") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

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
                            if (text.isEmpty()) Text("Ask anything...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
