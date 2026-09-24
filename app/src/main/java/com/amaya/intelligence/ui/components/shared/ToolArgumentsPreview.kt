package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

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
    uiMetadata: ToolUiMetadata? = null,
    result: String? = null,
    isBrowserStep: Boolean = false
) {
    val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val codeBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val codeColor = if (isDark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)
    val blockBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    
    @Suppress("UNCHECKED_CAST")
    val args = arguments
    val effectiveCategory = when (toolName) {
        "delegate_agent" -> ToolCategory.TASK_MANAGEMENT
        "update_memory", "memory_manage", "agent_memory", "agent_memory_internal" -> ToolCategory.MEMORY
        "skill_manage", "skill_view" -> ToolCategory.SKILL
        else -> category
    }

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

    fun memoryResultValue(key: String): String? {
        val parsed = result?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        val nested = parsed?.optJSONObject("proposal") ?: parsed?.optJSONObject("memory")
        return nested?.optString(key)?.takeIf { it.isNotBlank() }
            ?: parsed?.optString(key)?.takeIf { it.isNotBlank() }
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
            ?: runCatching { (args["ArtifactMetadata"] as? JSONObject)?.optString("Summary") }.getOrNull()
            ?: args["Description"]?.toString()
            ?: args["description"]?.toString()
            ?: args["Instruction"]?.toString()
            ?: args["instruction"]?.toString()
        
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

    fun argText(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        args[key]?.toString()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.contains("%SAME%") }
    }

    @Composable
    fun CompactArgRows(vararg pairs: Pair<String, String?>) {
        pairs.forEach { (label, value) ->
            value?.takeIf { it.isNotBlank() }?.let { MetaRow("$label: $it", Icons.AutoMirrored.Filled.Subject, metaColor) }
        }
    }

    @Composable
    fun CodePreviewBlock(label: String, text: String?, maxChars: Int = 900) {
        val value = text?.takeIf { it.isNotBlank() } ?: return
        Text(label, style = MaterialTheme.typography.labelSmall, color = metaColor)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = codeBg,
            border = BorderStroke(1.dp, blockBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value.trim().let { if (it.length > maxChars) it.take(maxChars).trimEnd() + "\n… truncated" else it },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = codeColor,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isBrowserStep) {
            CodePreviewBlock("Arguments", JSONObject(arguments).toString(2), maxChars = 4_000)
            return@Column
        }
        if (toolName == "delegate_agent") {
            CodePreviewBlock("Prompt", argText("task"), maxChars = 4000)
            return@Column
        }
        when (effectiveCategory) {
            ToolCategory.SHELL -> {
                val command = args["command"]?.toString().orEmpty()
                val cwd = args["cwd"]?.toString().orEmpty()
                val id = args["commandId"]?.toString().orEmpty()
                val wait = args["waitSeconds"]?.toString() ?: "0"
                val chars = args["maxChars"]?.toString()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DescriptionPayload(args)
                    if (cwd.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = codeBg,
                            border = BorderStroke(1.dp, blockBorderColor),
                            modifier = Modifier.fillMaxWidth().clipToBounds()
                        ) {
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
                                    overflow = TextOverflow.Ellipsis
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
                val path = argText("path", "SearchPath", "searchPath", "SearchDirectory", "searchDirectory", "DirectoryPath", "directoryPath").orEmpty()
                val query = argText("query", "Query", "pattern", "Pattern", "content")
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DescriptionPayload(args)
                    query?.let { MetaRow("Query: $it", Icons.Default.Search, metaColor) }
                    if (path.isNotBlank()) {
                        val icon = uiMetadata?.targetIcon?.let { mapToolIcon(it) } ?: Icons.Default.Search
                        PathRow(formatRelativePath(path), icon, metaColor)
                    }
                }
            }

            ToolCategory.FILE_IO -> {
                val pathStr = argText("path", "TargetFile", "targetFile", "AbsolutePath", "absolutePath", "File", "file", "filePath", "uri").orEmpty()
                val startLine = argText("StartLine", "startLine")
                val endLine = argText("EndLine", "endLine")
                val lineLabel = when {
                    !startLine.isNullOrBlank() && !endLine.isNullOrBlank() -> "$startLine-$endLine"
                    !startLine.isNullOrBlank() -> startLine
                    else -> null
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DescriptionPayload(args)
                    if (pathStr.isNotBlank()) PathRow(formatRelativePath(pathStr), Icons.Default.Description, metaColor)
                    CompactArgRows(
                        "Lines" to lineLabel,
                        "Complexity" to argText("complexity", "Complexity")
                    )
                    CodePreviewBlock("Target", argText("targetContent", "TargetContent"), maxChars = 500)
                    CodePreviewBlock("Replacement", argText("replacementContent", "ReplacementContent", "CodeContent", "codeContent"), maxChars = 900)
                }
            }

            ToolCategory.SYSTEM, ToolCategory.TASK_MANAGEMENT, ToolCategory.MEMORY -> {
                val status = args["taskStatus"]?.toString()?.takeIf { it.isNotBlank() && !it.contains("%SAME%") }
                val content = if (toolName == "update_memory") {
                    memoryResultValue("content") ?: args["content"]?.toString()?.takeIf { it.isNotBlank() }
                } else {
                    args["content"]?.toString()?.takeIf { it.isNotBlank() }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (effectiveCategory == ToolCategory.MEMORY && toolName == "update_memory" && content != null) {
                        MetaRow("Content: $content", Icons.AutoMirrored.Filled.Subject, metaColor)
                    } else if (effectiveCategory == ToolCategory.MEMORY && (content != null || args["query"] != null || args["id"] != null)) {
                        content?.let { MetaRow("Content: $it", Icons.AutoMirrored.Filled.Subject, metaColor) }
                        args["query"]?.toString()?.takeIf { it.isNotBlank() }?.let { MetaRow("Query: $it", Icons.Default.Search, metaColor) }
                        args["id"]?.toString()?.takeIf { it.isNotBlank() }?.let { MetaRow("ID: $it", Icons.Default.Fingerprint, metaColor) }
                    } else {
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
            }

            ToolCategory.SKILL -> {
                val action = args["action"]?.toString()?.lowercase().orEmpty()
                val name = args["name"]?.toString()?.takeIf { it.isNotBlank() }
                val description = args["description"]?.toString()?.takeIf { it.isNotBlank() }
                val reason = args["reason"]?.toString()?.takeIf { it.isNotBlank() }
                val summary = args["summary"]?.toString()?.takeIf { it.isNotBlank() }
                val tags = (args["tags"] as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
                val content = args["content"]?.toString()?.takeIf { it.isNotBlank() }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (toolName == "skill_view" && name != null) {
                        MetaRow("Name: $name", Icons.Default.Book, metaColor)
                    } else {
                        when (action) {
                            "create" -> {
                                description?.let { MetaRow("Description: $it", Icons.Default.Info, metaColor) }
                                if (tags.isNotEmpty()) MetaRow("Tags: ${tags.joinToString(" · ")}", Icons.Default.LocalOffer, metaColor)
                            }
                            "update", "patch" -> {
                                reason?.let { MetaRow("Why: $it", Icons.Default.Info, metaColor) }
                                summary?.let { MetaRow("Added / changed: $it", Icons.AutoMirrored.Filled.Subject, metaColor) }
                                if (reason == null && summary == null && content != null) {
                                    MetaRow("Content preview: ${content.lineSequence().filter { it.isNotBlank() }.take(3).joinToString(" ").take(220)}", Icons.AutoMirrored.Filled.Subject, metaColor)
                                }
                            }
                            else -> {
                                name?.let { MetaRow("Name: $it", Icons.Default.Book, metaColor) }
                                DescriptionPayload(args, showIcon = name == null)
                            }
                        }
                    }
                }
            }

            ToolCategory.WEB -> {
                // Web: search_web, read_url_content, browser
                DescriptionPayload(args)
                CompactArgRows(
                    "Task" to argText("task", "Task", "query", "Query"),
                    "URL" to argText("url", "Url")
                )
            }

            ToolCategory.UNKNOWN -> {
                DescriptionPayload(args)

                val internalKeys = setOf(
                    "waitForPreviousTools", "conversationId", "original_name", "details", "uri", "processId", "id"
                )
                val relevantArgs = args.filterKeys { !it.startsWith("_") && it !in internalKeys }
                if (relevantArgs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeBg,
                        border = BorderStroke(1.dp, blockBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val blockBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = codeBg,
        border = BorderStroke(1.dp, blockBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(command,
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, color = codeColor, modifier = Modifier.padding(10.dp))
    }
}
