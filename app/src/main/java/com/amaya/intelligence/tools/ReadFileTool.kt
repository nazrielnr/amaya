package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
// PDF support temporarily disabled - PDFBox-Android not available in public repos
// import com.tom_roush.pdfbox.pdmodel.PDDocument
// import com.tom_roush.pdfbox.text.PDFTextStripper
// import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Read file content using java.io.File API for Android compatibility.
 */
@Singleton
class ReadFileTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val DEFAULT_MAX_SIZE = 1024 * 1024L  // 1MB
        const val ABSOLUTE_MAX_SIZE = 10 * 1024 * 1024L  // 10MB
        const val BINARY_CHECK_SIZE = 8000
        
        // Document formats supported (PDF disabled - dependency not available)
        val DOCUMENT_EXTENSIONS = setOf(
            "docx", "xlsx", "pptx", "odt", "ods", "odp", "rtf"
        )
    }
    
    override val name = "read_file"

    override val description = "Read text files and document formats (DOCX, XLSX, PPTX, ODT, ODS, RTF). Pass 'path' for single file or 'paths' array for batch. Use 'info_only' for metadata only. Documents are automatically extracted to plain text. Note: PDF support currently unavailable."

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
            
            // Check if this is a document format
            val ext = file.extension.lowercase()
            if (ext in DOCUMENT_EXTENSIONS) {
                return@withContext extractDocument(file, startLine, endLine)
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
                        // Check if document format
                        val ext = file.extension.lowercase()
                        if (ext in DOCUMENT_EXTENSIONS) {
                            val extractedText = when (ext) {
                                "pdf" -> "ERROR: PDF support unavailable"
                                "docx" -> extractDocx(file)
                                "xlsx" -> extractXlsx(file)
                                "pptx" -> extractPptx(file)
                                "odt" -> extractOdt(file)
                                "ods" -> extractOds(file)
                                "odp" -> extractOdp(file)
                                "rtf" -> extractRtf(file)
                                else -> "ERROR: Unsupported format"
                            }
                            val lines = extractedText.lines()
                            appendLine("[${ext.uppercase()}] Words: ~${extractedText.split(Regex("\\s+")).size}")
                            if (lines.size > maxLines) {
                                lines.take(maxLines).forEach { appendLine(it) }
                                appendLine("... (${lines.size - maxLines} more lines)")
                            } else {
                                appendLine(extractedText)
                            }
                        } else {
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

    // ── Document Extraction ─────────────────────────────────────────────────────
    private suspend fun extractDocument(file: File, startLine: Int?, endLine: Int?): ToolResult = withContext(Dispatchers.IO) {
        try {
            val ext = file.extension.lowercase()
            val extractedText = when (ext) {
                "pdf" -> return@withContext ToolResult.Error(
                    "PDF support is currently unavailable. Supported formats: DOCX, XLSX, PPTX, ODT, ODS, RTF",
                    ErrorType.VALIDATION_ERROR
                )
                "docx" -> extractDocx(file)
                "xlsx" -> extractXlsx(file)
                "pptx" -> extractPptx(file)
                "odt" -> extractOdt(file)
                "ods" -> extractOds(file)
                "odp" -> extractOdp(file)
                "rtf" -> extractRtf(file)
                else -> return@withContext ToolResult.Error(
                    "Unsupported document format: $ext",
                    ErrorType.VALIDATION_ERROR
                )
            }
            
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
    
    // ── PDF Extraction ──────────────────────────────────────────────────────────
    // Disabled - PDFBox-Android dependency not available in public repos
    // private fun extractPdf(file: File): String {
    //     return PDDocument.load(file).use { document ->
    //         val stripper = PDFTextStripper()
    //         stripper.sortByPosition = true
    //         stripper.getText(document)
    //     }
    // }
    
    // ── DOCX Extraction ─────────────────────────────────────────────────────────
    private fun extractDocx(file: File): String = extractFromZipXml(
        file = file,
        targetPath = "word/document.xml",
        textTag = "w:t"
    )
    
    // ── XLSX Extraction ─────────────────────────────────────────────────────────
    private fun extractXlsx(file: File): String {
        val sharedStrings = mutableListOf<String>()
        val sheetTexts = mutableListOf<String>()
        
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        // Extract shared strings
                        val factory = XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        parser.setInput(zip.reader())
                        
                        var eventType = parser.eventType
                        val textBuilder = StringBuilder()
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    textBuilder.append(parser.text)
                                }
                            } else if (eventType == XmlPullParser.END_TAG && parser.name == "si") {
                                sharedStrings.add(textBuilder.toString())
                                textBuilder.clear()
                            }
                            eventType = parser.next()
                        }
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                        // Extract sheet content
                        val factory = XmlPullParserFactory.newInstance()
                        val parser = factory.newPullParser()
                        parser.setInput(zip.reader())
                        
                        val sheetText = StringBuilder()
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "v") {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    val value = parser.text
                                    // Try to get from shared strings, otherwise use raw value
                                    val cellValue = value.toIntOrNull()?.let { idx ->
                                        sharedStrings.getOrNull(idx) ?: value
                                    } ?: value
                                    sheetText.append(cellValue).append("\t")
                                }
                            } else if (eventType == XmlPullParser.END_TAG && parser.name == "row") {
                                sheetText.append("\n")
                            }
                            eventType = parser.next()
                        }
                        sheetTexts.add(sheetText.toString())
                    }
                }
                entry = zip.nextEntry
            }
        }
        
        return sheetTexts.mapIndexed { idx, text -> 
            "=== Sheet ${idx + 1} ===\n$text"
        }.joinToString("\n\n")
    }
    
    // ── PPTX Extraction ─────────────────────────────────────────────────────────
    private fun extractPptx(file: File): String {
        // Read all slide entry bytes first (can't parse while ZipInputStream is open for other entries)
        val slideEntries = mutableListOf<Pair<String, ByteArray>>()
        
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Match slide files only, skip _rels and slideLayouts
                if (entry.name.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))) {
                    slideEntries.add(entry.name to zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        
        // Sort slides by number to ensure correct order
        slideEntries.sortBy { (name, _) ->
            name.replace("ppt/slides/slide", "").replace(".xml", "").toIntOrNull() ?: 0
        }
        
        val slides = slideEntries.mapIndexedNotNull { _, (_, bytes) ->
            val slideText = StringBuilder()
            
            // Parse XML bytes — use non-namespace-aware mode for simpler tag name matching
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name ?: ""
                    // PPTX text runs: a:t. Also handle <t> in case namespace stripped
                    if (tagName == "a:t" || tagName == "t") {
                        // Collect all TEXT tokens until end tag
                        val sb = StringBuilder()
                        eventType = parser.next()
                        while (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.ENTITY_REF) {
                            sb.append(parser.text ?: "")
                            eventType = parser.next()
                        }
                        val text = sb.toString()
                        if (text.isNotBlank()) {
                            slideText.append(text).append(" ")
                        }
                        continue // already advanced eventType
                    }
                    // End of paragraph → add newline
                    if (tagName == "a:p" || tagName == "p") {
                        if (slideText.isNotEmpty() && !slideText.endsWith("\n")) {
                            slideText.append("\n")
                        }
                    }
                }
                eventType = parser.next()
            }
            
            slideText.toString().trim().takeIf { it.isNotEmpty() }
        }
        
        return if (slides.isEmpty()) {
            "No text content found in presentation"
        } else {
            slides.mapIndexed { idx, text ->
                "=== Slide ${idx + 1} ===\n$text"
            }.joinToString("\n\n")
        }
    }
    
    // ── ODT Extraction ──────────────────────────────────────────────────────────
    private fun extractOdt(file: File): String = extractFromZipXml(
        file = file,
        targetPath = "content.xml",
        textTag = "text:p"
    )
    
    // ── ODS Extraction ──────────────────────────────────────────────────────────
    private fun extractOds(file: File): String = extractFromZipXml(
        file = file,
        targetPath = "content.xml",
        textTag = "text:p"
    )
    
    // ── ODP Extraction ──────────────────────────────────────────────────────────
    private fun extractOdp(file: File): String = extractFromZipXml(
        file = file,
        targetPath = "content.xml",
        textTag = "text:p"
    )
    
    // ── RTF Extraction ──────────────────────────────────────────────────────────
    private fun extractRtf(file: File): String {
        val content = file.readText()
        // Simple RTF stripping - remove control words and groups
        return content
            .replace(Regex("""\\[a-z]+(-?\d+)? ?"""), " ") // Control words
            .replace(Regex("""\{|\}"""), "") // Braces
            .replace(Regex("""\\'[0-9a-f]{2}"""), "") // Hex chars
            .replace(Regex("""\s+"""), " ") // Normalize whitespace
            .trim()
    }
    
    // ── Helper: Extract from ZIP+XML ────────────────────────────────────────────
    private fun extractFromZipXml(file: File, targetPath: String, textTag: String): String {
        val textBuilder = StringBuilder()
        
        ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == targetPath) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(zip.reader())
                    
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == textTag) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                textBuilder.append(parser.text).append("\n")
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        
        return textBuilder.toString().trim()
    }
}
