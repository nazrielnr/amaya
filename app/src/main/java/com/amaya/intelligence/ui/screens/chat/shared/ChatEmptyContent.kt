package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.ui.components.shared.ConversationSkeleton
import com.amaya.intelligence.ui.components.shared.WelcomeScreen

@Composable
fun ChatEmptyContent(
    isRemoteMode: Boolean,
    connectionState: ConnectionState,
    uiState: ChatUiState,
    showSkeletonOverride: Boolean,
    headerDp: Dp,
    bottomDp: Dp,
    drawerOpen: Boolean,
    onSendMessage: (String) -> Unit,
    onNavigateToWorkspace: () -> Unit,
    workspaces: List<RemoteWorkspace>
) {
    val isRemoteInitialMount = isRemoteMode && uiState.conversationId == null
    
    if ((isRemoteMode && connectionState != ConnectionState.CONNECTED && uiState.messages.isEmpty()) || isRemoteInitialMount) {
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(top = headerDp, bottom = bottomDp)
                .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (connectionState == ConnectionState.CONNECTING || isRemoteInitialMount) "Connecting to Remote Session..." else "Disconnected. Trying to reconnect...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = headerDp, bottom = bottomDp)
                .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading || (isRemoteMode && connectionState == ConnectionState.CONNECTING) || showSkeletonOverride) {
                ConversationSkeleton()
            } else {
                WelcomeScreen(
                    onPromptClick = onSendMessage,
                    currentWorkspace = uiState.workspacePath,
                    onNewProjectClick = onNavigateToWorkspace,
                    workspaces = workspaces,
                    onWorkspaceClick = onNavigateToWorkspace
                )
            }
        }
    }
}
