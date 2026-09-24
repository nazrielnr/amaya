package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
) : Tool, ContextAwareTool {

    private val documentWriter = DocumentWriter(context)

    // FIX #19: State machine enum for bracket-matching parser — must be at class level (not inside function)
    private enum class ParseState {
        NORMAL, STRING_SINGLE, STRING_DOUBLE, MULTILINE_DOUBLE, MULTILINE_SINGLE, LINE_COMMENT, BLOCK_COMMENT
    }

    companion object {
        const val TAG = "WriteFileTool"
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
        Write content to a file using a best-effort temp-file replacement.
        Supports text files and document formats (DOCX, XLSX, PPTX, ODT, ODS, ODP).

        Arguments:
        - path (string, required): Host-resolved workspace path
        - content (string, required): Content to write (plain text for documents)
        - validate_syntax (bool, optional): Validate code syntax (default: false)
        - create_dirs (bool, optional): Create parent directories if needed (default: true)
        - append (bool, optional): Append instead of overwrite (default: false)

        For document formats: Creates new document with plain text content. Existing formatting is replaced.
    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult = withContext(Dispatchers.IO) {

        val pathStr = (arguments["path"] ?: arguments["file_path"] ?: arguments["filePath"] ?: arguments["file"]) as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )

        val content = (arguments["content"] ?: arguments["text"] ?: arguments["code"] ?: arguments["data"]) as? String
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
            is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                return@withContext ToolResult.RequiresConfirmation(
                    validation.reason,
                    "Path: $pathStr, Content length: ${content.length} chars"
                )
            }
            is ValidationResult.Allowed -> { /* proceed */ }
        }

        val file = File(pathStr)
        if (file.exists() && file.isDirectory) {
            return@withContext ToolResult.Error(
                "Cannot write to path: $pathStr is an existing directory.",
                ErrorType.VALIDATION_ERROR
            )
        }
        // Default false — AI-generated code is typically valid, validation causes false positives
        val validateSyntax = arguments["validate_syntax"] as? Boolean ?: false
        val createDirs = arguments["create_dirs"] as? Boolean ?: true
        val append = arguments["append"] as? Boolean ?: false

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

            // 2. Check if this is a document format
            val ext = file.extension.lowercase()
            if (ext in DOCUMENT_EXTENSIONS) {
                return@withContext documentWriter.write(file, content, ext, append)
            }

            // 3. Validate syntax for code files
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

            // 4. Temp-file replacement for text files
            if (append && file.exists()) {
                val existingContent = file.readText()
                val newContent = existingContent + content
                atomicWrite(file, newContent)
            } else {
                atomicWrite(file, content)
            }

            val operation = if (append) "appended to" else "written to"
            ToolResult.Success(
                output = "Successfully $operation: $pathStr (${content.length} chars)",
                metadata = mapOf<String, Any>(
                    "path" to pathStr,
                    "size" to content.length
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            ToolResult.Error(
                "Failed to write file (${e.javaClass.simpleName}): ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
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

    // ── Document Write ──────────────────────────────────────────────────────────

}
