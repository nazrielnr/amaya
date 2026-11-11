package com.amaya.intelligence.ui.screens.chat.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.ui.components.remote.RemoteSessionPill
import com.amaya.intelligence.ui.components.remote.WindowsBridgeChatUiState
import com.amaya.intelligence.ui.components.remote.WindowsBridgeWelcomePill
import com.amaya.intelligence.ui.components.shared.ConversationSkeleton
import com.amaya.intelligence.ui.components.shared.WelcomeScreen

@Composable
fun ChatEmptyContent(
    isRemoteMode: Boolean,
    connectionState: ConnectionState,
    uiState: ChatUiState,
    headerDp: Dp,
    bottomDp: Dp,
    drawerOpen: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onNavigateToWorkspace: () -> Unit,
    workspaces: List<RemoteWorkspace>,
    bridgeState: WindowsBridgeChatUiState = WindowsBridgeChatUiState(),
    isStreaming: Boolean = false
) {
    val isBridgeMode =
        uiState.sessionMode == IntelligenceSessionManager.SessionMode.WINDOWS_BRIDGE

    if (isRemoteMode && !isBridgeMode && connectionState != ConnectionState.CONNECTED &&
        uiState.messages.isEmpty()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(top = headerDp, bottom = bottomDp)
                .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (connectionState == ConnectionState.CONNECTING)
                        stringResource(R.string.connecting_to_remote_session)
                    else stringResource(R.string.disconnected_trying_to_reconnect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val showSkeleton = uiState.isLoadingHistory ||
        (isRemoteMode && !isBridgeMode && connectionState == ConnectionState.CONNECTING)

    // Crossfade rather than a hard cut: history arrives in one shot, and swapping the
    // placeholder for the real list without a transition is the visible "flicker" when
    // opening a session.
    Crossfade(
        targetState = showSkeleton,
        animationSpec = tween(durationMillis = 200),
        label = "chat_empty_content"
    ) { skeleton ->
        if (skeleton) {
            // Top-anchored, and padded to match ChatMessageList's contentPadding
            // exactly, so the placeholder rows sit where the real messages will land.
            ConversationSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = headerDp + 8.dp,
                    bottom = bottomDp
                )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerDp, bottom = bottomDp)
                    .then(if (!drawerOpen) Modifier.imePadding() else Modifier),
                contentAlignment = Alignment.Center
            ) {
                val headerSlot: (@Composable () -> Unit)? = when {
                    isBridgeMode && bridgeState.shouldShowBanner -> {
                        { WindowsBridgeWelcomePill(state = bridgeState) }
                    }
                    isRemoteMode && !isBridgeMode && connectionState != ConnectionState.DISCONNECTED -> {
                        {
                            RemoteSessionPill(
                                sessionMode = uiState.sessionMode,
                                connectionState = connectionState,
                                isStreaming = isStreaming
                            )
                        }
                    }
                    else -> null
                }
                WelcomeScreen(
                    onPromptClick = onInputTextChange,
                    currentWorkspace = uiState.workspacePath,
                    onNewProjectClick = onNavigateToWorkspace,
                    workspaces = workspaces,
                    onWorkspaceClick = onNavigateToWorkspace,
                    showWorkspaceChip = !isBridgeMode,
                    header = headerSlot
                )
            }
        }
    }
}
