package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.ConversationMode
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(
    resetKey: Any? = null,
    isStreaming: Boolean,
    attachedFilePath: String? = null,
    attachedImageBase64: String? = null,
    attachedImageName: String? = null,
    onAttachFile: () -> Unit = {},
    onAttachImage: () -> Unit = {},
    onClearAttachment: () -> Unit = {},
    onClearImageAttachment: () -> Unit = {},
    conversationMode: ConversationMode = ConversationMode.PLANNING,
    showConversationModeSelector: Boolean = false,
    onShowConversationModeSelector: () -> Unit = {},
    workspacePath: String? = null,
    onWorkspaceClick: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit
) {
    var text by remember(resetKey) { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val hasAttachment = attachedFilePath != null || attachedImageBase64 != null
    var showAttachMenu by remember { mutableStateOf(false) }

    val wsName = remember(workspacePath) {
        workspacePath?.substringAfterLast("/").orEmpty()
    }
    val hasWorkspace = remember(workspacePath) { !workspacePath.isNullOrBlank() }

    val pillColor = remember(isDark) {
        if (isDark) android.graphics.Color.parseColor("#FF2C2C2E").let { androidx.compose.ui.graphics.Color(it) }
        else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showConversationModeSelector) {
            Surface(
                onClick = onShowConversationModeSelector,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (conversationMode == ConversationMode.PLANNING) Icons.Default.Lightbulb else Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (conversationMode == ConversationMode.PLANNING) "Planning" else "Fast",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Attached file pill
        if (attachedFilePath != null) {
            val fileName = attachedFilePath.substringAfterLast("/")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp))
                    Box(
                        modifier = Modifier.size(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            .clickable { onClearAttachment() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            modifier = Modifier.size(9.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // Attached image pill
        if (attachedImageBase64 != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = attachedImageName ?: "Image",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp))
                    Box(
                        modifier = Modifier.size(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            .clickable { onClearImageAttachment() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            modifier = Modifier.size(9.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // Pill-shaped input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // (+) attach button
            Box {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(
                            if (isStreaming) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .clickable(enabled = !isStreaming) { showAttachMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Attach",
                        modifier = Modifier.size(22.dp),
                        tint = if (isStreaming) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSecondaryContainer)
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Attach file") },
                        onClick = {
                            showAttachMenu = false
                            onAttachFile()
                        },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Attach image") },
                        onClick = {
                            showAttachMenu = false
                            onAttachImage()
                        },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
                }
            }

            // Glassmorphism pill input container
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = pillColor,
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.12f else 0.08f)
                ),
                modifier = Modifier.weight(1f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val placeholderText = remember(hasWorkspace, wsName) {
                        if (hasWorkspace) "Ask anything on $wsName" else "Message"
                    }
                    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (text.isEmpty()) {
                                    Text(text = placeholderText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = placeholderColor,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                inner()
                            }
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    // Send / Stop button
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(
                                if (isStreaming) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else if (text.isNotBlank() || hasAttachment)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                            .clickable {
                                if (isStreaming) {
                                    onStopGeneration()
                                } else if (text.isNotBlank() || hasAttachment) {
                                    val msg = text.trim()
                                    text = ""
                                    onSendMessage(msg)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isStreaming) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send",
                                modifier = Modifier.size(15.dp),
                                tint = if (text.isNotBlank() || hasAttachment)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
