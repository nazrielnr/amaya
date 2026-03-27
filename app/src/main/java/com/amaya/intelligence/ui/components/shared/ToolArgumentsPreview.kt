package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared badge component for status/mode labels.
 */
@Composable
fun BadgeLabel(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Renders tool-specific argument previews inside an expanded ToolCallCard.
 * Uses ToolCategory for primary rendering logic, with toolName for specific behavior.
 */
@Composable
fun ToolArgumentsPreview(
    toolName: String,
    arguments: Map<String, Any?>,
    isDark: Boolean,
    category: ToolCategory = ToolCategory.UNKNOWN,
    uiMetadata: ToolUiMetadata? = null
) {
    val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val codeBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val codeColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
    
    @Suppress("UNCHECKED_CAST")
    val args = arguments

    fun withSoftBreaks(text: String): String = text
            .replace("\\\\", "\\\\\u200B")
            .replace("/", "/\u200B")
            .replace(":", ":\u200B")

    fun formatRelativePath(path: String): String {
        if (path.isBlank()) return path
        val normalized = path.replace("/", "\\")
        val index = normalized.lowercase().indexOf("\\amaya\\")
        return if (index != -1) {
            normalized.substring(index + 1)
        } else {
            normalized.substringAfterLast("\\")
        }
    }

    fun summarizeTerminalPath(path: String, maxSegments: Int = 3): String {
        val normalized = formatRelativePath(path).replace('/', '\\').trimEnd('\\')
        val segments = normalized.split('\\').filter { it.isNotBlank() }
        if (segments.isEmpty()) return normalized

        val tail = segments.takeLast(maxSegments).joinToString("\\")
        return if (tail == normalized) tail else "…\\$tail"
    }

    @Composable
    fun DescriptionPayload(args: Map<String, Any?>, showIcon: Boolean = true) {
        val summary = args["summary"]?.toString()
            ?: (args["ArtifactMetadata"] as? Map<*, *>)?.get("Summary")?.toString()
            ?: args["Description"]?.toString()
        
        if (!summary.isNullOrBlank() && !summary.contains("%SAME%", ignoreCase = true)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 6.dp)) {
                if (showIcon) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp).padding(top = 2.dp),
                        tint = metaColor
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor,
                    lineHeight = 16.sp
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (category) {
            ToolCategory.SHELL -> {
                val command = args["command"]?.toString().orEmpty()
                val cwd = args["cwd"]?.toString().orEmpty()
                val id = args["commandId"]?.toString().orEmpty()
                val wait = args["waitSeconds"]?.toString() ?: "0"
                val chars = args["maxChars"]?.toString()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DescriptionPayload(args)
                    if (cwd.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = codeBg, modifier = Modifier.fillMaxWidth().clipToBounds()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${summarizeTerminalPath(cwd)} >",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = metaColor,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                if (command.isNotBlank()) {
                                    Text(
                                        text = withSoftBreaks(command),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = codeColor,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    } else if (command.isNotBlank()) {
                        CommandBlock("$ ${withSoftBreaks(command)}", codeBg, codeColor)
                    }
                    if (id.isNotBlank() || chars != null || wait != "0") {
                        MetaRow("Sync: ${wait}s wait", Icons.Default.AccessTime, metaColor)
                        if (!chars.isNullOrBlank()) {
                            MetaRow("Buffer: $chars chars", Icons.AutoMirrored.Filled.Subject, metaColor)
                        }
                        if (id.isNotBlank()) {
                            MetaRow("ID: ${id.take(12)}...", Icons.Default.Fingerprint, metaColor.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            ToolCategory.SEARCH -> {
                val path = args["path"]?.toString().orEmpty()
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DescriptionPayload(args)
                    
                    if (path.isNotBlank()) {
                        val icon = uiMetadata?.targetIcon?.let { mapToolIcon(it) } ?: Icons.Default.Search
                        PathRow(formatRelativePath(path), icon, metaColor)
                    }
                }
            }

            ToolCategory.FILE_IO -> {
                val pathStr = args["path"]?.toString().orEmpty()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DescriptionPayload(args)
                    PathRow(formatRelativePath(pathStr), Icons.Default.Description, metaColor)
                }
            }

            ToolCategory.SYSTEM, ToolCategory.TASK_MANAGEMENT, ToolCategory.MEMORY -> {
                val status = args["taskStatus"]?.toString()?.takeIf { it.isNotBlank() && !it.contains("%SAME%") }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (status != null) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp).padding(top = 2.dp),
                                tint = metaColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DescriptionPayload(args, showIcon = status == null)
                }
            }

            ToolCategory.WEB -> {
                // Web: search_web, read_url_content, browser
                DescriptionPayload(args)
            }

            ToolCategory.UNKNOWN -> {
                DescriptionPayload(args)

                val internalKeys = setOf(
                    "waitForPreviousTools", "conversationId", "original_name", "details", "uri", "processId", "id"
                )
                val relevantArgs = args.filterKeys { !it.startsWith("_") && it !in internalKeys }
                if (relevantArgs.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = codeBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            relevantArgs.entries.take(8).forEach { (k, v) ->
                                val valueStr = v.toString()
                                if (valueStr != "null" && valueStr.isNotBlank()) {
                                    Text(
                                        "$k: $valueStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = codeColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PathRow(path: String, icon: androidx.compose.ui.graphics.vector.ImageVector, metaColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = metaColor)
        Spacer(Modifier.width(6.dp))
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            color = metaColor
        )
    }
}

@Composable
private fun MetaRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, metaColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(13.dp), tint = metaColor)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = metaColor
        )
    }
}

@Composable
private fun CommandBlock(command: String, codeBg: Color, codeColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = codeBg, modifier = Modifier.fillMaxWidth()) {
        Text(command,
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, color = codeColor, modifier = Modifier.padding(10.dp))
    }
}
