package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read file content using java.io.File API for Android compatibility.
 */
@Singleton
class ReadFileTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val DEFAULT_MAX_SIZE = 1024 * 1024L  // 1MB
        const val ABSOLUTE_MAX_SIZE = 10 * 1024 * 1024L  // 10MB
        const val BINARY_CHECK_SIZE = 8000
    }
    
    override val name = "read_file"

    override val description = "Read one or multiple text files. Pass 'path' for single file or 'paths' array for batch. Use 'info_only' for metadata only."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {

        // ── Batch mode: paths[] ──────────────────────────────────────────
        @Suppress("UNCHECKED_CAST")
        val pathsList = (arguments["paths"] as? List<*>)?.mapNotNull { it?.toString() }
        if (pathsList != null) {
            return@withContext executeBatch(pathsList, arguments)
        }

        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path or paths",
                ErrorType.VALIDATION_ERROR
            )

        // ── Info-only mode ───────────────────────────────────────────────
        val infoOnly = arguments["info_only"] as? Boolean ?: false
        if (infoOnly) {
            return@withContext executeInfo(pathStr)
        }
        
        // Validate path access
        when (val validation = commandValidator.validatePath(pathStr, isWrite = false)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Path: $pathStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val file = File(pathStr)
        
        if (!file.exists()) {
            return@withContext ToolResult.Error(
                "File does not exist: $pathStr",
                ErrorType.NOT_FOUND
            )
        }
        
        if (!file.isFile) {
            return@withContext ToolResult.Error(
                "Path is not a regular file: $pathStr",
                ErrorType.VALIDATION_ERROR
            )
        }
        
        // Parse optional arguments
        val maxSize = (arguments["max_size"] as? Number)?.toLong()
            ?.coerceIn(1, ABSOLUTE_MAX_SIZE)
            ?: DEFAULT_MAX_SIZE
        val startLine = (arguments["start_line"] as? Number)?.toInt()?.coerceAtLeast(1)
        val endLine = (arguments["end_line"] as? Number)?.toInt()
        val encoding = arguments["encoding"] as? String ?: "UTF-8"
        
        try {
            val fileSize = file.length()
            
            // Size check
            if (fileSize > maxSize) {
                return@withContext ToolResult.Error(
                    "File too large: ${formatSize(fileSize)} (max: ${formatSize(maxSize)}). " +
                    "Use start_line/end_line to read a portion.",
                    ErrorType.SIZE_LIMIT,
                    recoverable = true
                )
            }
            
            // Binary check
            if (isBinaryFile(file)) {
                return@withContext ToolResult.Error(
                    "File appears to be binary: $pathStr",
                    ErrorType.VALIDATION_ERROR
                )
            }
            
            // Read file with specified encoding
            val charset = runCatching { Charset.forName(encoding) }.getOrElse { Charsets.UTF_8 }
            val content = file.readText(charset)
            val allLines = content.lines()
            val totalLines = allLines.size
            
            // Smart line limiting
            val maxDisplayLines = 200
            
            // Handle line range if specified
            val (output, displayedRange) = if (startLine != null || endLine != null) {
                val start = (startLine ?: 1) - 1
                val end = (endLine ?: totalLines).coerceAtMost(totalLines)
                
                if (start >= totalLines) {
                    return@withContext ToolResult.Error(
                        "start_line ($startLine) exceeds file length ($totalLines lines)",
                        ErrorType.VALIDATION_ERROR
                    )
                }
                
                val rangeLines = allLines.subList(start, end)
                Pair(rangeLines.joinToString("\n"), "Lines ${start + 1}-$end of $totalLines")
            } else {
                if (totalLines > maxDisplayLines) {
                    val limitedLines = allLines.take(maxDisplayLines)
                    val output = buildString {
                        append(limitedLines.joinToString("\n"))
                        append("\n\n--- Showing lines 1-$maxDisplayLines of $totalLines ---")
                        append("\nTo see more, use: start_line and end_line parameters")
                    }
                    Pair(output, "Lines 1-$maxDisplayLines of $totalLines (truncated)")
                } else {
                    Pair(content, "All $totalLines lines")
                }
            }
            
            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "path" to pathStr,
                    "size" to fileSize,
                    "total_lines" to totalLines,
                    "displayed" to displayedRange,
                    "encoding" to charset.name()
                )
            )
            
        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: ${e.message}",
                ErrorType.PERMISSION_ERROR
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to read file: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun isBinaryFile(file: File): Boolean {
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BINARY_CHECK_SIZE)
            val read = input.read(buffer)
            
            if (read == -1) return false
            
            for (i in 0 until read) {
                if (buffer[i] == 0.toByte()) {
                    return true
                }
            }
        }
        return false
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    // ── Batch mode ───────────────────────────────────────────────────────────
    private suspend fun executeBatch(paths: List<String>, arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            if (paths.size > 10) {
                return@withContext ToolResult.Error("Too many files: ${paths.size} (max: 10)", ErrorType.SIZE_LIMIT)
            }
            val maxLines = (arguments["max_lines"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 100
            val summaryOnly = arguments["summary_only"] as? Boolean ?: false

            val output = buildString {
                paths.forEachIndexed { idx, path ->
                    val file = java.io.File(path)
                    appendLine("=== ${file.name} ===")
                    // FIX #6/#15: Validate every path in batch mode through security layer
                    when (val v = commandValidator.validatePath(path, isWrite = false)) {
                        is ValidationResult.Denied -> {
                            appendLine("BLOCKED: ${v.reason}")
                            appendLine()
                            return@forEachIndexed
                        }
                        is ValidationResult.RequiresConfirmation -> {
                            appendLine("SKIPPED: Requires confirmation — ${v.reason}")
                            appendLine()
                            return@forEachIndexed
                        }
                        is ValidationResult.Allowed -> { /* proceed */ }
                    }
                    if (!file.exists()) { appendLine("ERROR: File not found"); appendLine(); return@forEachIndexed }
                    if (!file.isFile)   { appendLine("ERROR: Not a file");      appendLine(); return@forEachIndexed }
                    try {
                        val lines = file.readLines(Charsets.UTF_8)
                        if (summaryOnly && lines.size > 20) {
                            lines.take(10).forEach { appendLine(it) }
                            appendLine("... (${lines.size - 20} lines omitted) ...")
                            lines.takeLast(10).forEach { appendLine(it) }
                        } else if (lines.size > maxLines) {
                            lines.take(maxLines).forEach { appendLine(it) }
                            appendLine("... (${lines.size - maxLines} more lines)")
                        } else {
                            // FIX 2.7: lines already read — don't call readText() again (double I/O, race risk)
                            appendLine(lines.joinToString("\n"))
                        }
                    } catch (e: Exception) { appendLine("ERROR: ${e.message}") }
                    appendLine()
                }
            }
            ToolResult.Success(output.trimEnd(), metadata = mapOf("files_read" to paths.size))
        }

    // ── Info-only mode ───────────────────────────────────────────────────────
    private suspend fun executeInfo(pathStr: String): ToolResult = withContext(Dispatchers.IO) {
        when (val v = commandValidator.validatePath(pathStr, isWrite = false)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(v.reason, pathStr)
            is ValidationResult.Allowed -> {}
        }
        val file = java.io.File(pathStr)
        if (!file.exists()) return@withContext ToolResult.Error("Not found: $pathStr", ErrorType.NOT_FOUND)
        val type = if (file.isDirectory) "directory" else "file"
        val output = buildString {
            appendLine("Path: $pathStr")
            appendLine("Type: $type")
            appendLine("Size: ${formatSize(file.length())}")
            appendLine("Modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(file.lastModified()))}")
            if (file.isFile) appendLine("Extension: ${file.extension.ifEmpty { "(none)" }}")
            appendLine("Permissions: ${if (file.canRead()) "r" else "-"}${if (file.canWrite()) "w" else "-"}${if (file.canExecute()) "x" else "-"}")
            if (file.isDirectory) appendLine("Children: ${file.listFiles()?.size ?: 0} items")
        }
        ToolResult.Success(output.trim(), metadata = mapOf("path" to pathStr, "type" to type, "size" to file.length()))
    }
}
