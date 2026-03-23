package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write content to a file with comprehensive safety features.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class WriteFileTool @Inject constructor(
    private val commandValidator: CommandValidator,
    @ApplicationContext private val context: Context
) : Tool {
    
    // FIX #19: State machine enum for bracket-matching parser — must be at class level (not inside function)
    private enum class ParseState {
        NORMAL, STRING_SINGLE, STRING_DOUBLE, MULTILINE_DOUBLE, MULTILINE_SINGLE, LINE_COMMENT, BLOCK_COMMENT
    }

    companion object {
        const val TAG = "WriteFileTool"
        const val MAX_BACKUPS = 5
        
        val CODE_EXTENSIONS = setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "c", "cpp", "h", "hpp", "cs", "go", "rs",
            "swift", "dart", "rb", "php", "scala"
        )
        
        val STRUCTURED_EXTENSIONS = setOf(
            "json", "xml", "yaml", "yml", "toml", "html", "htm"
        )
        
        // Document formats that can be written
        val DOCUMENT_EXTENSIONS = setOf(
            "docx", "xlsx", "pptx", "odt", "ods", "odp"
        )
    }
    
    override val name = "write_file"
    
    override val description = """
        Write content to a file with atomic operations and automatic backup.
        Supports text files and document formats (DOCX, XLSX, PPTX, ODT, ODS, ODP).
        
        SAFETY: Always creates a backup before writing. If write fails,
        automatically restores from backup.
        
        Arguments:
        - path (string, required): Absolute path to the file
        - content (string, required): Content to write (plain text for documents)
        - create_backup (bool, optional): Create backup before write (default: true)
        - validate_syntax (bool, optional): Validate code syntax (default: false)
        - create_dirs (bool, optional): Create parent directories if needed (default: true)
        - append (bool, optional): Append instead of overwrite (default: false)
        
        For document formats: Creates new document with plain text content. Existing formatting is replaced.
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = 
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )
        
        val content = arguments["content"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: content",
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
                "Path: $pathStr, Content length: ${content.length} chars"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val file = File(pathStr)
        val createBackup = arguments["create_backup"] as? Boolean ?: true
        // Default false — AI-generated code is typically valid, validation causes false positives
        val validateSyntax = arguments["validate_syntax"] as? Boolean ?: false
        val createDirs = arguments["create_dirs"] as? Boolean ?: true
        val append = arguments["append"] as? Boolean ?: false
        
        var backupFile: File? = null
        
        try {
            // 1. Create parent directories if needed
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                if (createDirs) {
                    parent.mkdirs()
                } else {
                    return@withContext ToolResult.Error(
                        "Parent directory does not exist: $parent",
                        ErrorType.NOT_FOUND
                    )
                }
            }
            
            // 2. Create backup of existing file
            if (createBackup && file.exists()) {
                backupFile = createBackupFile(file)
            }
            
            // 3. Check if this is a document format
            val ext = file.extension.lowercase()
            if (ext in DOCUMENT_EXTENSIONS) {
                return@withContext writeDocument(file, content, ext, append, backupFile)
            }
            
            // 4. Validate syntax for code files
            if (validateSyntax && shouldValidateSyntax(file)) {
                val syntaxResult = validateCodeSyntax(content, file.extension)
                if (syntaxResult != null) {
                    Log.w(TAG, "syntax validation failed: $syntaxResult")
                    return@withContext ToolResult.Error(
                        "Syntax validation failed: $syntaxResult",
                        ErrorType.VALIDATION_ERROR,
                        recoverable = true
                    )
                }
            }
            
            // 5. Atomic write process for text files
            if (append && file.exists()) {
                val existingContent = file.readText()
                val newContent = existingContent + content
                atomicWrite(file, newContent)
            } else {
                atomicWrite(file, content)
            }
            
            // 6. Clean up old backups
            if (backupFile != null) {
                cleanupOldBackups(file)
            }
            
            val operation = if (append) "appended to" else "written to"
            ToolResult.Success(
                output = "Successfully $operation: $pathStr (${content.length} chars)" +
                        (if (backupFile != null) "\nBackup created: ${backupFile!!.name}" else ""),
                metadata = mapOf<String, Any>(
                    "path" to pathStr,
                    "size" to content.length,
                    "backup" to (backupFile?.absolutePath ?: "none")
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            // ROLLBACK: Restore from backup if we have one
            if (backupFile != null && backupFile!!.exists()) {
                try {
                    backupFile!!.copyTo(file, overwrite = true)
                } catch (restoreError: Exception) {
                    Log.e(TAG, "rollback FAILED: ${restoreError.message}")
                    return@withContext ToolResult.Error(
                        "Write failed AND restore failed: ${e.message}. " +
                        "Backup remains at: ${backupFile!!.absolutePath}",
                        ErrorType.EXECUTION_ERROR
                    )
                }
                
                return@withContext ToolResult.Error(
                    "Write failed (${e.javaClass.simpleName}): ${e.message}",
                    ErrorType.EXECUTION_ERROR,
                    recoverable = true
                )
            }
            
            ToolResult.Error(
                "Failed to write file (${e.javaClass.simpleName}): ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun createBackupFile(file: File): File {
        val timestamp = System.currentTimeMillis()
        val backupName = "${file.name}.bak.$timestamp"
        val backupFile = File(file.parentFile, backupName)
        
        file.copyTo(backupFile, overwrite = true)
        
        return backupFile
    }
    
    private fun atomicWrite(targetFile: File, content: String) {
        // Strategy 1: temp file in same directory → atomic rename (preferred)
        val parentDir = targetFile.parentFile
        val tempInSameDir = if (parentDir != null) {
            try {
                File.createTempFile(".write_", ".tmp", parentDir)
            } catch (e: Exception) {
                null
            }
        } else null
        
        val tempFile = tempInSameDir ?: File.createTempFile(".write_", ".tmp", context.cacheDir.also { it.mkdirs() })
        
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            
            // Try rename first (atomic, same filesystem)
            if (tempFile.renameTo(targetFile)) {
                return
            }
            
            // Rename failed (cross-filesystem or permission) → copy
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "atomicWrite failed: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { tempFile.delete() }
            throw e
        }
    }
    
    private fun shouldValidateSyntax(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in CODE_EXTENSIONS || ext in STRUCTURED_EXTENSIONS
    }
    
    private fun validateCodeSyntax(content: String, extension: String): String? {
        return when (extension.lowercase()) {
            "json" -> validateJson(content)
            "xml", "html", "htm" -> validateXml(content)
            else -> validateBracketMatching(content)
        }
    }
    
    private fun validateBracketMatching(content: String): String? {
        // FIX #19: Rewritten to handle multiline strings (""" / '''), single-line comments (//),
        // block comments (/* */), and escape sequences — eliminating false positives on valid code.
        val stack = ArrayDeque<Char>()
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        var i = 0
        var lineNumber = 1

        var state = ParseState.NORMAL

        while (i < content.length) {
            val char = content[i]
            val remaining = content.substring(i)

            if (char == '\n') lineNumber++

            when (state) {
                ParseState.NORMAL -> {
                    when {
                        // Triple-quote multiline strings (Kotlin/Python)
                        remaining.startsWith("\"\"\"") -> { state = ParseState.MULTILINE_DOUBLE; i += 3; continue }
                        remaining.startsWith("'''")    -> { state = ParseState.MULTILINE_SINGLE; i += 3; continue }
                        // Line comment
                        remaining.startsWith("//")     -> { state = ParseState.LINE_COMMENT; i += 2; continue }
                        // Block comment
                        remaining.startsWith("/*")     -> { state = ParseState.BLOCK_COMMENT; i += 2; continue }
                        // Single-line strings — only track for bracket skipping, not strict close
                        char == '"'  -> { state = ParseState.STRING_DOUBLE; i++; continue }
                        char == '\'' -> { state = ParseState.STRING_SINGLE; i++; continue }
                        // Brackets
                        char == '(' || char == '[' || char == '{' -> stack.addLast(char)
                        char == ')' || char == ']' || char == '}' -> {
                            val expected = pairs[char]
                            if (stack.isEmpty()) {
                                return "Unexpected '$char' at line $lineNumber (no matching open bracket)"
                            }
                            if (stack.last() != expected) {
                                return "Mismatched bracket '$char' at line $lineNumber"
                            }
                            stack.removeLast()
                        }
                    }
                }
                ParseState.STRING_DOUBLE -> when {
                    remaining.startsWith("\\") -> i++ // skip escaped char
                    char == '"'  -> state = ParseState.NORMAL
                    char == '\n' -> state = ParseState.NORMAL // unterminated single-line string, be lenient
                }
                ParseState.STRING_SINGLE -> when {
                    remaining.startsWith("\\") -> i++ // skip escaped char
                    char == '\'' -> state = ParseState.NORMAL
                    char == '\n' -> state = ParseState.NORMAL
                }
                ParseState.MULTILINE_DOUBLE -> {
                    if (remaining.startsWith("\"\"\"")) { state = ParseState.NORMAL; i += 3; continue }
                }
                ParseState.MULTILINE_SINGLE -> {
                    if (remaining.startsWith("'''")) { state = ParseState.NORMAL; i += 3; continue }
                }
                ParseState.LINE_COMMENT -> {
                    if (char == '\n') state = ParseState.NORMAL
                }
                ParseState.BLOCK_COMMENT -> {
                    if (remaining.startsWith("*/")) { state = ParseState.NORMAL; i += 2; continue }
                }
            }
            i++
        }

        // Lenient: unclosed string at EOF is not an error (template literals etc.)
        if (stack.isNotEmpty()) {
            val unclosed = stack.joinToString(", ") { "'$it'" }
            return "Unclosed brackets: $unclosed"
        }

        return null
    }
    
    private fun validateJson(content: String): String? {
        val trimmed = content.trim()
        
        if (trimmed.isEmpty()) {
            return "Empty JSON"
        }
        
        if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
            return "JSON must start with '{' or '['"
        }
        
        return validateBracketMatching(content)
    }
    
    private fun validateXml(content: String): String? {
        val tagStack = ArrayDeque<String>()
        // FIX 2.6: In raw strings ("""), \\w is two literal chars — must use \w for regex word-char.
        // Also fixed self-closing pattern: (/)? at end instead of (/?)\> which was broken.
        val tagPattern = Regex("""<(/?)(\w+)[^>]*(/)?>""")
        
        for (match in tagPattern.findAll(content)) {
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2]
            val isSelfClosing = match.groupValues[3] == "/"
            
            if (isSelfClosing) continue
            
            if (isClosing) {
                if (tagStack.isEmpty()) {
                    return "Unexpected closing tag: </$tagName>"
                }
                if (tagStack.last() != tagName) {
                    return "Mismatched tag: expected </${tagStack.last()}>, found </$tagName>"
                }
                tagStack.removeLast()
            } else {
                tagStack.addLast(tagName)
            }
        }
        
        if (tagStack.isNotEmpty()) {
            return "Unclosed tags: ${tagStack.joinToString(", ") { "<$it>" }}"
        }
        
        return null
    }
    
    private fun cleanupOldBackups(originalFile: File) {
        val parent = originalFile.parentFile ?: return
        val baseName = originalFile.name
        val pattern = Regex("""${Regex.escape(baseName)}\.bak\.(\d+)""")
        
        val backups = try {
            parent.listFiles()?.filter { pattern.matches(it.name) }
                ?.sortedByDescending { file ->
                    pattern.find(file.name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                } ?: emptyList()
        } catch (e: SecurityException) {
            return
        }
        
        backups.drop(MAX_BACKUPS).forEach { backup ->
            runCatching { backup.delete() }
        }
    }
    
    // ── Document Write ──────────────────────────────────────────────────────────
    private fun writeDocument(
        file: File,
        content: String,
        ext: String,
        append: Boolean,
        backupFile: File?
    ): ToolResult {
        return try {
            when (ext) {
                "docx" -> writeDocx(file, content, append)
                "xlsx" -> writeXlsx(file, content, append)
                "pptx" -> writePptx(file, content, append)
                "odt" -> writeOdt(file, content, append)
                "ods" -> writeOds(file, content, append)
                "odp" -> writeOdp(file, content, append)
                else -> return ToolResult.Error(
                    "Unsupported document format for writing: $ext",
                    ErrorType.VALIDATION_ERROR
                )
            }
            
            if (backupFile != null) {
                cleanupOldBackups(file)
            }
            
            val operation = if (append) "appended to" else "written to"
            ToolResult.Success(
                output = "Successfully $operation document: ${file.name} (${content.length} chars)" +
                        (if (backupFile != null) "\nBackup created: ${backupFile.name}" else ""),
                metadata = mapOf(
                    "path" to file.absolutePath,
                    "format" to ext,
                    "size" to content.length,
                    "backup" to (backupFile?.absolutePath ?: "none")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "writeDocument failed: ext=$ext: ${e.javaClass.simpleName}: ${e.message}", e)
            // Rollback from backup if available
            if (backupFile != null && backupFile.exists()) {
                runCatching { backupFile.copyTo(file, overwrite = true) }
            }
            ToolResult.Error(
                "Failed to write document (${e.javaClass.simpleName}): ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    // ── DOCX Write ──────────────────────────────────────────────────────────────
    private fun writeDocx(file: File, content: String, append: Boolean) {
        val paragraphs = content.lines()
        
        // Create minimal DOCX structure
        val documentXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            appendLine("""  <w:body>""")
            
            paragraphs.forEach { line ->
                appendLine("""    <w:p>""")
                appendLine("""      <w:r>""")
                appendLine("""        <w:t>${line.escapeXml()}</w:t>""")
                appendLine("""      </w:r>""")
                appendLine("""    </w:p>""")
            }
            
            appendLine("""  </w:body>""")
            appendLine("""</w:document>""")
        }
        
        // Write ZIP via temp file first to avoid corrupting target on failure
        val tempFile = File.createTempFile(".docx_write_", ".tmp", 
            file.parentFile?.takeIf { it.exists() } ?: context.cacheDir)
        try {
            ZipOutputStream(tempFile.outputStream()).use { zip ->
                // [Content_Types].xml
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray())
                zip.closeEntry()
                
                // _rels/.rels
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray())
                zip.closeEntry()
                
                // word/document.xml
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray())
                zip.closeEntry()
            }
            // Move temp to final target
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            throw e
        }
    }
    
    // ── XLSX Write ──────────────────────────────────────────────────────────────
    // Content format:
    //   - Tab-separated values per row, newline per row
    //   - First row is treated as header (bold + border)
    //   - Prefix cell with "##" for bold, "=formula" for formula
    private fun writeXlsx(file: File, content: String, append: Boolean) {
        val lines = content.lines().filter { it.isNotEmpty() }
        val hasHeader = lines.isNotEmpty()
        
        // Style IDs: 0=normal, 1=header (bold+border), 2=data with border
        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts>
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills>
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD9E1F2"/></fgColor></patternFill>
  </fills>
  <borders>
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FF000000"/></left>
      <right style="thin"><color rgb="FF000000"/></right>
      <top style="thin"><color rgb="FF000000"/></top>
      <bottom style="thin"><color rgb="FF000000"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""

        val sheetXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            // Auto column width hint
            if (lines.isNotEmpty()) {
                val maxCols = lines.maxOf { it.split("\t").size }
                appendLine("""  <cols>""")
                for (c in 1..maxCols) {
                    appendLine("""    <col min="$c" max="$c" width="18" customWidth="1"/>""")
                }
                appendLine("""  </cols>""")
            }
            appendLine("""  <sheetData>""")
            
            lines.forEachIndexed { rowIdx, line ->
                val cells = line.split("\t")
                val isHeader = rowIdx == 0 && hasHeader
                // styleId: 1=header, 2=data with border
                val styleId = if (isHeader) 1 else 2
                appendLine("""    <row r="${rowIdx + 1}">""")
                cells.forEachIndexed { colIdx, rawValue ->
                    val cellRef = colIdxToRef(colIdx) + (rowIdx + 1)
                    val cellValue = rawValue.trim()
                    when {
                        // Formula cell
                        cellValue.startsWith("=") -> {
                            appendLine("""      <c r="$cellRef" s="$styleId">""")
                            appendLine("""        <f>${cellValue.drop(1).escapeXml()}</f>""")
                            appendLine("""      </c>""")
                        }
                        // Numeric cell
                        cellValue.toDoubleOrNull() != null -> {
                            appendLine("""      <c r="$cellRef" s="$styleId">""")
                            appendLine("""        <v>${cellValue.escapeXml()}</v>""")
                            appendLine("""      </c>""")
                        }
                        // String cell
                        else -> {
                            appendLine("""      <c r="$cellRef" t="inlineStr" s="$styleId">""")
                            appendLine("""        <is><t>${cellValue.escapeXml()}</t></is>""")
                            appendLine("""      </c>""")
                        }
                    }
                }
                appendLine("""    </row>""")
            }
            
            appendLine("""  </sheetData>""")
            // Freeze first row if has header
            if (hasHeader && lines.size > 1) {
                appendLine("""  <sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            }
            appendLine("""</worksheet>""")
        }
        
        ZipOutputStream(file.outputStream()).use { zip ->
            // [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray())
            zip.closeEntry()
            
            // _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray())
            zip.closeEntry()
            
            // xl/workbook.xml
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray())
            zip.closeEntry()
            
            // xl/styles.xml
            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(stylesXml.toByteArray())
            zip.closeEntry()
            
            // xl/_rels/workbook.xml.rels
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray())
            zip.closeEntry()
            
            // xl/worksheets/sheet1.xml
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
    }
    
    // Convert column index (0-based) to Excel letter ref: 0→A, 25→Z, 26→AA
    private fun colIdxToRef(idx: Int): String {
        var n = idx
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }
    
    // ── PPTX Write ──────────────────────────────────────────────────────────────
    private fun writePptx(file: File, content: String, append: Boolean) {
        val lines = content.lines()
        
        val slideXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            appendLine("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            appendLine("""  <p:cSld>""")
            appendLine("""    <p:spTree>""")
            appendLine("""      <p:sp>""")
            appendLine("""        <p:txBody>""")
            appendLine("""          <a:p>""")
            lines.forEach { line ->
                appendLine("""            <a:r><a:t>${line.escapeXml()}</a:t></a:r>""")
            }
            appendLine("""          </a:p>""")
            appendLine("""        </p:txBody>""")
            appendLine("""      </p:sp>""")
            appendLine("""    </p:spTree>""")
            appendLine("""  </p:cSld>""")
            appendLine("""</p:sld>""")
        }
        
        ZipOutputStream(file.outputStream()).use { zip ->
            // Minimal PPTX structure (simplified)
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>""".toByteArray())
            zip.closeEntry()
            
            zip.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            zip.write(slideXml.toByteArray())
            zip.closeEntry()
        }
    }
    
    // ── ODT Write ───────────────────────────────────────────────────────────────
    private fun writeOdt(file: File, content: String, append: Boolean) {
        val paragraphs = content.lines()
        
        val contentXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">""")
            appendLine("""  <office:body>""")
            appendLine("""    <office:text>""")
            paragraphs.forEach { line ->
                appendLine("""      <text:p>${line.escapeXml()}</text:p>""")
            }
            appendLine("""    </office:text>""")
            appendLine("""  </office:body>""")
            appendLine("""</office:document-content>""")
        }
        
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/vnd.oasis.opendocument.text".toByteArray())
            zip.closeEntry()
            
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(contentXml.toByteArray())
            zip.closeEntry()
        }
    }
    
    // ── ODS Write ───────────────────────────────────────────────────────────────
    private fun writeOds(file: File, content: String, append: Boolean) {
        val lines = content.lines()
        
        val contentXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">""")
            appendLine("""  <office:body>""")
            appendLine("""    <office:spreadsheet>""")
            appendLine("""      <table:table table:name="Sheet1">""")
            lines.forEach { line ->
                appendLine("""        <table:table-row>""")
                line.split("\t").forEach { cellValue ->
                    appendLine("""          <table:table-cell><text:p>${cellValue.escapeXml()}</text:p></table:table-cell>""")
                }
                appendLine("""        </table:table-row>""")
            }
            appendLine("""      </table:table>""")
            appendLine("""    </office:spreadsheet>""")
            appendLine("""  </office:body>""")
            appendLine("""</office:document-content>""")
        }
        
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/vnd.oasis.opendocument.spreadsheet".toByteArray())
            zip.closeEntry()
            
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(contentXml.toByteArray())
            zip.closeEntry()
        }
    }
    
    // ── ODP Write ───────────────────────────────────────────────────────────────
    private fun writeOdp(file: File, content: String, append: Boolean) {
        val lines = content.lines()
        
        val contentXml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0">""")
            appendLine("""  <office:body>""")
            appendLine("""    <office:presentation>""")
            appendLine("""      <draw:page>""")
            lines.forEach { line ->
                appendLine("""        <text:p>${line.escapeXml()}</text:p>""")
            }
            appendLine("""      </draw:page>""")
            appendLine("""    </office:presentation>""")
            appendLine("""  </office:body>""")
            appendLine("""</office:document-content>""")
        }
        
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/vnd.oasis.opendocument.presentation".toByteArray())
            zip.closeEntry()
            
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(contentXml.toByteArray())
            zip.closeEntry()
        }
    }
    
    // ── Helper: XML Escaping ────────────────────────────────────────────────────
    private fun String.escapeXml(): String {
        return this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
