package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.data.remote.mcp.McpServerTestResult
import com.amaya.intelligence.ui.screens.amaya.iosAmayaColors
import com.amaya.intelligence.ui.screens.amaya.AmayaSwitch

@Composable
fun McpServerCard(
    server: McpServerConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: (() -> Unit)? = null,
    testResult: McpServerTestResult? = null,
    isTesting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = iosAmayaColors()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.groupSurface,
        border = BorderStroke(0.7.dp, colors.border),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = colors.iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.name.ifBlank { "MCP Server" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = colors.primaryText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    server.serverUrl.ifBlank { "No URL" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = colors.secondaryText,
                    maxLines = 1
                )
                if (server.headers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.tagBackground
                    ) {
                        Text(
                            "${server.headers.size} header${if (server.headers.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color = colors.secondaryText
                        )
                    }
                }
                if (isTesting) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Testing connection...",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (testResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (testResult.isSuccess) Color(0x1F34C759) else Color(0x1FFF3B30)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (testResult.isSuccess) Color(0xFF34C759) else Color(0xFFFF3B30))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (testResult.isSuccess) {
                                    "Active • ${testResult.toolCount} tools • ${testResult.latencyMs}ms"
                                } else {
                                    "Offline: ${testResult.message}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (testResult.isSuccess) Color(0xFF34C759) else Color(0xFFFF3B30),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            AmayaSwitch(
                checked = server.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = 4.dp)
            )
            if (onTest != null) {
                IconButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.iconTint
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Test MCP Connection",
                            tint = colors.iconTint.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = "Edit",
                    tint = colors.iconTint.copy(alpha = 0.75f)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
