package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
) : Tool, ContextAwareTool {

    companion object {
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        const val MAX_REPLACEMENTS = 100
        const val MAX_EXPANDED_DOCUMENT_BYTES = 20L * 1024 * 1024

        // Document formats that can be edited
        val DOCUMENT_EXTENSIONS = setOf(
            "docx", "xlsx", "pptx", "odt", "ods", "odp"
        )
    }

    override val name = "edit_file"

    override val description = "Edit a file by replacing specific text, or apply a unified diff/patch. Supports text files and document formats (DOCX, XLSX, PPTX, ODT, ODS, ODP). Use 'old_content'+'new_content' for text replacement, or 'diff' for patch mode (text files only)."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult = withContext(Dispatchers.IO) {

        val pathStr = (arguments["path"] ?: arguments["file_path"] ?: arguments["filePath"] ?: arguments["file"]) as? String
            ?: return@withContext ToolResult.Error("Missing required argument: path", ErrorType.VALIDATION_ERROR)

        // ── Diff/patch mode (replaces apply_diff) ────────────────────────
        val diffStr = arguments["diff"] as? String
        if (diffStr != null) {
            when (val v = commandValidator.validatePath(pathStr, isWrite = true)) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
                is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                    return@withContext ToolResult.RequiresConfirmation(v.reason, pathStr)
                }
                is ValidationResult.Allowed -> {}
            }
            val file = java.io.File(pathStr)
            if (!file.exists()) return@withContext ToolResult.Error("File not found: $pathStr", ErrorType.NOT_FOUND)
            val content = file.readText(Charsets.UTF_8)
            // Parse unified diff — apply @@ hunks
            return@withContext applyUnifiedDiff(file, content, diffStr)
        }

        val oldContent = (arguments["old_content"] ?: arguments["old_text"] ?: arguments["target_content"] ?: arguments["TargetContent"]) as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: old_content",
                ErrorType.VALIDATION_ERROR
            )

        val newContent = (arguments["new_content"] ?: arguments["new_text"] ?: arguments["replacement_content"] ?: arguments["ReplacementContent"]) as? String
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
            is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                return@withContext ToolResult.RequiresConfirmation(
                    validation.reason,
                    "Path: $pathStr\nReplace: ${oldContent.take(50)}...\nWith: ${newContent.take(50)}..."
                )
            }
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

        // Check if this is a document format
        val ext = file.extension.lowercase()
        if (ext in DOCUMENT_EXTENSIONS) {
            return@withContext editDocument(file, oldContent, newContent, ext)
        }

        val replaceAll = arguments["all_occurrences"] as? Boolean ?: false
        val dryRun = arguments["dry_run"] as? Boolean ?: false

        try {
            // Read current content
            var currentContent = file.readText()
            var targetOldContent = oldContent
            var targetNewContent = newContent

            // Check if old_content exists with exact match, CRLF tolerance, or whitespace/indentation tolerance
            var matchedSubstring: String? = null

            if (currentContent.contains(targetOldContent)) {
                matchedSubstring = targetOldContent
            } else {
                val normalizedCurrent = currentContent.replace("\r\n", "\n")
                val normalizedOld = targetOldContent.replace("\r\n", "\n")
                if (normalizedCurrent.contains(normalizedOld)) {
                    currentContent = normalizedCurrent
                    targetOldContent = normalizedOld
                    targetNewContent = targetNewContent.replace("\r\n", "\n")
                    matchedSubstring = targetOldContent
                } else {
                    // Tier 3: Line-by-line whitespace-tolerant sliding window matcher
                    val trimmedMatch = findTrimmedLineBlock(currentContent, targetOldContent)
                    if (trimmedMatch != null) {
                        matchedSubstring = trimmedMatch
                        targetOldContent = trimmedMatch
                    }
                }
            }

            if (matchedSubstring == null) {
                val fileLines = currentContent.lines()
                val targetLines = targetOldContent.lines()
                val contextHint = findBestContextHint(fileLines, targetLines)

                return@withContext ToolResult.Error(
                    "Text not found in file '$pathStr'. Make sure 'old_content' matches the actual file content.\n" +
                    "File total lines: ${fileLines.size}.\n\n" +
                    contextHint + "\n\n" +
                    "Hint: Use 'read_file' or refer to the context lines above to align indentation and line breaks.",
                    ErrorType.NOT_FOUND,
                    recoverable = true
                )
            }

            // Count occurrences
            var occurrences = 0
            var searchIndex = 0
            while (true) {
                val foundIndex = currentContent.indexOf(targetOldContent, searchIndex)
                if (foundIndex == -1) break
                occurrences++
                searchIndex = foundIndex + targetOldContent.length
                if (occurrences >= MAX_REPLACEMENTS) break
            }

            // Perform replacement
            val newFullContent = if (replaceAll) {
                currentContent.replace(targetOldContent, targetNewContent)
            } else {
                currentContent.replaceFirst(targetOldContent, targetNewContent)
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

            // FIX #7: Temp-file replacement prevents partial writes where rename is supported.
            // to prevent file corruption if the process is killed mid-write.
            val tempFile = File.createTempFile(".edit_", ".tmp", file.parentFile ?: File(context.cacheDir, "backups").also { it.mkdirs() })
            try {
                tempFile.writeText(newFullContent)
                if (!tempFile.renameTo(file)) {
                    // Fallback for cross-filesystem (e.g. external storage)
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }

            ToolResult.Success(
                output = buildString {
                    append("Successfully replaced $replacementCount occurrence(s)\n")
                    appendLine()
                    if (occurrences > 1 && !replaceAll) {
                        append("Note: Found $occurrences total occurrences, replaced only first. ")
                        append("Use all_occurrences=true to replace all.")
                    }
                },
                metadata = mapOf(
                    "path" to pathStr,
                    "replacements" to replacementCount,
                    "total_occurrences" to occurrences
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
                    // FIX #17: Use origStart (from hunk header) as the anchor position for all
                    // removals and insertions — never search by exact string match across the
                    // whole file, which would remove the wrong line when duplicates exist.
                    val origStart = hunkMatch.groupValues[1].toInt() - 1 + lineOffset
                    i++
                    val hunkLines = mutableListOf<Pair<Char, String>>() // '+'/'-'/' ' + content
                    while (i < diffLines.size && !diffLines[i].startsWith("@@")) {
                        val dl = diffLines[i]
                        when {
                            dl.startsWith("-") -> hunkLines.add('-' to dl.drop(1))
                            dl.startsWith("+") -> hunkLines.add('+' to dl.drop(1))
                            dl.startsWith(" ") -> hunkLines.add(' ' to dl.drop(1))
                            dl.isBlank()        -> hunkLines.add(' ' to "")
                        }
                        i++
                    }
                    // Apply hunk line-by-line at the anchored position
                    var cursor = origStart.coerceIn(0, lines.size)
                    var removed = 0
                    var added = 0
                    for ((op, content) in hunkLines) {
                        when (op) {
                            ' ' -> cursor++ // context line — advance cursor
                            '-' -> {
                                // Remove line at cursor position (position-based, not search-based)
                                if (cursor < lines.size) {
                                    lines.removeAt(cursor)
                                    removed++
                                }
                            }
                            '+' -> {
                                // Insert at cursor position
                                val pos = cursor.coerceIn(0, lines.size)
                                lines.add(pos, content)
                                cursor++
                                added++
                            }
                        }
                    }
                    lineOffset += added - removed
                } else i++
            }
            // FIX 2.5: Preserve trailing newline — original.lines() on "a\nb\n" yields ["a","b",""]
            // so joinToString("\n") would drop the final newline. Restore it explicitly.
            val hasTrailingNewline = original.endsWith("\n")
            val result = lines.joinToString("\n") + if (hasTrailingNewline) "\n" else ""
            // FIX #7: Temp-file replacement for diff mode too.
            val tempFile = java.io.File.createTempFile(".diff_", ".tmp", file.parentFile ?: file)
            try {
                tempFile.writeText(result, Charsets.UTF_8)
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
            ToolResult.Success(
                "Diff applied to ${file.name}.",
                metadata = mapOf("path" to file.absolutePath)
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to apply diff: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }

    // ── Document Edit ───────────────────────────────────────────────────────────
    // Strategy: replace inside XML text nodes — match plain text, not raw XML markup.
    // This handles the case where oldContent spans a single XML <t> or <w:t> text node.
    private fun editDocument(file: File, oldContent: String, newContent: String, ext: String): ToolResult {
        return try {

            val targetXmlPaths = when (ext) {
                "docx" -> listOf("word/document.xml")
                "xlsx" -> listOf("xl/sharedStrings.xml") + (1..10).map { "xl/worksheets/sheet$it.xml" }
                "pptx" -> (1..50).map { "ppt/slides/slide$it.xml" }
                "odt", "ods", "odp" -> listOf("content.xml")
                else -> return ToolResult.Error(
                    "Unsupported document format for editing: $ext",
                    ErrorType.VALIDATION_ERROR
                )
            }

            val (changed, replacements) = editZipXmlTextNodes(file, targetXmlPaths, oldContent, newContent)

            if (!changed) {
                return ToolResult.Error(
                    "Text not found in document: \"${oldContent.take(80)}\".\n" +
                    "Make sure the text exists exactly as typed in the document.",
                    ErrorType.NOT_FOUND,
                    recoverable = true
                )
            }

            ToolResult.Success(
                output = "Successfully edited document: ${file.name} ($replacements replacement(s))",
                metadata = mapOf(
                    "path" to file.absolutePath,
                    "format" to ext,
                    "replacements" to replacements
                )
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to edit document: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }

    // ── Helper: Edit XML text nodes inside ZIP entries ──────────────────────────
    // Replaces oldContent→newContent only within XML text content (not attributes/tags).
    // Returns (changed: Boolean, totalReplacements: Int)
    private fun editZipXmlTextNodes(
        file: File,
        targetPaths: List<String>,
        oldContent: String,
        newContent: String
    ): Pair<Boolean, Int> {
        // Read all entries into memory first
        val entries = mutableMapOf<String, ByteArray>()
        val budget = ByteReadBudget(MAX_EXPANDED_DOCUMENT_BYTES)
        ZipInputStream(file.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries[entry.name] = budget.readBytes(zipIn)
                entry = zipIn.nextEntry
            }
        }

        var totalReplacements = 0
        val modifiedEntries = mutableMapOf<String, ByteArray>()

        for (path in targetPaths) {
            val bytes = entries[path] ?: continue
            val xmlStr = bytes.toString(Charsets.UTF_8)

            // Replace plain text within XML text node values.
            // We escape oldContent for XML comparison, then replace.
            val oldEscaped = oldContent.escapeXmlText()
            val newEscaped = newContent.escapeXmlText()

            // Count occurrences before replacing
            var count = 0
            var searchIdx = 0
            while (true) {
                val idx = xmlStr.indexOf(oldEscaped, searchIdx)
                if (idx == -1) break
                count++
                searchIdx = idx + oldEscaped.length
            }

            if (count > 0) {
                val edited = xmlStr.replace(oldEscaped, newEscaped)
                modifiedEntries[path] = edited.toByteArray(Charsets.UTF_8)
                totalReplacements += count
            }
        }

        if (totalReplacements == 0) return Pair(false, 0)

        // Write back ZIP with modified entries
        val tempFile = File.createTempFile(".doc_edit_", ".tmp", file.parentFile ?: file)
        ZipOutputStream(tempFile.outputStream()).use { zipOut ->
            for ((name, bytes) in entries) {
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(modifiedEntries[name] ?: bytes)
                zipOut.closeEntry()
            }
        }

        // Atomic replace
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }

        return Pair(true, totalReplacements)
    }

    /**
     * Attempts a sliding-window match where leading/trailing whitespaces on each line are ignored.
     * Returns the exact original substring in [fullContent] that corresponds to the matched block.
     */
    private fun findTrimmedLineBlock(fullContent: String, targetOld: String): String? {
        val targetLines = targetOld.replace("\r\n", "\n").lines()
        val normalizedTargetLines = targetLines.map { it.trim() }
        if (normalizedTargetLines.all { it.isEmpty() }) return null

        val fileLines = fullContent.replace("\r\n", "\n").lines()
        if (fileLines.size < targetLines.size) return null

        val windowSize = targetLines.size
        for (i in 0..(fileLines.size - windowSize)) {
            var allMatch = true
            for (j in 0 until windowSize) {
                if (fileLines[i + j].trim() != normalizedTargetLines[j]) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) {
                // Reconstruct the exact slice from fullContent
                val matchedBlock = fileLines.subList(i, i + windowSize).joinToString("\n")
                if (fullContent.contains(matchedBlock)) {
                    return matchedBlock
                }
            }
        }
        return null
    }

    /**
     * Identifies the closest line region in the file to help the agent locate the target,
     * providing surrounding lines with 1-based line numbers.
     */
    private fun findBestContextHint(fileLines: List<String>, targetLines: List<String>): String {
        if (fileLines.isEmpty()) return "File is empty."

        val firstMeaningfulTarget = targetLines.firstOrNull { it.isNotBlank() }?.trim() ?: ""
        var bestIndex = 0
        var bestScore = -1

        if (firstMeaningfulTarget.isNotEmpty()) {
            val targetTokens = firstMeaningfulTarget.split(Regex("""\W+""")).filter { it.isNotBlank() }.toSet()
            for ((index, line) in fileLines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed == firstMeaningfulTarget) {
                    bestIndex = index
                    bestScore = 100
                    break
                }
                if (targetTokens.isNotEmpty()) {
                    val lineTokens = trimmed.split(Regex("""\W+""")).filter { it.isNotBlank() }.toSet()
                    val overlap = lineTokens.intersect(targetTokens).size
                    if (overlap > bestScore) {
                        bestScore = overlap
                        bestIndex = index
                    }
                }
            }
        }

        val startLine = (bestIndex - 4).coerceAtLeast(0)
        val endLine = (bestIndex + 5).coerceAtMost(fileLines.size)
        val snippet = fileLines.subList(startLine, endLine).mapIndexed { offset, lineText ->
            val lineNum = startLine + offset + 1
            val marker = if (startLine + offset == bestIndex) ">>" else "  "
            "$marker ${lineNum.toString().padStart(4, ' ')}: $lineText"
        }.joinToString("\n")

        return "Closest matching section around line ${bestIndex + 1}:\n" +
               "--- File Content Preview ---\n" +
               snippet + "\n" +
               "----------------------------"
    }

}
