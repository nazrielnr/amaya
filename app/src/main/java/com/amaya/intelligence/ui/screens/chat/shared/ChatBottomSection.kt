package com.amaya.intelligence.ui.screens.chat.shared

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.ai.displayName
import com.amaya.intelligence.domain.models.ChatUiState
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.ui.components.shared.ChatInput
import com.amaya.intelligence.ui.components.shared.ScrollablePills
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChatBottomSection(
    modifier: Modifier = Modifier,
    isRemoteMode: Boolean,
    uiState: ChatUiState,
    connectionState: ConnectionState,
    drawerOpen: Boolean,
    bgColor: Color,
    attachedFilePath: String?,
    attachedImageBase64: String? = null,
    attachedImageMimeType: String? = null,
    attachedImageName: String? = null,
    filePicker: ActivityResultLauncher<String>,
    imagePicker: ActivityResultLauncher<String>? = null,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    scope: CoroutineScope,
    onClearError: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendMessageWithImage: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onClearImageAttachment: () -> Unit = {},
    onStopGeneration: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onShowConversationModeSheet: () -> Unit,
    onInputBarHeightChange: (Int) -> Unit
) {
    var attachedPath by remember { mutableStateOf(attachedFilePath) }
    var currentImageBase64 by remember { mutableStateOf(attachedImageBase64) }
    var currentImageMimeType by remember { mutableStateOf(attachedImageMimeType) }
    var currentImageName by remember { mutableStateOf(attachedImageName) }

    // Sync with parent state changes
    LaunchedEffect(attachedImageBase64) { currentImageBase64 = attachedImageBase64 }
    LaunchedEffect(attachedImageMimeType) { currentImageMimeType = attachedImageMimeType }
    LaunchedEffect(attachedImageName) { currentImageName = attachedImageName }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!drawerOpen) Modifier.imePadding() else Modifier)
            .onSizeChanged { onInputBarHeightChange(it.height) }
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bgColor.copy(alpha = 0.0f),
                            0.20f to bgColor.copy(alpha = 0.30f),
                            0.45f to bgColor.copy(alpha = 0.75f),
                            0.65f to bgColor.copy(alpha = 0.92f),
                            1.0f to bgColor.copy(alpha = 1.0f)
                        )
                    )
                )
            }
    ) {
        AnimatedVisibility(visible = uiState.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        AnimatedVisibility(visible = isRemoteMode && connectionState != ConnectionState.CONNECTED) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (connectionState == ConnectionState.CONNECTING) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    val sessionName = uiState.sessionMode.displayName()
                    Text(
                        text = if (connectionState == ConnectionState.CONNECTING) "Reconnecting to $sessionName server..." else "Disconnected from $sessionName server. Attempting to reconnect...",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (uiState.messages.isEmpty() && !uiState.isLoading && !isRemoteMode) {
            ScrollablePills(onPromptClick = onSendMessage)
        }
        ChatInput(
            resetKey = uiState.conversationId,
            isStreaming = uiState.isStreaming,
            attachedFilePath = attachedPath,
            attachedImageBase64 = currentImageBase64,
            attachedImageName = currentImageName,
            onAttachFile = { filePicker.launch("*/*") },
            onAttachImage = { imagePicker?.launch("image/*") },
            onClearAttachment = { attachedPath = null },
            onClearImageAttachment = {
                currentImageBase64 = null
                currentImageMimeType = null
                currentImageName = null
                onClearImageAttachment()
            },
            conversationMode = uiState.conversationMode,
            showConversationModeSelector = isRemoteMode,
            onShowConversationModeSelector = onShowConversationModeSheet,
            workspacePath = uiState.workspacePath,
            onWorkspaceClick = onNavigateToWorkspace,
            onSendMessage = { text ->
                keyboardController?.hide()
                val path = attachedPath
                val imgBase64 = currentImageBase64
                val imgMime = currentImageMimeType
                val imgName = currentImageName
                attachedPath = null
                currentImageBase64 = null
                currentImageMimeType = null
                currentImageName = null
                onClearImageAttachment()
                scope.launch {
                    when {
                        imgBase64 != null -> {
                            android.util.Log.d("ChatBottomSection", "Calling onSendMessageWithImage: text=${text.take(30)}, base64Len=${imgBase64.length}, mime=$imgMime, name=$imgName")
                            onSendMessageWithImage(text, imgBase64, imgMime ?: "image/*", imgName ?: "image")
                        }
                        path != null -> {
                            val fileName = path.substringAfterLast("/")
                            val combined = buildString {
                                if (text.isNotBlank()) { append(text); append("\n\n") }
                                append("[Attached file: $fileName]\nPath: $path\nPlease read this file using read_file tool and use its content to help me.")
                            }
                            onSendMessage(combined)
                        }
                        else -> {
                            onSendMessage(text)
                        }
                    }
                }
            },
            onStopGeneration = onStopGeneration
        )
    }
}
