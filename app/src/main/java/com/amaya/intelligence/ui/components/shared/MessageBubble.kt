package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.R


import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.remote.api.MessageRole
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64

@Composable
fun MessageBubble(
    message: UiMessage,
    hideThinkingHeader: Boolean = false,
    onToolAccept: ((ToolExecution) -> Unit)? = null,
    onToolDecline: ((ToolExecution) -> Unit)? = null,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    onInteraction: () -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    if (isUser) {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val maxBubbleWidthDp = (screenWidth * 0.75f).dp
        val hPad = 14.dp
        val vPad = 10.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Display image attachments
            if (message.attachments.isNotEmpty()) {
                val imageAttachments = message.attachments.filter { it.mimeType.startsWith("image/") }
                if (imageAttachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidthDp)
                            .padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        imageAttachments.forEach { attachment ->
                            val bitmap = try {
                                val bytes = Base64.decode(attachment.dataBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                            
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = attachment.fileName.ifBlank { "Attached image" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                modifier = Modifier.widthIn(max = maxBubbleWidthDp)
            ) {
                Text(
                    message.content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Agent Icon
            val agentName = message.metadata["agent_name"] ?: ""
            val modelId = message.metadata["model_id"] ?: ""
            val iconRes = AgentIcon.get(agentName, modelId)
            
            if (iconRes != 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                if (message.steps.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        message.steps.forEach { step ->
                            when (step) {
                                is MessageStep.ToolCall -> {
                                    if (step.execution.name != "update_todo") {
                                        key(step.execution.toolCallId) {
                                            ToolCallCard(
                                                execution = step.execution,
                                                onAccept = onToolAccept?.let { callback -> { callback(step.execution) } },
                                                onDecline = onToolDecline?.let { callback -> { callback(step.execution) } },
                                                onLocalhostLinkClick = onLocalhostLinkClick,
                                                onInteraction = onInteraction
                                            )
                                        }
                                    }
                                }
                                is MessageStep.Text -> {
                                    val textContent = step.formattedContent ?: step.content
                                    if (textContent.isNotBlank()) {
                                        key(step.id) {
                                            MarkdownText(
                                                text = textContent,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.fillMaxWidth(),
                                                onLocalhostLinkClick = onLocalhostLinkClick
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (message.toolExecutions.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.toolExecutions.filter { it.name != "update_todo" }.forEach { execution ->
                                key(execution.toolCallId) {
                                    ToolCallCard(
                                        execution = execution,
                                        onAccept = onToolAccept?.let { callback -> { callback(execution) } },
                                        onDecline = onToolDecline?.let { callback -> { callback(execution) } },
                                        onLocalhostLinkClick = onLocalhostLinkClick,
                                        onInteraction = onInteraction
                                    )
                                }
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        val content = message.formattedContent ?: message.content
                        MarkdownText(
                            text = content,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            onLocalhostLinkClick = onLocalhostLinkClick
                        )
                    }
                }
            }
        }


    }
}
