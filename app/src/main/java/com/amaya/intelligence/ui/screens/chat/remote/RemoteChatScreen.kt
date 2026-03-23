package com.amaya.intelligence.ui.screens.chat.remote

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.ui.screens.chat.shared.ChatScreen
import com.amaya.intelligence.ui.screens.chat.shared.remoteChatScreenConfig
import com.amaya.intelligence.ui.viewmodels.ChatViewModel

@Composable
fun RemoteChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    activeReminderCount: Int = -1,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToRemoteSession: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val config = remoteChatScreenConfig(
        onExit = onExit,
        onNavigateToSettings = onNavigateToSettings,
        onToolAccept = { execution -> viewModel.respondToToolInteraction(execution.toolCallId, true) },
        onToolDecline = { execution -> viewModel.respondToToolInteraction(execution.toolCallId, false) }
    )
    
    ChatScreen(
        viewModel = viewModel,
        activeReminderCount = activeReminderCount,
        config = config,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToWorkspace = onNavigateToWorkspace,
        onNavigateToRemoteSession = onNavigateToRemoteSession,
        onExit = onExit
    )
}
