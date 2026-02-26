package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apply unified diff patch to a file.
 * More precise than edit_file for complex multi-line changes.
 */
@Singleton
class ApplyDiffTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
    }
    
    override val name = "apply_diff"
    
    override val description = """
        Apply a unified diff patch to a file. More precise than edit_file for complex changes.
        
        Arguments:
        - path (string, required): Absolute path to the file
        - diff (string, required): Unified diff content (lines starting with +/-)
        - dry_run (boolean, optional): Preview changes without saving (default: false)
        
        Diff format example:
        @@ -10,3 +10,4 @@
         existing line
        +new line added
         another line
        -line to remove
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )
        
        val diff = arguments["diff"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: diff",
                ErrorType.VALIDATION_ERROR
            )
        
        val dryRun = arguments["dry_run"] as? Boolean ?: false
        
        // Validate path
        when (val validation = commandValidator.validatePath(pathStr, isWrite = !dryRun)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Apply diff to: $pathStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val file = File(pathStr)
        
        if (!file.exists()) {
            return@withContext ToolResult.Error(
                "File not found: $pathStr",
                ErrorType.NOT_FOUND
            )
        }
        
        if (file.length() > MAX_FILE_SIZE) {
            return@withContext ToolResult.Error(
                "File too large for diff application",
                ErrorType.SIZE_LIMIT
            )
        }
        
        try {
            val originalLines = file.readLines().toMutableList()
            val result = applyUnifiedDiff(originalLines, diff)
            
            if (result.errors.isNotEmpty()) {
                return@withContext ToolResult.Error(
                    "Diff application failed:\n${result.errors.joinToString("\n")}",
                    ErrorType.VALIDATION_ERROR
                )
            }
            
            if (dryRun) {
                val preview = buildString {
                    append("Dry run - changes would be:\n\n")
                    append("Lines added: ${result.linesAdded}\n")
                    append("Lines removed: ${result.linesRemoved}\n\n")
                    append("Preview (first 30 lines):\n")
                    result.newContent.take(30).forEachIndexed { i, line ->
                        append("${i + 1}: $line\n")
                    }
                    if (result.newContent.size > 30) {
                        append("... (${result.newContent.size - 30} more lines)\n")
                    }
                }
                return@withContext ToolResult.Success(preview)
            }
            
            // Create backup
            val backupDir = File(file.parent, ".backup")
            backupDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "${file.name}.$timestamp.bak")
            file.copyTo(backupFile, overwrite = true)
            
            // Apply changes
            file.writeText(result.newContent.joinToString("\n"))
            
            ToolResult.Success(
                output = buildString {
                    append("Applied diff to ${file.name}\n")
                    append("Lines added: ${result.linesAdded}\n")
                    append("Lines removed: ${result.linesRemoved}\n")
                    append("Backup: ${backupFile.name}")
                },
                metadata = mapOf(
                    "lines_added" to result.linesAdded,
                    "lines_removed" to result.linesRemoved,
                    "backup" to backupFile.absolutePath
                )
            )
            
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to apply diff: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private data class DiffResult(
        val newContent: List<String>,
        val linesAdded: Int,
        val linesRemoved: Int,
        val errors: List<String>
    )
    
    private fun applyUnifiedDiff(originalLines: MutableList<String>, diff: String): DiffResult {
        val errors = mutableListOf<String>()
        var linesAdded = 0
        var linesRemoved = 0
        
        val result = originalLines.toMutableList()
        var offset = 0
        
        val diffLines = diff.lines()
        var i = 0
        
        while (i < diffLines.size) {
            val line = diffLines[i]
            
            // Parse hunk header: @@ -start,count +start,count @@
            if (line.startsWith("@@")) {
                val match = """@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""".toRegex().find(line)
                if (match != null) {
                    val oldStart = match.groupValues[1].toInt() - 1 + offset
                    var currentLine = oldStart
                    
                    i++
                    while (i < diffLines.size && !diffLines[i].startsWith("@@")) {
                        val hunkLine = diffLines[i]
                        when {
                            hunkLine.startsWith("+") -> {
                                // Add line
                                val content = hunkLine.substring(1)
                                if (currentLine <= result.size) {
                                    result.add(currentLine, content)
                                    offset++
                                    linesAdded++
                                    currentLine++
                                }
                            }
                            hunkLine.startsWith("-") -> {
                                // Remove line
                                if (currentLine < result.size) {
                                    result.removeAt(currentLine)
                                    offset--
                                    linesRemoved++
                                }
                            }
                            hunkLine.startsWith(" ") || hunkLine.isEmpty() -> {
                                // Context line, just advance
                                currentLine++
                            }
                        }
                        i++
                    }
                    continue
                }
            }
            i++
        }
        
        return DiffResult(result, linesAdded, linesRemoved, errors)
    }
}
