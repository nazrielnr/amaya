package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
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
    
    override val description = """
        Read the content of a text file.
        Returns an error if the file is too large or appears to be binary.
        
        Arguments:
        - path (string, required): Absolute path to the file
        - max_size (int, optional): Maximum file size in bytes (default: 1MB, max: 10MB)
        - start_line (int, optional): Start reading from this line (1-indexed)
        - end_line (int, optional): Stop reading at this line (inclusive)
        - encoding (string, optional): Character encoding (default: UTF-8)
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = 
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )
        
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
}
