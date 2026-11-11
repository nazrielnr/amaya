package com.amaya.intelligence.ui.screens.chat.opencode

import com.amaya.intelligence.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.domain.ai.IntelligenceSessionManager
import com.amaya.intelligence.domain.bridge.AgentModes
import com.amaya.intelligence.impl.ide.opencode.OpencodePermissionRequest
import com.amaya.intelligence.impl.ide.opencode.OpencodeSessionForegroundService
import com.amaya.intelligence.ui.screens.chat.shared.ChatScreen
import com.amaya.intelligence.ui.screens.chat.shared.remoteChatScreenConfig
import com.amaya.intelligence.ui.viewmodels.ChatViewModel
import com.amaya.intelligence.ui.viewmodels.opencode.OpencodeChatPanelViewModel

/**
 * Opencode chat surface. The Plan/Build mode selector is owned by the generic
 * Conversation Mode sheet — [OpencodeProvider.conversationModes] declares the
 * options so we don't duplicate the pill here.
 *
 * The only opencode-specific overlay this screen renders is the permission
 * approval card, anchored under the top app bar and inside the chat content
 * box so the drawer overlaps it when opened.
 */
@Composable
fun OpencodeChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    panelViewModel: OpencodeChatPanelViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val panelState by panelViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.switchMode(IntelligenceSessionManager.SessionMode.OPENCODE)
        OpencodeSessionForegroundService.start(
            context,
            title = "Opencode session",
            text = when (panelState.mode) {
                AgentModes.PLAN -> "Plan mode — menjaga chat opencode tetap aktif"
                else -> "Build mode — menjaga chat opencode tetap aktif"
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.switchMode(IntelligenceSessionManager.SessionMode.LOCAL)
            OpencodeSessionForegroundService.stop(context)
        }
    }
    LaunchedEffect(panelState.mode) {
        OpencodeSessionForegroundService.update(
            context,
            title = "Opencode session",
            text = when (panelState.mode) {
                AgentModes.PLAN -> "Plan mode aktif"
                else -> "Build mode aktif"
            }
        )
    }

    val config = remoteChatScreenConfig(
        onExit = onExit,
        onNavigateToSettings = onNavigateToSettings,
        onToolAccept = { _ -> panelViewModel.approvePermission() },
        onToolDecline = { _ -> panelViewModel.rejectPermission() }
    ).copy(
        // Opencode exposes Plan/Build via the generic conversation-mode picker
        // that lives inside the chat input bar. Turning it on here lets the
        // shared ChatInput surface the pill + bottom sheet.
        showConversationModeSelector = true,
        selectedModelFallbackLabel = R.string.select_opencode_model,
        streamingLabel = R.string.opencode_streaming,
        idleLabel = R.string.opencode_ready
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            viewModel = viewModel,
            isRemoteModeOverride = true,
            config = config,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToWorkspace = onNavigateToWorkspace,
            onExit = onExit,
            sessionDisconnectName = "Opencode"
        )

        panelState.pendingPermission?.let { request ->
            OpencodePermissionOverlay(
                request = request,
                onApproveOnce = panelViewModel::approvePermission,
                onApproveAlways = panelViewModel::approvePermissionAlways,
                onReject = panelViewModel::rejectPermission
            )
        }
    }
}

@Composable
private fun BoxScopeAlignTop(content: @Composable () -> Unit) {
    content()
}

@Composable
private fun OpencodePermissionOverlay(
    request: OpencodePermissionRequest,
    onApproveOnce: () -> Unit,
    onApproveAlways: () -> Unit,
    onReject: () -> Unit
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = statusBar + 80.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Opencode needs approval",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                val title = request.title.ifBlank { request.kind ?: "Tool call" }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                request.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onReject,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) { Text("Reject") }
                    TextButton(onClick = onApproveAlways) { Text("Always") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onApproveOnce,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Allow once") }
                }
            }
        }
    }
}
