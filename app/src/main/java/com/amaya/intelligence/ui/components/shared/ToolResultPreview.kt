package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.amaya.intelligence.ui.components.shared.MarkdownText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a per-tool-type expanded result preview inside an expanded ToolCallCard.
 * Uses ToolCategory for primary rendering logic, with toolName for specific behavior.
 */
@Composable
fun ToolResultPreview(
    toolName: String,
    arguments: Map<String, Any?>,
    result: String,
    isDark: Boolean,
    category: ToolCategory = ToolCategory.UNKNOWN,
    onLocalhostLinkClick: ((String) -> Unit)? = null,
    uiMetadata: ToolUiMetadata? = null
) {
    val codeBlockBg   = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val codeTextColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
    val metaColor     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    val redBg = Color(0xFFFF3B30).copy(alpha = 0.12f)
    val greenBg = Color(0xFF34C759).copy(alpha = 0.12f)

    @Suppress("UNCHECKED_CAST")
    val args = arguments

    fun findArg(vararg keys: String): String? {
        keys.forEach { key ->
            val v = args[key] ?: args[key.lowercase()] ?: args[key.replaceFirstChar { it.uppercase() }]
            if (v != null && v.toString().isNotBlank() && !v.toString().contains("%SAME%")) {
                return v.toString()
            }
        }
        return null
    }

    @Composable
    fun DiffBlock(content: String) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = codeBlockBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content.lines().forEach { line ->
                    val bgColor = when {
                        line.startsWith("-") -> redBg
                        line.startsWith("+") -> greenBg
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.startsWith("-") -> Color(0xFFFF453A)
                        line.startsWith("+") -> Color(0xFF32D74B)
                        else -> codeTextColor
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 8.dp, vertical = 1.dp)) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 16.sp,
                                color = textColor
                            )
                        )
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (category) {
            ToolCategory.FILE_IO -> {
                if (arguments.hasCanonicalFileDiff()) {
                    val target = findArg("targetContent", "TargetContent")
                    val replacement = findArg("replacementContent", "ReplacementContent", "CodeContent")

                    val diffText = buildString {
                        val chunks = (args["replacementChunks"] as? List<*>)
                            ?: (args["ReplacementChunks"] as? List<*>)
                            ?: emptyList<Any?>()
                        val normalizedChunks = chunks.mapNotNull { it as? Map<*, *> }

                        if (normalizedChunks.isNotEmpty()) {
                            normalizedChunks.forEachIndexed { idx, chunk ->
                                val t = chunk["TargetContent"]?.toString() ?: chunk["targetContent"]?.toString()
                                val r = chunk["ReplacementContent"]?.toString() ?: chunk["replacementContent"]?.toString()
                                if (!t.isNullOrBlank()) append("- $t\n")
                                if (!r.isNullOrBlank()) append("+ $r\n")
                                if (idx < normalizedChunks.size - 1) append("\n")
                            }
                        } else {
                            if (!target.isNullOrBlank()) append("- $target\n")
                            if (!replacement.isNullOrBlank()) append("+ $replacement")
                        }
                    }.trim()

                    if (diffText.isNotBlank()) {
                        DiffBlock(diffText)
                    } else if (result.isNotBlank() && !result.lowercase().contains("file updated") && !result.lowercase().contains("success")) {
                        GenericResultBlock(result, codeBlockBg, codeTextColor, onLocalhostLinkClick)
                    }
                } else if (result.isNotBlank()) {
                    GenericResultBlock(result, codeBlockBg, codeTextColor, onLocalhostLinkClick)
                }
            }

            ToolCategory.SHELL -> {
                if (result.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg, modifier = Modifier.fillMaxWidth()) {
                        Text(result.trim().take(4096).let { if (result.length > 4096) "$it…" else it },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, lineHeight = 18.sp, fontSize = 11.sp),
                            color = codeTextColor,
                            modifier = Modifier.padding(10.dp))
                    }
                }
            }

            ToolCategory.SEARCH -> {
                val lines = result.lines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            lines.take(30).forEach { line ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                    val icon = uiMetadata?.targetIcon?.let { mapToolIcon(it) }
                                        ?: if (category == ToolCategory.SEARCH) Icons.Default.Search else Icons.Default.Link
                                    Icon(icon, null, modifier = Modifier.size(12.dp), tint = metaColor)
                                    Spacer(Modifier.width(6.dp))
                                    val displayLine = line.trim()
                                    Text(displayLine, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, 
                                        color = codeTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            ToolCategory.WEB -> {
                if (result.isNotBlank()) {
                    GenericResultBlock(result, codeBlockBg, codeTextColor, onLocalhostLinkClick)
                }
            }

            ToolCategory.SYSTEM, ToolCategory.TASK_MANAGEMENT, ToolCategory.MEMORY -> {
            }

            ToolCategory.UNKNOWN -> {
                if (result.isNotBlank() && !result.lowercase().contains("success")) {
                    GenericResultBlock(result, codeBlockBg, codeTextColor, onLocalhostLinkClick)
                }
            }
        }
    }
}

@Composable
private fun GenericResultBlock(
    result: String,
    codeBlockBg: Color,
    codeTextColor: Color,
    onLocalhostLinkClick: ((String) -> Unit)? = null
) {
    Surface(shape = RoundedCornerShape(8.dp), color = codeBlockBg, modifier = Modifier.fillMaxWidth()) {
        MarkdownText(text = result.trim(), color = codeTextColor, modifier = Modifier.padding(10.dp), compact = true, onLocalhostLinkClick = onLocalhostLinkClick)
    }
}
