package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Edit a file by replacing specific text content.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class EditFileTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        const val MAX_REPLACEMENTS = 100
    }
    
    override val name = "edit_file"

    override val description = "Edit a file by replacing specific text, or apply a unified diff/patch. Use 'old_content'+'new_content' for text replacement, or 'diff' for patch mode."
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {

        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error("Missing required argument: path", ErrorType.VALIDATION_ERROR)

        // ── Diff/patch mode (replaces apply_diff) ────────────────────────
        val diffStr = arguments["diff"] as? String
        if (diffStr != null) {
            when (val v = commandValidator.validatePath(pathStr, isWrite = true)) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
                is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(v.reason, pathStr)
                is ValidationResult.Allowed -> {}
            }
            val file = java.io.File(pathStr)
            if (!file.exists()) return@withContext ToolResult.Error("File not found: $pathStr", ErrorType.NOT_FOUND)
            val content = file.readText(Charsets.UTF_8)
            // Parse unified diff — apply @@ hunks
            return@withContext applyUnifiedDiff(file, content, diffStr)
        }
        
        val oldContent = arguments["old_content"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: old_content",
                ErrorType.VALIDATION_ERROR
            )
        
        val newContent = arguments["new_content"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: new_content",
                ErrorType.VALIDATION_ERROR
            )
        
        // Validate path access
        when (val validation = commandValidator.validatePath(pathStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Path: $pathStr\nReplace: ${oldContent.take(50)}...\nWith: ${newContent.take(50)}..."
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
        
        val fileSize = file.length()
        if (fileSize > MAX_FILE_SIZE) {
            return@withContext ToolResult.Error(
                "File too large: ${fileSize / 1024}KB (max: ${MAX_FILE_SIZE / 1024}KB)",
                ErrorType.SIZE_LIMIT
            )
        }
        
        val replaceAll = arguments["all_occurrences"] as? Boolean ?: false
        val dryRun = arguments["dry_run"] as? Boolean ?: false
        
        try {
            // Read current content
            val currentContent = file.readText()
            
            // Check if old_content exists
            if (!currentContent.contains(oldContent)) {
                return@withContext ToolResult.Error(
                    "Text not found in file. Make sure 'old_content' matches exactly (including whitespace).",
                    ErrorType.NOT_FOUND,
                    recoverable = true
                )
            }
            
            // Count occurrences
            var occurrences = 0
            var searchIndex = 0
            while (true) {
                val foundIndex = currentContent.indexOf(oldContent, searchIndex)
                if (foundIndex == -1) break
                occurrences++
                searchIndex = foundIndex + oldContent.length
                if (occurrences >= MAX_REPLACEMENTS) break
            }
            
            // Perform replacement
            val newFullContent = if (replaceAll) {
                currentContent.replace(oldContent, newContent)
            } else {
                currentContent.replaceFirst(oldContent, newContent)
            }
            
            val replacementCount = if (replaceAll) occurrences else 1
            
            // Dry run - just preview
            if (dryRun) {
                return@withContext ToolResult.Success(
                    output = buildString {
                        append("DRY RUN - No changes made\n\n")
                        append("Found $occurrences occurrence(s)\n")
                        append("Would replace $replacementCount occurrence(s)\n\n")
                        append("Preview of first change:\n")
                        append("--- Before ---\n")
                        append(oldContent.take(200))
                        if (oldContent.length > 200) append("...")
                        append("\n--- After ---\n")
                        append(newContent.take(200))
                        if (newContent.length > 200) append("...")
                    },
                    metadata = mapOf(
                        "dry_run" to true,
                        "occurrences" to occurrences,
                        "would_replace" to replacementCount
                    )
                )
            }
            
            // Create backup
            val backupDir = File(context.cacheDir, "backups")
            backupDir.mkdirs()
            val backupFile = File(backupDir, "${file.name}.bak.${System.currentTimeMillis()}")
            file.copyTo(backupFile, overwrite = true)
            
            // Write new content
            file.writeText(newFullContent)
            
            ToolResult.Success(
                output = buildString {
                    append("Successfully replaced $replacementCount occurrence(s)\n")
                    append("Backup saved to: ${backupFile.name}\n\n")
                    if (occurrences > 1 && !replaceAll) {
                        append("Note: Found $occurrences total occurrences, replaced only first. ")
                        append("Use all_occurrences=true to replace all.")
                    }
                },
                metadata = mapOf(
                    "path" to pathStr,
                    "replacements" to replacementCount,
                    "total_occurrences" to occurrences,
                    "backup" to backupFile.absolutePath
                )
            )
            
        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: ${e.message}",
                ErrorType.PERMISSION_ERROR
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Edit failed: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }

    private fun applyUnifiedDiff(file: java.io.File, original: String, diff: String): ToolResult {
        return try {
            val lines = original.lines().toMutableList()
            val hunkRegex = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
            val diffLines = diff.lines()
            var i = 0
            var lineOffset = 0
            while (i < diffLines.size) {
                val hunkMatch = hunkRegex.find(diffLines[i])
                if (hunkMatch != null) {
                    val origStart = hunkMatch.groupValues[1].toInt() - 1 + lineOffset
                    i++
                    val insertAt = origStart.coerceIn(0, lines.size)
                    val removals = mutableListOf<String>()
                    val additions = mutableListOf<String>()
                    while (i < diffLines.size && !diffLines[i].startsWith("@@")) {
                        val dl = diffLines[i]
                        when {
                            dl.startsWith("-") -> removals.add(dl.drop(1))
                            dl.startsWith("+") -> additions.add(dl.drop(1))
                        }
                        i++
                    }
                    // Remove lines
                    var removed = 0
                    removals.forEach { rem ->
                        val idx = lines.indexOfFirst { it == rem }
                        if (idx >= 0) { lines.removeAt(idx); removed++ }
                    }
                    // Insert additions
                    additions.forEachIndexed { addIdx, add ->
                        val pos = (insertAt + addIdx).coerceIn(0, lines.size)
                        lines.add(pos, add)
                    }
                    lineOffset += additions.size - removed
                } else i++
            }
            val result = lines.joinToString("\n")
            val backup = java.io.File(file.parent, "${file.name}.bak.${System.currentTimeMillis()}")
            file.copyTo(backup, overwrite = true)
            file.writeText(result, Charsets.UTF_8)
            ToolResult.Success(
                "Diff applied to ${file.name}. Backup: ${backup.name}",
                metadata = mapOf("path" to file.absolutePath)
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to apply diff: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}
