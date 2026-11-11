package com.amaya.intelligence.ui.screens.chat.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import com.amaya.intelligence.ui.viewmodels.ChatViewModel

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.ai.displayName
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.ui.components.shared.ConversationModeSheet
import com.amaya.intelligence.ui.components.shared.LocalhostLinkBottomSheet
import com.amaya.intelligence.ui.components.shared.LocalhostLinkInfo
import com.amaya.intelligence.ui.components.shared.LocalhostLinkInfoParser
import com.amaya.intelligence.ui.components.shared.ModelSelectorSheet
import com.amaya.intelligence.ui.components.remote.WindowsBridgeChatPanelViewModel
import com.amaya.intelligence.ui.components.remote.WindowsBridgeSessionInfoSheet
import com.amaya.intelligence.ui.components.local.SessionInfoSheet
import com.amaya.intelligence.ui.components.local.TodoSheet
import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet
import com.amaya.intelligence.ui.activities.agent.local.LocalAgentConfigActivity
import com.amaya.intelligence.ui.activities.browser.BrowserOperatorActivity
import com.amaya.intelligence.util.NetworkUtils
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    bridgeViewModel: WindowsBridgeChatPanelViewModel = hiltViewModel(),
    activeReminderCount: Int = -1,
    isRemoteModeOverride: Boolean? = null,
    config: ChatScreenConfig? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToAgents: () -> Unit = {},
    onNavigateToRemoteSession: () -> Unit = {},
    onNavigateToOpencode: (() -> Unit)? = null,
    onExit: () -> Unit = {},
    sessionDisconnectName: String? = null,
    onConfirmSessionDisconnect: (() -> Unit)? = null,
    /**
     * Optional slot rendered below the top app bar and above the message list.
     * Used by opencode (Plan/Build pill, permission card) so the overlay moves
     * with the chat content when the drawer opens instead of sitting on top
     * of the drawer.
     */
    topOverlay: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val todoItems by viewModel.todoItems.collectAsState()
    val localReminderCount by viewModel.activeReminderCount.collectAsState()
    val effectiveReminderCount = if (activeReminderCount >= 0) activeReminderCount else localReminderCount
    val scrollEventFlow = viewModel.scrollEvent
    val conversationsFlow = viewModel.conversations
    val ownerName by viewModel.ownerLabel.collectAsState()
    val ownerLabel = ownerName ?: stringResource(when (uiState.assistantMode) {
        com.amaya.intelligence.domain.models.AssistantMode.CHAT -> R.string.chat
        com.amaya.intelligence.domain.models.AssistantMode.PROJECT -> R.string.project
        com.amaya.intelligence.domain.models.AssistantMode.AGENT -> R.string.agent
    })
    val activeAgentGroupLabel by viewModel.activeAgentGroupLabel.collectAsState()
    val activeAgentMembers by viewModel.activeAgentMembers.collectAsState()
    val activeDelegationTasks by viewModel.activeDelegationTasks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val agentGroups by viewModel.agentGroups.collectAsState()
    val allAgents by viewModel.allAgents.collectAsState()
    val allLocalConversations by viewModel.allLocalConversations.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()

    val isRemoteMode = isRemoteModeOverride ?: uiState.sessionMode.isRemote()
    val isBridgeMode = uiState.sessionMode ==
        com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode.WINDOWS_BRIDGE
    val bridgeState by bridgeViewModel.state.collectAsState()
    val connectionState = uiState.connectionState
    val workspaces by viewModel.workspaces.collectAsState()

    val doSendMessage: (String) -> Unit = remember(viewModel) { { viewModel.sendMessage(it) } }
    val doSendMessageWithImage: (String, String, String, String) -> Unit = remember(viewModel) {
        { content, base64, mime, name -> viewModel.sendMessageWithImage(content, base64, mime, name) }
    }
    val doStopGeneration: () -> Unit = remember(viewModel) { { viewModel.stopGeneration() } }
    val doClearConversation: () -> Unit = remember(viewModel) { { viewModel.clearConversation() } }
    val doSelectModel: (String) -> Unit = remember(viewModel) { { viewModel.selectModel(it) } }
    val doLoadConversation: (Long) -> Unit = remember(viewModel) { { viewModel.loadConversation(it) } }
    val doDeleteConversation: (Long) -> Unit = remember(viewModel) { { viewModel.deleteConversation(it) } }
    val doClearVisibleHistory: (Boolean) -> Unit = remember(viewModel) { { viewModel.clearVisibleHistory(it) } }
    val doCompactConversation: (String) -> Unit = remember(viewModel) { { focus -> viewModel.compactConversation(focus) } }
    val doCancelCompactConversation: () -> Unit = remember(viewModel) { { viewModel.cancelCompactConversation() } }
    val doClearError: () -> Unit = remember(viewModel) { { viewModel.clearError() } }
    val doHasMoreConversations: () -> Boolean = remember(viewModel) { { viewModel.hasMoreConversations() } }
    val doLoadMoreConversations: () -> Unit = remember(viewModel) { { viewModel.loadMoreConversations() } }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerVisible = drawerState.currentValue == DrawerValue.Open ||
        drawerState.targetValue == DrawerValue.Open

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(drawerState.targetValue, drawerState.isAnimationRunning) {
        if (drawerState.targetValue == DrawerValue.Open) {
            keyboardController?.hide()
        }
    }

    var showModelSelector by remember { mutableStateOf(false) }
    var showSessionInfo by remember { mutableStateOf(false) }
    var showBridgeSessionInfo by remember { mutableStateOf(false) }
    var showTodoSheet by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val inputBarHeight = remember { mutableIntStateOf(0) }
    val conversationKey = uiState.conversationId.orEmpty()
    val composerKey = "${uiState.assistantMode}:${uiState.ownerId}:${uiState.agentId}:${uiState.conversationId}"
    var attachedFilePath by remember(composerKey) { mutableStateOf<String?>(null) }
    var attachedImageBase64 by remember(composerKey) { mutableStateOf<String?>(null) }
    var attachedImageMimeType by remember(composerKey) { mutableStateOf<String?>(null) }
    var attachedImageName by remember(composerKey) { mutableStateOf<String?>(null) }
    var showConversationModeSheet by remember { mutableStateOf(false) }
    var showAgentMenu by remember { mutableStateOf(false) }
    var showDeleteAgentChatSheet by remember { mutableStateOf(false) }

    var showLocalhostLinkSheet by remember { mutableStateOf(false) }
    var selectedLocalhostLink by remember { mutableStateOf<LocalhostLinkInfo?>(null) }
    var localIp by remember { mutableStateOf("127.0.0.1") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { localIp = NetworkUtils.getLocalIpAddress() }
    }
    val inputText = remember(composerKey) { mutableStateOf("") }

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

                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream == null) return@withContext null

                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap == null) return@withContext null

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
                        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                        bitmap.recycle()

                        Triple(base64, "image/jpeg", fileName.removeSuffix(".png").removeSuffix(".webp") + ".jpg")
                    } catch (e: Exception) {
                        com.amaya.intelligence.util.errorLog("ChatScreen", "Image processing failed", e)
                        null
                    }
                }
                result?.let { (base64, mime, name) ->
                    attachedImageBase64 = base64
                    attachedImageMimeType = mime
                    attachedImageName = name
                }
            }
        }
    }

    val activeModelKey = uiState.activeModelKey
    val selectedModelItem = uiState.modelOptions.firstOrNull { it.id == activeModelKey }
    val selectedModel = uiState.selectedModel.ifBlank { selectedModelItem?.modelId.orEmpty() }
    val selectedModelFallbackLabel = stringResource(config?.selectedModelFallbackLabel ?: R.string.select_model)

    val onToolAccept: ((ToolExecution) -> Unit)? = remember(viewModel) {
        { execution: ToolExecution ->
            viewModel.respondToToolInteraction(execution.metadata["approvalId"] ?: execution.toolCallId, true)
        }
    }
    val onToolDecline: ((ToolExecution) -> Unit)? = remember(viewModel) {
        { execution: ToolExecution ->
            viewModel.respondToToolInteraction(execution.metadata["approvalId"] ?: execution.toolCallId, false)
        }
    }

    val displayMessages by remember(uiState.messages) {
        derivedStateOf {
            uiState.messages.filter {
                it.content.isNotBlank() ||
                !it.thinking.isNullOrBlank() ||
                it.steps.isNotEmpty()
            }
        }
    }

    val autoFollow = rememberChatAutoFollow(
        listState = listState,
        conversationKey = conversationKey,
        drawerVisible = drawerVisible,
        turnActive = uiState.isLoading || uiState.isStreaming,
        inputBarHeight = inputBarHeight,
        scrollEvents = scrollEventFlow
    )

    BackHandler(
        enabled = uiState.messages.isNotEmpty() ||
            (isRemoteMode && (isRemoteModeOverride == true || onConfirmSessionDisconnect != null))
    ) {
        if (isRemoteMode) {
            if (onConfirmSessionDisconnect != null) {
                showDisconnectDialog = true
            } else {
                onExit()
            }
        } else doClearConversation()
    }

    val conversations by conversationsFlow.collectAsState()
    val selectedModelLabel = (selectedModelItem?.name ?: selectedModel).ifBlank { selectedModelFallbackLabel }
    val activeConversationTitle = remember(conversations, allLocalConversations, uiState.conversationId) {
        (conversations + allLocalConversations).firstOrNull { it.id.toString() == uiState.conversationId }?.title
    }
    val activeAgentName = allAgents.firstOrNull { it.id == uiState.agentId }?.name
    val topBarTitle = when (uiState.assistantMode) {
        com.amaya.intelligence.domain.models.AssistantMode.CHAT -> activeConversationTitle?.takeIf { uiState.messages.isNotEmpty() }.orEmpty()
        com.amaya.intelligence.domain.models.AssistantMode.PROJECT -> activeConversationTitle?.takeIf { uiState.messages.isNotEmpty() } ?: stringResource(R.string.new_chat)
        com.amaya.intelligence.domain.models.AssistantMode.AGENT -> activeAgentName ?: ownerLabel
    }
    val topBarSubtitle = if (isRemoteMode) null else when (uiState.assistantMode) {
        com.amaya.intelligence.domain.models.AssistantMode.CHAT -> null
        com.amaya.intelligence.domain.models.AssistantMode.PROJECT -> uiState.workspacePath.orEmpty()
        com.amaya.intelligence.domain.models.AssistantMode.AGENT -> activeAgentGroupLabel
    }

    val statusBarInsets = WindowInsets.statusBars.asPaddingValues()
    val statusBarHeight = statusBarInsets.calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerDp = statusBarHeight + 84.dp
    val bottomDp = 80.dp + navBarHeight
    val bgColor = MaterialTheme.colorScheme.background

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                drawerShape = RectangleShape,
                drawerContainerColor = Color.Transparent,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                ChatDrawerContent(
                    drawerState = drawerState,
                    activeConversationId = uiState.conversationId,
                    isRemoteMode = isRemoteMode,
                    sessionMode = uiState.sessionMode,
                    workspacePath = uiState.workspacePath,
                    assistantMode = uiState.assistantMode,
                    ownerLabel = ownerLabel,
                    agentMembers = activeAgentMembers,
                    delegationTasks = activeDelegationTasks,
                    projects = projects,
                    agentGroups = agentGroups,
                    allAgents = allAgents,
                    allLocalConversations = allLocalConversations,
                    runningSessions = runningSessions,
                    isLoadingConversations = uiState.isLoadingConversations,
                    connectionState = connectionState,
                    conversations = conversations,
                    onLoadConversation = doLoadConversation,
                    onDeleteConversation = doDeleteConversation,
                    onClearConversation = doClearConversation,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToWorkspace = onNavigateToWorkspace,
                    onNavigateToAgents = onNavigateToAgents,
                    onOpenChat = {
                        viewModel.selectChatTarget(com.amaya.intelligence.domain.models.AssistantMode.CHAT)
                    },
                    onOpenProject = { project, conversationId ->
                        viewModel.selectChatTarget(
                            mode = com.amaya.intelligence.domain.models.AssistantMode.PROJECT,
                            ownerId = project.id.toString(),
                            workspacePath = project.rootPath,
                            conversationId = conversationId
                        )
                    },
                    onOpenAgent = { group, agent, _ ->
                        viewModel.selectChatTarget(
                            mode = com.amaya.intelligence.domain.models.AssistantMode.AGENT,
                            ownerId = group.id.toString(),
                            workspacePath = group.workspacePath,
                            agentId = agent.id
                        )
                    },
                    onNavigateToRemoteSession = onNavigateToRemoteSession,
                    onNavigateToOpencode = onNavigateToOpencode,
                    onExit = onExit,
                    hasMoreConversations = doHasMoreConversations,
                    loadMoreConversations = doLoadMoreConversations,
                    scope = scope,
                    sessionDisconnectVisible = isRemoteMode &&
                        (isBridgeMode && bridgeState.isConnected ||
                            !isBridgeMode && onConfirmSessionDisconnect != null),
                    onRequestSessionDisconnect = { showDisconnectDialog = true }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            if (uiState.messages.isEmpty()) {
                ChatEmptyContent(
                    isRemoteMode = isRemoteMode,
                    connectionState = connectionState,
                    uiState = uiState,

                    headerDp = headerDp,
                    bottomDp = bottomDp,
                    drawerOpen = drawerVisible,
                    onInputTextChange = { inputText.value = it },
                    onSendMessage = doSendMessage,
                    onNavigateToWorkspace = onNavigateToWorkspace,
                    workspaces = workspaces,
                    bridgeState = bridgeState,
                    isStreaming = uiState.isStreaming
                )
            } else {
                ChatMessageList(
                    listState = listState,
                    displayMessages = displayMessages,
                    conversationKey = conversationKey,
                    isLoading = uiState.isLoading && !uiState.isLoadingHistory,
                    isStreaming = uiState.isStreaming,
                    isRemoteMode = isRemoteMode,
                    headerDp = headerDp,
                    inputBarHeight = inputBarHeight,
                    drawerOpen = drawerVisible,
                    onToolAccept = onToolAccept,
                    onToolDecline = onToolDecline,
                    onLocalhostLinkClick = { annotationItem ->
                        selectedLocalhostLink = LocalhostLinkInfoParser.parse(annotationItem, serverIp)
                        showLocalhostLinkSheet = true
                    },
                    onScrollToBottomClick = autoFollow.jumpToBottom,
                    shouldAutoScroll = autoFollow.shouldAutoScroll
                )
            }

            if (showLocalhostLinkSheet && selectedLocalhostLink != null) {
                LocalhostLinkBottomSheet(
                    linkInfo = selectedLocalhostLink!!,
                    localIp = serverIp,
                    onDismiss = {
                        showLocalhostLinkSheet = false
                        selectedLocalhostLink = null
                    },
                    onCopyLink = { url ->
                        },
                    onOpenLink = { url ->
                        }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopStart)
                    .background(LocalAmayaGradients.current.topScrim)
            )

            ChatFloatingTopBar(
                title = topBarTitle,
                subtitle = topBarSubtitle,
                isRemoteMode = isRemoteMode,
                isBridgeMode = isBridgeMode,
                onMenuClick = {
                    keyboardController?.hide()
                    scope.launch { drawerState.open() }
                },
                onTitleClick = {
                    when {
                        isBridgeMode -> showBridgeSessionInfo = true
                        isRemoteMode -> viewModel.refreshState()
                        todoItems.isNotEmpty() -> showTodoSheet = true
                        else -> showSessionInfo = true
                    }
                },
                showNewChat = uiState.assistantMode != com.amaya.intelligence.domain.models.AssistantMode.AGENT,
                onNewChatClick = {
                    keyboardController?.hide()
                    doClearConversation()
                },
                showAgentMenu = uiState.assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT && uiState.agentId != null,
                onAgentMenuClick = { showAgentMenu = true },
                modifier = Modifier.align(Alignment.TopStart),
                agentMenu = {
                    AgentChatMenu(
                        expanded = showAgentMenu,
                        onOpenBrowser = {
                            showAgentMenu = false
                            (context as? android.app.Activity)?.let(BrowserOperatorActivity::start)
                        },
                        onConfigure = {
                            showAgentMenu = false
                            (context as? android.app.Activity)?.let { activity ->
                                uiState.agentId?.let { LocalAgentConfigActivity.startForResult(activity, it) }
                            }
                        },
                        onDeleteChat = {
                            showAgentMenu = false
                            showDeleteAgentChatSheet = true
                        },
                        onDismiss = { showAgentMenu = false }
                    )
                }
            )

            ChatBottomSection(
                modifier = Modifier.align(Alignment.BottomStart),
                inputText = inputText,
                isRemoteMode = isRemoteMode,
                uiState = uiState,
                connectionState = connectionState,
                drawerOpen = drawerVisible,
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
                supportsImages = selectedModelItem?.supportsImages == true,
                onSendMessageWithImage = doSendMessageWithImage,
                onClearImageAttachment = {
                    attachedImageBase64 = null
                    attachedImageMimeType = null
                    attachedImageName = null
                },
                onStopGeneration = doStopGeneration,
                onCancelCompactConversation = doCancelCompactConversation,
                onNavigateToWorkspace = onNavigateToWorkspace,
                ownerLabel = ownerLabel,
                mentionAgents = if (uiState.assistantMode == com.amaya.intelligence.domain.models.AssistantMode.AGENT) {
                    activeAgentMembers.filter { it.id != uiState.agentId }.map {
                        com.amaya.intelligence.ui.components.shared.ChatMentionAgent(it.localId, it.name, it.role, activeAgentGroupLabel)
                    }
                } else emptyList(),
                onSearchWorkspaceFiles = { query -> viewModel.searchWorkspaceFiles(query) },
                showConversationModeSelector = config?.showConversationModeSelector ?: isRemoteMode,
                onShowConversationModeSheet = { showConversationModeSheet = true },
                modelLabel = selectedModelLabel,
                modelId = selectedModelItem?.modelId.orEmpty(),
                modelProviderId = selectedModelItem?.providerId,
                modelIconType = selectedModelItem?.iconType,
                effort = uiState.effort,
                onEffortChange = { viewModel.setEffort(it) },
                onCompactConversation = doCompactConversation,
                onSelectModel = {
                    keyboardController?.hide()
                    showModelSelector = true
                },
                onInputBarHeightChange = { inputBarHeight.intValue = it }
            )
        }
    }

    if (showDeleteAgentChatSheet) {
        var deleteContext by remember(uiState.conversationId) { mutableStateOf(false) }
        StandardModalBottomSheet(
            onDismissRequest = { showDeleteAgentChatSheet = false },
            title = stringResource(R.string.delete_chat)
        ) {
            Text(stringResource(R.string.clear_this_agent_chat_from_the_screen_and_history))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { deleteContext = !deleteContext },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = deleteContext, onCheckedChange = { deleteContext = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_context_too))
            }
            Button(
                onClick = {
                    showDeleteAgentChatSheet = false
                    doClearVisibleHistory(deleteContext)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.yes)) }
            OutlinedButton(
                onClick = { showDeleteAgentChatSheet = false },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.no)) }
        }
    }

    if (showTodoSheet && todoItems.isNotEmpty()) {
        TodoSheet(
            items = todoItems,
            onDismiss = { showTodoSheet = false }
        )
    }

    if (showConversationModeSheet && isRemoteMode && config?.showConversationModeSelector != false) {
        val providerModes = remember(uiState.sessionMode) {
            com.amaya.intelligence.impl.ide.IdeProviderFactory.get(uiState.sessionMode.ideId)
                ?.conversationModes
                ?: com.amaya.intelligence.domain.models.ConversationModeOption.ANTIGRAVITY
        }
        val selectedId = uiState.conversationModeId
            ?: uiState.conversationMode.wireValue
        ConversationModeSheet(
            options = providerModes,
            selectedId = selectedId,
            onSelect = { option ->
                viewModel.setConversationModeId(option.id)
                showConversationModeSheet = false
            },
            onDismiss = { showConversationModeSheet = false }
        )
    }

    if (showModelSelector) {
        ModelSelectorSheet(
            modelOptions = uiState.modelOptions,
            activeModelKey = activeModelKey,
            onSelect = { item ->
                doSelectModel(item.id)
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
            onDismiss = { showSessionInfo = false },
            inputTokens = uiState.totalInputTokens,
            outputTokens = uiState.totalOutputTokens,
            providerId = selectedModelItem?.providerId,
            providerNameOverride = selectedModelItem?.providerName,
            modelDisplayNameOverride = selectedModelItem?.name
        )
    }

    if (showBridgeSessionInfo && isBridgeMode) {
        var pendingEnableAgentControl by remember { mutableStateOf(false) }
        WindowsBridgeSessionInfoSheet(
            state = bridgeState,
            onToggleAgentControl = {
                if (bridgeState.isAgentControlEnabled) {
                    bridgeViewModel.disableAgentControl()
                } else {
                    pendingEnableAgentControl = true
                }
            },
            onCapture = { bridgeViewModel.captureScreen() },
            onClearCapture = { bridgeViewModel.clearCapture() },
            onDisconnect = {
                bridgeViewModel.disconnect()
                onExit()
            },
            onDismiss = { showBridgeSessionInfo = false }
        )
        if (pendingEnableAgentControl) {
            com.amaya.intelligence.ui.components.remote.WindowsBridgeAgentControlDialog(
                onConfirm = {
                    pendingEnableAgentControl = false
                    bridgeViewModel.confirmEnableAgentControl()
                },
                onDismiss = { pendingEnableAgentControl = false }
            )
        }
    }

    if (showDisconnectDialog) {
        val disconnectName = sessionDisconnectName
            ?: if (isBridgeMode) "Windows Bridge" else uiState.sessionMode.displayName()
        com.amaya.intelligence.ui.components.shared.SessionDisconnectDialog(
            sessionName = disconnectName,
            onConfirm = {
                showDisconnectDialog = false
                val handler = onConfirmSessionDisconnect
                if (handler != null) {
                    handler()
                } else if (isBridgeMode) {
                    bridgeViewModel.disconnect()
                }
                onExit()
            },
            onDismiss = { showDisconnectDialog = false }
        )
    }
}
