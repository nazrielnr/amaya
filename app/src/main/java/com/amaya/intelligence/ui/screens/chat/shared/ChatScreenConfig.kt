package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.ui.res.UiStrings

/**
 * Configuration for ChatScreen behavior.
 * Allows LocalChatScreen and RemoteChatScreen to customize behavior without
 * hardcoding mode checks in ChatScreen.
 */
data class ChatScreenConfig(
    // UI Configuration
    val showRemoteSessionButton: Boolean,
    val showConnectionIndicator: Boolean,
    val showConversationModeSelector: Boolean,
    val showSessionInfoButton: Boolean,
    val showScrollablePills: Boolean,
    val showStreamingIndicator: Boolean,

    val selectedAgentFallbackLabel: String = "Select Agent",
    val streamingLabel: String = "Streaming",
    val idleLabel: String = "Idle",
    
    // Tool interaction callbacks (remote only)
    val onToolAccept: ((ToolExecution) -> Unit)? = null,
    val onToolDecline: ((ToolExecution) -> Unit)? = null,
    
    // Behavior
    val onBackPressed: () -> Unit,
    
    // Content slots
    val welcomeContent: @Composable (
        onPromptClick: (String) -> Unit,
        currentWorkspace: String?,
        onNewProjectClick: () -> Unit,
        workspaces: List<RemoteWorkspace>,
        onWorkspaceClick: () -> Unit
    ) -> Unit,
    
    val loadingContent: @Composable (connectionState: ConnectionState) -> Unit,
    
    val drawerFooterContent: @Composable (
        onNavigateToSettings: () -> Unit,
        onNavigateToRemoteSession: () -> Unit,
        onExit: () -> Unit
    ) -> Unit
)

/**
 * Default configuration factory for Local mode.
 */
fun localChatScreenConfig(
    onClearConversation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRemoteSession: () -> Unit
): ChatScreenConfig = ChatScreenConfig(
    showRemoteSessionButton = true,
    showConnectionIndicator = false,
    showConversationModeSelector = false,
    showSessionInfoButton = true,
    showScrollablePills = true,
    showStreamingIndicator = false,
    selectedAgentFallbackLabel = "Select Agent",
    streamingLabel = "Streaming",
    idleLabel = "Idle",
    onToolAccept = null,
    onToolDecline = null,
    onBackPressed = onClearConversation,
    welcomeContent = { onPromptClick, currentWorkspace, onNewProjectClick, workspaces, onWorkspaceClick ->
        com.amaya.intelligence.ui.components.shared.WelcomeScreen(
            onPromptClick = onPromptClick,
            currentWorkspace = currentWorkspace,
            onNewProjectClick = onNewProjectClick,
            workspaces = workspaces,
            onWorkspaceClick = onWorkspaceClick
        )
    },
    loadingContent = { _ ->
        // Local doesn't show connection loading
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize())
    },
    drawerFooterContent = { onNavigateToSettings, onNavigateToRemoteSession, _ ->
        LocalDrawerFooter(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRemoteSession = onNavigateToRemoteSession
        )
    }
)

/**
 * Default configuration factory for Remote mode.
 */
fun remoteChatScreenConfig(
    onExit: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToolAccept: (com.amaya.intelligence.domain.models.ToolExecution) -> Unit,
    onToolDecline: (com.amaya.intelligence.domain.models.ToolExecution) -> Unit
): ChatScreenConfig = ChatScreenConfig(
    showRemoteSessionButton = false,
    showConnectionIndicator = true,
    showConversationModeSelector = true,
    showSessionInfoButton = false,
    showScrollablePills = false,
    showStreamingIndicator = true,
    selectedAgentFallbackLabel = "Select Agent",
    streamingLabel = "Streaming",
    idleLabel = "Idle",
    onToolAccept = onToolAccept,
    onToolDecline = onToolDecline,
    onBackPressed = onExit,
    welcomeContent = { onPromptClick, _, _, workspaces, _ ->
        // Remote uses different welcome screen
        com.amaya.intelligence.ui.components.remote.RemoteWelcomeScreen(
            onPromptClick = onPromptClick,
            serverName = workspaces.firstOrNull()?.name,
            onConnectClick = {} // Already connected in remote mode
        )
    },
    loadingContent = { connectionState ->
        com.amaya.intelligence.ui.screens.chat.components.ConnectionBanner(
            connectionState = connectionState
        )
    },
    drawerFooterContent = { onNavigateToSettings, _, onExit ->
        RemoteDrawerFooter(
            onNavigateToSettings = onNavigateToSettings,
            onExit = onExit
        )
    }
)

/**
 * Drawer footer for Local mode - shows Remote Connection button.
 */
@Composable
fun LocalDrawerFooter(
    onNavigateToSettings: () -> Unit,
    onNavigateToRemoteSession: () -> Unit
) {
    // Remote Connection button (only in local mode)
    Surface(
        onClick = onNavigateToRemoteSession,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Text(UiStrings.Connection.REMOTE_CONNECTION, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
        }
    }
}

/**
 * Drawer footer for Remote mode - shows Exit button.
 */
@Composable
fun RemoteDrawerFooter(
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit
) {
    // Exit Remote Session button
    Surface(
        onClick = onExit,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(14.dp))
            Text("Exit Remote Session", style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        }
    }
}
