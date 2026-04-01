package com.amaya.intelligence.ui.screens.chat.shared

import com.amaya.intelligence.ui.viewmodels.ChatViewModel

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.data.local.entity.ConversationEntity
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.ui.components.shared.ConfirmationDialog
import com.amaya.intelligence.ui.components.shared.ConversationModeSheet
import com.amaya.intelligence.ui.components.shared.LocalhostLinkBottomSheet
import com.amaya.intelligence.ui.components.shared.LocalhostLinkInfo
import com.amaya.intelligence.ui.components.shared.LocalhostLinkInfoParser
import com.amaya.intelligence.ui.components.shared.ModelSelectorSheet
import com.amaya.intelligence.ui.components.shared.ScrollablePills
import com.amaya.intelligence.ui.components.local.SessionInfoButton
import com.amaya.intelligence.ui.components.local.SessionInfoSheet
import com.amaya.intelligence.ui.components.local.TodoPill
import com.amaya.intelligence.ui.components.local.TodoSheet
import com.amaya.intelligence.utils.NetworkUtils
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    activeReminderCount: Int = -1,
    isRemoteModeOverride: Boolean? = null,
    config: ChatScreenConfig? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToRemoteSession: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val confirmationRequest by viewModel.confirmationRequest.collectAsState()
    val todoItems by viewModel.todoItems.collectAsState()
    val localReminderCount by viewModel.activeReminderCount.collectAsState()
    val effectiveReminderCount = if (activeReminderCount >= 0) activeReminderCount else localReminderCount
    val scrollEventFlow = viewModel.scrollEvent
    val conversationsFlow = viewModel.conversations

    val isRemoteMode = isRemoteModeOverride ?: uiState.sessionMode.isRemote()
    val connectionState = uiState.connectionState
    val workspaces by viewModel.workspaces.collectAsState()

    // Action delegates
    val doSendMessage: (String) -> Unit = { viewModel.sendMessage(it) }
    val doSendMessageWithImage: (String, String, String, String) -> Unit = { content, base64, mime, name ->
        viewModel.sendMessageWithImage(content, base64, mime, name)
    }
    val doStopGeneration: () -> Unit = { viewModel.stopGeneration() }
    val doClearConversation: () -> Unit = { viewModel.clearConversation() }
    val doRespondToConfirmation: (Boolean) -> Unit = { viewModel.respondToConfirmation(it) }
    val doSetSelectedAgent: (String) -> Unit = { viewModel.setSelectedAgent(it) }
    val doLoadConversation: (Long) -> Unit = { viewModel.loadConversation(it) }
    val doDeleteConversation: (Long) -> Unit = { viewModel.deleteConversation(it) }
    val doClearError: () -> Unit = { viewModel.clearError() }
    val doHasMoreConversations: () -> Boolean = { viewModel.hasMoreConversations() }
    val doLoadMoreConversations: () -> Unit = { viewModel.loadMoreConversations() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showModelSelector by remember { mutableStateOf(false) }
    var showSessionInfo by remember { mutableStateOf(false) }
    var showTodoSheet by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var inputBarHeight by remember { mutableStateOf(0) }
    var attachedFilePath by remember(uiState.conversationId) { mutableStateOf<String?>(null) }
    var attachedImageBase64 by remember(uiState.conversationId) { mutableStateOf<String?>(null) }
    var attachedImageMimeType by remember(uiState.conversationId) { mutableStateOf<String?>(null) }
    var attachedImageName by remember(uiState.conversationId) { mutableStateOf<String?>(null) }
    var showConversationModeSheet by remember { mutableStateOf(false) }

    var showLocalhostLinkSheet by remember { mutableStateOf(false) }
    var selectedLocalhostLink by remember { mutableStateOf<LocalhostLinkInfo?>(null) }
    var localIp by remember { mutableStateOf("127.0.0.1") }
    
    // Get local IP address on launch as fallback
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            localIp = NetworkUtils.getLocalIpAddress()
        }
    }

    // Lifted input text state
    var inputText by remember(uiState.conversationId) { mutableStateOf("") }

    // Use server IP from extension if available, otherwise fallback to device local IP
    val serverIp = uiState.serverIp ?: localIp

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val resolvedPath = withContext(Dispatchers.IO) {
                    var path: String? = null
                    try {
                        context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) path = cursor.getString(0)
                        }
                    } catch (_: Exception) { }

                    if (path == null) {
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "file"
                        val cacheFile = File(context.cacheDir, "attach_$fileName")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                cacheFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            path = cacheFile.absolutePath
                        } catch (_: Exception) { }
                    }
                    path
                }
                attachedFilePath = resolvedPath
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val contentResolver = context.contentResolver
                        val rawMimeType = contentResolver.getType(uri) ?: "image/*"
                        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "image"
                        
                        // Load and compress image to avoid API limits
                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream == null) return@withContext null
                        
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        
                        if (bitmap == null) return@withContext null
                        
                        // Scale down if too large (max 2048px on longest side)
                        val maxDim = 2048
                        val width = bitmap.width
                        val height = bitmap.height
                        val scaledBitmap = if (width > maxDim || height > maxDim) {
                            val scale = maxDim.toFloat() / maxOf(width, height)
                            val newWidth = (width * scale).toInt()
                            val newHeight = (height * scale).toInt()
                            android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        } else {
                            bitmap
                        }
                        
                        // Adaptive compression: compress until under 180KB base64 (safe for inline upload)
                        // This avoids artifact upload issues with large images
                        // Base64 is ~33% larger than binary, so target ~135KB binary
                        val maxBinarySize = 135_000 // ~135KB binary = ~180KB base64
                        var quality = 85
                        var bytes: ByteArray
                        val outputStream = java.io.ByteArrayOutputStream()
                        
                        do {
                            outputStream.reset()
                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
                            bytes = outputStream.toByteArray()
                            quality -= 10
                        } while (bytes.size > maxBinarySize && quality >= 30)
                        
                        outputStream.close()
                        
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        android.util.Log.d("ChatScreen", "Image compressed: ${bytes.size} bytes -> ${base64.length} base64 chars, final quality=$quality")
                        
                        // Recycle bitmaps to free memory
                        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                        bitmap.recycle()
                        
                        // Always use JPEG since we compressed as JPEG
                        Triple(base64, "image/jpeg", fileName.removeSuffix(".png").removeSuffix(".webp") + ".jpg")
                    } catch (e: Exception) {
                        android.util.Log.e("ChatScreen", "Image processing failed", e)
                        null
                    }
                }
                result?.let { (base64, mime, name) ->
                    android.util.Log.d("ChatScreen", "Image attached: base64Len=${base64.length}, mime=$mime, name=$name")
                    attachedImageBase64 = base64
                    attachedImageMimeType = mime
                    attachedImageName = name
                }
            }
        }
    }

    val activeAgentId = uiState.activeAgentId
    val activeAgent = uiState.agentConfigs.find { it.id == activeAgentId } ?: uiState.agentConfigs.firstOrNull()
    val selectedModel = uiState.selectedModel.ifBlank { activeAgent?.modelId ?: "" }
    val selectedAgentFallbackLabel = config?.selectedAgentFallbackLabel ?: "Select Agent"
    val streamingLabel = config?.streamingLabel ?: "Streaming"
    val idleLabel = config?.idleLabel ?: "Idle"

    val displayMessages = remember(uiState.messages) {
        uiState.messages.filter {
            it.content.isNotBlank() ||
            !it.thinking.isNullOrBlank() ||
            it.steps.isNotEmpty()
        }
    }

    // Auto-scroll
    var shouldAutoScroll by remember { mutableStateOf(true) }

    val performScrollToBottom: suspend (Boolean) -> Unit = { animated ->
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total > 0) {
            if (animated) {
                val lastVisible = info.visibleItemsInfo.lastOrNull { it.index == total - 1 }
                if (lastVisible != null) {
                    val distance = (lastVisible.offset + lastVisible.size) - info.viewportEndOffset
                    if (distance > 0) {
                        listState.animateScrollBy(distance.toFloat())
                    }
                } else {
                    listState.animateScrollToItem(total - 1)
                    val newInfo = listState.layoutInfo
                    val newLast = newInfo.visibleItemsInfo.lastOrNull { it.index == total - 1 }
                    if (newLast != null) {
                        val newDistance = (newLast.offset + newLast.size) - newInfo.viewportEndOffset
                        if (newDistance > 0) {
                            listState.animateScrollBy(newDistance.toFloat())
                        }
                    }
                }
            } else {
                listState.scrollToItem(total - 1, Int.MAX_VALUE)
            }
        }
    }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) {
                shouldAutoScroll = false
            }
        }
    }

    LaunchedEffect(listState.canScrollForward) {
        if (!listState.canScrollForward) {
            shouldAutoScroll = true
        }
    }

    LaunchedEffect(Unit) {
        scrollEventFlow.collect { reason ->
            when (reason) {
                ChatViewModel.ScrollReason.NEW_MESSAGE -> {
                    shouldAutoScroll = true
                    delay(150)
                    performScrollToBottom(true)
                }
                ChatViewModel.ScrollReason.NEW_TOOL -> {
                    if (shouldAutoScroll) {
                        delay(150)
                        performScrollToBottom(true)
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.isStreaming, shouldAutoScroll) {
        if (!uiState.isStreaming || !shouldAutoScroll) return@LaunchedEffect
        while (true) {
            performScrollToBottom(false)
            delay(50)
        }
    }

    LaunchedEffect(displayMessages.size) {
        if (shouldAutoScroll) {
            delay(100)
            performScrollToBottom(true)
        }
    }

    LaunchedEffect(isRemoteMode, displayMessages.size) {
        if (!isRemoteMode || displayMessages.isEmpty()) return@LaunchedEffect

        // Remote prompts can arrive outside the local send path, so no scroll event is emitted.
        // Keep the latest remote turn visible whenever the message list grows.
        if (shouldAutoScroll) {
            delay(120)
            performScrollToBottom(true)
        }
    }

    confirmationRequest?.let { request ->
        ConfirmationDialog(
            request = request,
            onConfirm = { doRespondToConfirmation(true) },
            onDismiss = { doRespondToConfirmation(false) }
        )
    }

    BackHandler(enabled = uiState.messages.isNotEmpty() || (isRemoteMode && isRemoteModeOverride == true)) {
        if (isRemoteMode) onExit() else doClearConversation()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val conversations by conversationsFlow.collectAsState()

    // WindowInsets
    val statusBarInsets = WindowInsets.statusBars.asPaddingValues()
    val statusBarHeight = statusBarInsets.calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerDp = statusBarHeight + 64.dp
    val bottomDp = 80.dp + navBarHeight
    val bgColor = MaterialTheme.colorScheme.background

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ChatDrawerContent(
                drawerState = drawerState,
                isRemoteMode = isRemoteMode,
                uiState = uiState,
                connectionState = connectionState,
                conversations = conversations,
                onLoadConversation = doLoadConversation,
                onDeleteConversation = doDeleteConversation,
                onClearConversation = doClearConversation,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToWorkspace = onNavigateToWorkspace,
                onNavigateToRemoteSession = onNavigateToRemoteSession,
                onExit = onExit,
                hasMoreConversations = doHasMoreConversations,
                loadMoreConversations = doLoadMoreConversations,
                scope = scope
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val drawerOpen = drawerState.isOpen
            var showSkeletonOverride by remember { mutableStateOf(false) }

            LaunchedEffect(uiState.conversationId) {
                if (isRemoteMode && uiState.conversationId != null) {
                    showSkeletonOverride = true
                    delay(1200)
                    showSkeletonOverride = false
                } else {
                    showSkeletonOverride = false
                }
            }

            // Content area
            if (uiState.messages.isEmpty()) {
                ChatEmptyContent(
                    isRemoteMode = isRemoteMode,
                    connectionState = connectionState,
                    uiState = uiState,
                    showSkeletonOverride = showSkeletonOverride,
                    headerDp = headerDp,
                    bottomDp = bottomDp,
                    drawerOpen = drawerOpen,
                    onInputTextChange = { inputText = it },
                    onSendMessage = doSendMessage,
                    onNavigateToWorkspace = onNavigateToWorkspace,
                    workspaces = workspaces
                )
            } else {
                ChatMessageList(
                    listState = listState,
                    displayMessages = displayMessages,
                    isLoading = uiState.isLoading,
                    isRemoteMode = isRemoteMode,
                    headerDp = headerDp,
                    inputBarHeight = inputBarHeight,
                    drawerOpen = drawerOpen,
                    onToolAccept = if (isRemoteMode) { execution -> viewModel.respondToToolInteraction(execution.toolCallId, true) } else null,
                    onToolDecline = if (isRemoteMode) { execution -> viewModel.respondToToolInteraction(execution.toolCallId, false) } else null,
                    onLocalhostLinkClick = { annotationItem ->
                        selectedLocalhostLink = LocalhostLinkInfoParser.parse(annotationItem, serverIp)
                        showLocalhostLinkSheet = true
                    },
                    onContentResized = {
                        if (shouldAutoScroll) {
                            scope.launch { performScrollToBottom(true) }
                        }
                    },
                    onScrollToBottomClick = {
                        shouldAutoScroll = true
                        scope.launch { performScrollToBottom(true) }
                    },
                    shouldAutoScroll = shouldAutoScroll,
                    scope = scope
                )
            }

            // Localhost Link Bottom Sheet
            if (showLocalhostLinkSheet && selectedLocalhostLink != null) {
                LocalhostLinkBottomSheet(
                    linkInfo = selectedLocalhostLink!!,
                    localIp = serverIp,
                    onDismiss = {
                        showLocalhostLinkSheet = false
                        selectedLocalhostLink = null
                    },
                    onCopyLink = { url ->
                        // Copy link callback - can be used for analytics or additional handling
                    },
                    onOpenLink = { url ->
                        // Open link callback - can be used for analytics or additional handling
                    }
                )
            }

            // Gradient scrim for status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopStart)
                    .background(LocalAmayaGradients.current.topScrim)
            )

            // TopAppBar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showModelSelector = true }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (activeAgent?.name ?: selectedModel).ifBlank { selectedAgentFallbackLabel }
                                        .let { if (it.length > 22) it.take(20) + "…" else it },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, "Select Model",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    keyboardController?.hide()
                                    scope.launch { drawerState.open() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        if (todoItems.isNotEmpty()) {
                            TodoPill(
                                items = todoItems,
                                onClick = { showTodoSheet = true }
                            )
                        }

                        if (isRemoteMode) {
                            val isStreaming = uiState.isStreaming
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isStreaming) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (isStreaming) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isStreaming) streamingLabel else idleLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isStreaming) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.refreshState() }) {
                                Icon(Icons.Default.Refresh, "Refresh")
                            }
                        }

                        if (!isRemoteMode) {
                            SessionInfoButton(
                                totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens,
                                activeModel = selectedModel,
                                activeReminderCount = effectiveReminderCount,
                                onClick = { showSessionInfo = true }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            // Bottom section
            ChatBottomSection(
                modifier = Modifier.align(Alignment.BottomStart),
                inputText = inputText,
                onInputTextChange = { inputText = it },
                isRemoteMode = isRemoteMode,
                uiState = uiState,
                connectionState = connectionState,
                drawerOpen = drawerOpen,
                bgColor = bgColor,
                attachedFilePath = attachedFilePath,
                attachedImageBase64 = attachedImageBase64,
                attachedImageMimeType = attachedImageMimeType,
                attachedImageName = attachedImageName,
                filePicker = filePicker,
                imagePicker = imagePicker,
                keyboardController = keyboardController,
                scope = scope,
                onClearError = doClearError,
                onSendMessage = doSendMessage,
                onSendMessageWithImage = doSendMessageWithImage,
                onClearImageAttachment = {
                    attachedImageBase64 = null
                    attachedImageMimeType = null
                    attachedImageName = null
                },
                onStopGeneration = doStopGeneration,
                onNavigateToWorkspace = onNavigateToWorkspace,
                onShowConversationModeSheet = { showConversationModeSheet = true },
                onInputBarHeightChange = { inputBarHeight = it }
            )
        }
    }

    // Bottom sheets
    if (showTodoSheet && todoItems.isNotEmpty()) {
        TodoSheet(
            items = todoItems,
            onDismiss = { showTodoSheet = false }
        )
    }

    if (showConversationModeSheet && isRemoteMode) {
        ConversationModeSheet(
            currentMode = uiState.conversationMode,
            onSelect = { mode ->
                viewModel.setConversationMode(mode)
                showConversationModeSheet = false
            },
            onDismiss = { showConversationModeSheet = false }
        )
    }

    if (showModelSelector) {
        ModelSelectorSheet(
            agentItems = uiState.agentConfigs,
            activeAgentId = activeAgentId,
            isRemote = isRemoteMode,
            onRefresh = { viewModel.refreshModels() },
            onSelect = { item ->
                doSetSelectedAgent(item.id)
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false }
        )
    }

    if (showSessionInfo && !isRemoteMode) {
        SessionInfoSheet(
            totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens,
            activeModel = selectedModel,
            activeReminderCount = effectiveReminderCount,
            onDismiss = { showSessionInfo = false }
        )
    }
}
