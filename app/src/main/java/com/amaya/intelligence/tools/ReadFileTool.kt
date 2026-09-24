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
// PDF support temporarily disabled - PDFBox-Android not available in public repos
// import com.tom_roush.pdfbox.pdmodel.PDDocument
// import com.tom_roush.pdfbox.text.PDFTextStripper
// import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Read file content using java.io.File API for Android compatibility.
 */
@Singleton
class ReadFileTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandValidator: CommandValidator
) : Tool, ContextAwareTool {

    companion object {
        const val DEFAULT_MAX_SIZE = 1024 * 1024L  // 1MB
        const val ABSOLUTE_MAX_SIZE = 10 * 1024 * 1024L  // 10MB
        const val BINARY_CHECK_SIZE = 8000
        const val MAX_BATCH_FILES = 10
        const val DEFAULT_BATCH_MAX_LINES = 100

        // Document formats supported (PDF disabled - dependency not available)
        val DOCUMENT_EXTENSIONS = setOf(
            "docx", "xlsx", "pptx", "odt", "ods", "odp", "rtf"
        )
    }

    override val name = "read_file"

    override val description = "Read text files and document formats (DOCX, XLSX, PPTX, ODT, ODS, RTF). Pass 'path' for single file or 'paths' array for batch. Use 'outline: true' to extract structural definitions (classes, functions, headers) with line numbers to conserve context. Use 'info_only' for metadata only. Documents are automatically extracted to plain text."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult = withContext(Dispatchers.IO) {

        // ── Batch mode: paths[] ──────────────────────────────────────────
        val rawPaths = arguments["paths"] as? List<*>
        val pathsList = rawPaths?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }
        if (rawPaths != null && pathsList?.size != rawPaths.size) {
            return@withContext ToolResult.Error(
                "Every paths item must be a non-blank string",
                ErrorType.VALIDATION_ERROR
            )
        }
        if (pathsList != null) {
            if (pathsList.isEmpty()) return@withContext ToolResult.Error(
                "Missing required argument: path or paths",
                ErrorType.VALIDATION_ERROR
            )
            return@withContext executeBatch(pathsList, arguments, executionContext)
        }

        val pathStr = (arguments["path"] ?: arguments["file_path"] ?: arguments["filePath"] ?: arguments["file"]) as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path or paths",
                ErrorType.VALIDATION_ERROR
            )

        // ── Info-only mode ───────────────────────────────────────────────
        val infoOnly = arguments["info_only"] as? Boolean ?: false
        if (infoOnly) {
            return@withContext executeInfo(pathStr, executionContext.confirmed)
        }

        // Validate path access
        when (val validation = commandValidator.validatePath(pathStr, isWrite = false)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                return@withContext ToolResult.RequiresConfirmation(validation.reason, "Path: $pathStr")
            }
            is ValidationResult.Allowed -> { /* proceed */ }
        }

        val file = File(pathStr)

        if (!file.exists()) {
            val parent = file.parentFile
            val suggestions = if (parent != null && parent.exists() && parent.isDirectory) {
                parent.listFiles()?.filter { it.isFile }?.map { it.name }
                    ?.filter { it.contains(file.nameWithoutExtension, ignoreCase = true) || file.nameWithoutExtension.contains(it.substringBeforeLast('.'), ignoreCase = true) }
                    ?.take(3)
                    .orEmpty()
            } else emptyList()

            val suggestionHint = if (suggestions.isNotEmpty()) {
                " (Possible matches in directory: ${suggestions.joinToString(", ")})"
            } else ""

            return@withContext ToolResult.Error(
                "File does not exist: $pathStr$suggestionHint",
                ErrorType.NOT_FOUND
            )
        }

        if (!file.isFile) {
            val message = if (file.isDirectory) {
                "Path is a directory, not a file: $pathStr. Use list_files to inspect directory contents."
            } else {
                "Path is not a regular file: $pathStr"
            }
            return@withContext ToolResult.Error(
                message,
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
        val outline = arguments["outline"] as? Boolean ?: false

        try {
            val fileSize = file.length()

            // Size check
            if (fileSize > maxSize && !outline) {
                return@withContext ToolResult.Error(
                    "File too large: ${formatSize(fileSize)} (max: ${formatSize(maxSize)}). " +
                    "Use outline: true or start_line/end_line to read a portion.",
                    ErrorType.SIZE_LIMIT,
                    recoverable = true
                )
            }

            // Check if this is a document format
            val ext = file.extension.lowercase()
            if (ext in DOCUMENT_EXTENSIONS) {
                return@withContext extractDocument(file, startLine, endLine)
            }

            // Without this a PDF falls through to the binary check and comes back as
            // "appears to be binary", which reads like a bug rather than a missing format.
            if (ext == "pdf") {
                return@withContext ToolResult.Error(
                    "PDF support is currently unavailable. Supported document formats: ${DOCUMENT_EXTENSIONS.joinToString()}",
                    ErrorType.VALIDATION_ERROR
                )
            }

            // Binary check for non-document files
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

            val lineCountOnly = arguments["line_count"] as? Boolean ?: false
            if (lineCountOnly) {
                return@withContext ToolResult.Success(
                    output = "Total lines: $totalLines",
                    metadata = mapOf(
                        "path" to pathStr,
                        "size" to fileSize,
                        "total_lines" to totalLines
                    )
                )
            }

            if (outline) {
                val outlineText = extractOutline(allLines)
                return@withContext ToolResult.Success(
                    output = outlineText,
                    metadata = mapOf(
                        "path" to pathStr,
                        "size" to fileSize,
                        "total_lines" to totalLines,
                        "mode" to "outline"
                    )
                )
            }

            // Smart line limiting
            val maxDisplayLines = 200

            // Handle line range if specified
            val (output, displayedRange) = if (startLine != null || endLine != null) {
                if (startLine != null && endLine != null && endLine < startLine) {
                    return@withContext ToolResult.Error(
                        "end_line ($endLine) must be greater than or equal to start_line ($startLine)",
                        ErrorType.VALIDATION_ERROR
                    )
                }
                val start = (startLine ?: 1) - 1
                val end = (endLine ?: totalLines).coerceAtMost(totalLines)

                if (start >= totalLines) {
                    return@withContext ToolResult.Error(
                        "start_line ($startLine) exceeds file length ($totalLines lines)",
                        ErrorType.VALIDATION_ERROR
                    )
                }
                if (end < start) {
                    return@withContext ToolResult.Error(
                        "end_line ($endLine) resolved before start_line ($startLine)",
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

    private fun formatSize(bytes: Long): String = formatFileSize(bytes)

    // ── Batch mode ───────────────────────────────────────────────────────────
    //
    // One tool call carries one ToolResult, so N files have to share a single output
    // string. Every section is therefore introduced by a header the UI can parse back
    // into one card per file:
    //
    //     === [2/3] app/src/main/Foo.kt — 312 lines ===
    //     === [3/3] missing.kt — ERROR: File not found ===
    //
    // The index prefix and the status suffix are what make the delimiter unambiguous —
    // the old "=== name ===" collided with the "=== Sheet 1 ===" markers the spreadsheet
    // extractor emits, and with any source line that happened to look like a banner.
    // Header paths are workspace-relative, so two files with the same basename stay
    // distinguishable.
    private suspend fun executeBatch(
        paths: List<String>,
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult =
        withContext(Dispatchers.IO) {
            if (paths.size > MAX_BATCH_FILES) {
                return@withContext ToolResult.Error(
                    "Too many files: ${paths.size} (max: $MAX_BATCH_FILES)",
                    ErrorType.SIZE_LIMIT
                )
            }

            // Validate every path before reading anything. Raising the confirmation from
            // inside the read loop threw away the files already read and made the retry
            // redo all of them.
            val denials = mutableMapOf<String, String>()
            paths.forEach { path ->
                when (val v = commandValidator.validatePath(path, isWrite = false)) {
                    is ValidationResult.Denied -> denials[path] = v.reason
                    is ValidationResult.RequiresConfirmation ->
                        if (!executionContext.confirmed) {
                            return@withContext ToolResult.RequiresConfirmation(v.reason, "Path: $path")
                        }
                    is ValidationResult.Allowed -> Unit
                }
            }

            val maxLines = (arguments["max_lines"] as? Number)?.toInt()?.coerceIn(1, 500)
                ?: DEFAULT_BATCH_MAX_LINES
            val summaryOnly = arguments["summary_only"] as? Boolean ?: false
            val maxSize = (arguments["max_size"] as? Number)?.toLong()?.coerceIn(1, ABSOLUTE_MAX_SIZE)
                ?: DEFAULT_MAX_SIZE
            val charset = runCatching { Charset.forName(arguments["encoding"] as? String ?: "UTF-8") }
                .getOrElse { Charsets.UTF_8 }

            var read = 0
            var truncated = 0
            val output = buildString {
                paths.forEachIndexed { idx, path ->
                    val section = readBatchSection(path, denials[path], maxLines, summaryOnly, maxSize, charset)
                    appendLine(
                        batchSectionHeader(
                            index = idx + 1,
                            total = paths.size,
                            path = relativizePath(path, executionContext.workspacePath),
                            note = section.note
                        )
                    )
                    if (section.body.isNotEmpty()) appendLine(section.body)
                    appendLine()
                    if (!section.failed) read++
                    if (section.truncated) truncated++
                }
            }

            ToolResult.Success(
                output.trimEnd(),
                metadata = mapOf(
                    "files_requested" to paths.size,
                    "files_read" to read,
                    "files_failed" to (paths.size - read),
                    "files_truncated" to truncated
                )
            )
        }

    /** Header the batch UI parses back into one read card per file. */
    private fun batchSectionHeader(index: Int, total: Int, path: String, note: String): String =
        "=== [$index/$total] $path — $note ==="

    private class BatchSection(
        val note: String,
        val body: String = "",
        val failed: Boolean = false,
        val truncated: Boolean = false
    )

    private fun readBatchSection(
        path: String,
        denialReason: String?,
        maxLines: Int,
        summaryOnly: Boolean,
        maxSize: Long,
        charset: Charset
    ): BatchSection {
        if (denialReason != null) return BatchSection("BLOCKED: $denialReason", failed = true)

        val file = File(path)
        if (!file.exists()) return BatchSection("ERROR: File not found", failed = true)
        if (!file.isFile) return BatchSection("ERROR: Not a regular file", failed = true)
        if (file.length() > maxSize) return BatchSection(
            "ERROR: Too large — ${formatSize(file.length())} (max ${formatSize(maxSize)}); " +
                "read it on its own with start_line/end_line",
            failed = true
        )

        // summary_only keeps the head and the tail; the plain path keeps the head only.
        val headLimit = if (summaryOnly) 10 else maxLines
        val tailLimit = if (summaryOnly) 10 else 0

        return try {
            val ext = file.extension.lowercase()
            when {
                ext == "pdf" -> BatchSection("ERROR: PDF support is currently unavailable", failed = true)
                ext in DOCUMENT_EXTENSIONS -> {
                    val text = DocumentTextExtractor.extractDocumentText(file, ext)
                    val window = LineWindow(headLimit, tailLimit).also { text.lineSequence().forEach(it::add) }
                    BatchSection(
                        note = "${ext.uppercase()} · ${windowNote(window)}",
                        body = window.render(),
                        truncated = window.truncated
                    )
                }
                isBinaryFile(file) -> BatchSection("ERROR: Binary file", failed = true)
                else -> {
                    // Streamed: readLines() materialised the whole file before take(maxLines)
                    // threw it away, ten files at a time.
                    val window = LineWindow(headLimit, tailLimit)
                    file.bufferedReader(charset).useLines { lines -> lines.forEach(window::add) }
                    BatchSection(
                        note = windowNote(window),
                        body = window.render(),
                        truncated = window.truncated
                    )
                }
            }
        } catch (e: Exception) {
            BatchSection("ERROR: ${e.message}", failed = true)
        }
    }

    private fun windowNote(window: LineWindow): String =
        if (window.truncated) "${window.total} lines (showing ${window.shown})"
        else "${window.total} lines"

    /**
     * Head/tail line window over a stream, so a batch never holds a whole file in memory
     * just to print its first hundred lines.
     */
    private class LineWindow(private val headLimit: Int, private val tailLimit: Int) {
        private val head = ArrayList<String>(minOf(headLimit, 512))
        private val tail = ArrayDeque<String>()
        var total = 0
            private set

        fun add(line: String) {
            total++
            if (head.size < headLimit) head += line
            if (tailLimit > 0) {
                tail.addLast(line)
                if (tail.size > tailLimit) tail.removeFirst()
            }
        }

        private val overlap: Int get() = (head.size + tail.size - total).coerceAtLeast(0)
        val shown: Int get() = head.size + tail.size - overlap
        val truncated: Boolean get() = shown < total

        fun render(): String {
            val omitted = total - head.size - tail.size
            return if (omitted > 0) {
                (head + "... ($omitted lines omitted — re-read this file alone for the rest) ..." + tail)
                    .joinToString("\n")
            } else {
                (head + tail.drop(overlap)).joinToString("\n")
            }
        }
    }

    /** Path as the model wrote it: workspace-relative, so basenames stay distinguishable. */
    private fun relativizePath(path: String, workspacePath: String?): String {
        val root = workspacePath?.trimEnd('/', '\\').orEmpty()
        if (root.isNotEmpty() && path.startsWith(root)) {
            return path.removePrefix(root).trimStart('/', '\\').ifEmpty { File(path).name }
        }
        return path
    }

    // ── Info-only mode ───────────────────────────────────────────────────────
    private suspend fun executeInfo(pathStr: String, confirmed: Boolean): ToolResult = withContext(Dispatchers.IO) {
        when (val v = commandValidator.validatePath(pathStr, isWrite = false)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
            is ValidationResult.RequiresConfirmation -> if (!confirmed) {
                return@withContext ToolResult.RequiresConfirmation(v.reason, pathStr)
            }
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

    // ── Document Extraction ─────────────────────────────────────────────────────
    private suspend fun extractDocument(file: File, startLine: Int?, endLine: Int?): ToolResult = withContext(Dispatchers.IO) {
        try {
            val ext = file.extension.lowercase()
            if (ext !in DOCUMENT_EXTENSIONS) return@withContext ToolResult.Error(
                "Unsupported document format: $ext. Supported: ${DOCUMENT_EXTENSIONS.joinToString()}",
                ErrorType.VALIDATION_ERROR
            )
            val extractedText = DocumentTextExtractor.extractDocumentText(file, ext)

            val allLines = extractedText.lines()
            val totalLines = allLines.size
            val wordCount = extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size

            // Apply line range if specified
            val maxDisplayLines = 200
            val (output, displayedRange) = if (startLine != null || endLine != null) {
                val start = (startLine ?: 1) - 1
                val end = (endLine ?: totalLines).coerceAtMost(totalLines)

                if (start >= totalLines) {
                    return@withContext ToolResult.Error(
                        "start_line ($startLine) exceeds document length ($totalLines lines)",
                        ErrorType.VALIDATION_ERROR
                    )
                }

                val rangeLines = allLines.subList(start, end)
                Pair(rangeLines.joinToString("\n"), "Lines ${start + 1}-$end of $totalLines")
            } else {
                if (totalLines > maxDisplayLines) {
                    val limitedLines = allLines.take(maxDisplayLines)
                    val output = buildString {
                        append("[${ext.uppercase()} Document: ${file.name}]\n")
                        append("Lines: $totalLines | Words: ~$wordCount\n\n")
                        append(limitedLines.joinToString("\n"))
                        append("\n\n--- Showing lines 1-$maxDisplayLines of $totalLines ---")
                        append("\nTo see more, use: start_line and end_line parameters")
                    }
                    Pair(output, "Lines 1-$maxDisplayLines of $totalLines (truncated)")
                } else {
                    val output = buildString {
                        append("[${ext.uppercase()} Document: ${file.name}]\n")
                        append("Lines: $totalLines | Words: ~$wordCount\n\n")
                        append(extractedText)
                    }
                    Pair(output, "All $totalLines lines")
                }
            }

            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "path" to file.absolutePath,
                    "format" to ext,
                    "size" to file.length(),
                    "total_lines" to totalLines,
                    "word_count" to wordCount,
                    "displayed" to displayedRange
                )
            )

        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to extract document: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }

    private fun extractOutline(lines: List<String>): String {
        val structuralKeywords = listOf(
            "class ", "interface ", "enum ", "object ", "fun ", "val ", "var ",
            "def ", "async def ", "function ", "type ", "export ", "public ",
            "private ", "protected ", "#", "package ", "import "
        )
        val outlineEntries = mutableListOf<String>()
        var importCount = 0

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
                importCount++
                if (importCount == 1) {
                    outlineEntries.add("L${index + 1}: $trimmed (and subsequent imports...)")
                }
                return@forEachIndexed
            }
            if (trimmed.isBlank() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                return@forEachIndexed
            }
            val matches = structuralKeywords.any { keyword ->
                trimmed.startsWith(keyword) || trimmed.contains(" fun ") || trimmed.contains(" class ") || trimmed.contains(" interface ")
            }
            if (matches) {
                val preview = if (trimmed.length > 120) trimmed.take(117) + "..." else trimmed
                outlineEntries.add("L${index + 1}: $preview")
            }
        }

        return buildString {
            appendLine("=== FILE OUTLINE (${outlineEntries.size} structural declarations found in ${lines.size} lines) ===")
            appendLine("Use start_line and end_line with read_file to inspect specific implementations:")
            appendLine()
            if (outlineEntries.isEmpty()) {
                appendLine("No top-level class/function declarations found. File contains plain text/data.")
            } else {
                outlineEntries.forEach { appendLine(it) }
            }
            appendLine("=== END OF OUTLINE ===")
        }.trim()
    }
}
