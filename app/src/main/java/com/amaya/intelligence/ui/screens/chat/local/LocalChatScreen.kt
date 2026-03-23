package com.amaya.intelligence.ui.screens.chat.local

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.ui.screens.chat.shared.ChatScreen
import com.amaya.intelligence.ui.screens.chat.shared.localChatScreenConfig
import com.amaya.intelligence.ui.viewmodels.ChatViewModel

@Composable
fun LocalChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    activeReminderCount: Int = -1,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToRemoteSession: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val config = localChatScreenConfig(
        onClearConversation = { viewModel.clearConversation() },
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToRemoteSession = onNavigateToRemoteSession
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
