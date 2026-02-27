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
 * Write content to a file with comprehensive safety features.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class WriteFileTool @Inject constructor(
    private val commandValidator: CommandValidator,
    @ApplicationContext private val context: Context
) : Tool {
    
    companion object {
        const val MAX_BACKUPS = 5
        
        val CODE_EXTENSIONS = setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "c", "cpp", "h", "hpp", "cs", "go", "rs",
            "swift", "dart", "rb", "php", "scala"
        )
        
        val STRUCTURED_EXTENSIONS = setOf(
            "json", "xml", "yaml", "yml", "toml", "html", "htm"
        )
    }
    
    override val name = "write_file"
    
    override val description = """
        Write content to a file with atomic operations and automatic backup.
        
        SAFETY: Always creates a backup before writing. If write fails,
        automatically restores from backup.
        
        Arguments:
        - path (string, required): Absolute path to the file
        - content (string, required): Content to write
        - create_backup (bool, optional): Create backup before write (default: true)
        - validate_syntax (bool, optional): Validate code syntax (default: true for code files)
        - create_dirs (bool, optional): Create parent directories if needed (default: true)
        - append (bool, optional): Append instead of overwrite (default: false)
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
        val validateSyntax = arguments["validate_syntax"] as? Boolean ?: true
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
            
            // 3. Validate syntax for code files
            if (validateSyntax && shouldValidateSyntax(file)) {
                val syntaxResult = validateCodeSyntax(content, file.extension)
                if (syntaxResult != null) {
                    return@withContext ToolResult.Error(
                        "Syntax validation failed: $syntaxResult",
                        ErrorType.VALIDATION_ERROR,
                        recoverable = true
                    )
                }
            }
            
            // 4. Atomic write process
            if (append && file.exists()) {
                val existingContent = file.readText()
                val newContent = existingContent + content
                atomicWrite(file, newContent)
            } else {
                atomicWrite(file, content)
            }
            
            // 5. Clean up old backups
            if (backupFile != null) {
                cleanupOldBackups(file)
            }
            
            val operation = if (append) "appended to" else "written to"
            ToolResult.Success(
                output = "Successfully $operation: $pathStr (${content.length} chars)" +
                        (if (backupFile != null) "\nBackup created: ${backupFile.absolutePath}" else ""),
                metadata = mapOf<String, Any>(
                    "path" to pathStr,
                    "size" to content.length,
                    "backup" to (backupFile?.absolutePath ?: "null")
                )
            )
            
        } catch (e: Exception) {
            // ROLLBACK: Restore from backup if we have one
            if (backupFile != null && backupFile.exists()) {
                try {
                    backupFile.copyTo(file, overwrite = true)
                    backupFile.delete()
                } catch (restoreError: Exception) {
                    return@withContext ToolResult.Error(
                        "Write failed AND restore failed: ${e.message}. " +
                        "Backup remains at: ${backupFile.absolutePath}",
                        ErrorType.EXECUTION_ERROR
                    )
                }
                
                return@withContext ToolResult.Error(
                    "Write failed, restored from backup: ${e.message}",
                    ErrorType.EXECUTION_ERROR,
                    recoverable = true
                )
            }
            
            ToolResult.Error(
                "Failed to write file: ${e.message}",
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
        val parent = targetFile.parentFile ?: File(".")
        val tempFile = File.createTempFile(".write_", ".tmp", parent)
        
        try {
            tempFile.writeText(content)
            
            // Rename to target (atomic on most filesystems)
            if (!tempFile.renameTo(targetFile)) {
                // Fallback: copy and delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
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
        val stack = ArrayDeque<Char>()
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        var inString = false
        var stringChar: Char? = null
        var prevChar: Char? = null
        var lineNumber = 1
        
        for ((index, char) in content.withIndex()) {
            if (char == '\n') lineNumber++
            
            if ((char == '"' || char == '\'') && prevChar != '\\') {
                if (!inString) {
                    inString = true
                    stringChar = char
                } else if (char == stringChar) {
                    inString = false
                    stringChar = null
                }
            }
            
            if (!inString) {
                when (char) {
                    '(', '[', '{' -> stack.addLast(char)
                    ')', ']', '}' -> {
                        val expected = pairs[char]
                        if (stack.isEmpty()) {
                            return "Unexpected '$char' at line $lineNumber (no matching open bracket)"
                        }
                        if (stack.last() != expected) {
                            return "Mismatched bracket '$char' at line $lineNumber (expected '${stack.last()}' to close first)"
                        }
                        stack.removeLast()
                    }
                }
            }
            
            prevChar = char
        }
        
        if (inString) {
            return "Unclosed string (started with $stringChar)"
        }
        
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
        val tagPattern = Regex("""<(/?)(\\w+)[^>]*(/?)\>""")
        
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
}
