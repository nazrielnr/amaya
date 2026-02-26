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
 * Get detailed information about a file or directory.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class GetFileInfoTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "get_file_info"
    
    override val description = """
        Get detailed information about a file or directory.
        
        Returns: size, type, permissions, modified dates, and more.
        
        Arguments:
        - path (string, required): Absolute path to file or directory
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
                "Path does not exist: $pathStr",
                ErrorType.NOT_FOUND
            )
        }
        
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            
            val type = when {
                file.isDirectory -> "directory"
                file.isFile -> "file"
                else -> "other"
            }
            
            val output = buildString {
                append("Path: $pathStr\n")
                append("Type: $type\n")
                append("Size: ${formatSize(file.length())}\n")
                append("Modified: ${dateFormat.format(Date(file.lastModified()))}\n")
                
                if (file.isFile) {
                    val ext = file.extension.ifEmpty { "(none)" }
                    append("Extension: $ext\n")
                }
                
                // Check permissions
                val readable = file.canRead()
                val writable = file.canWrite()
                val executable = file.canExecute()
                append("Permissions: ${if (readable) "r" else "-"}${if (writable) "w" else "-"}${if (executable) "x" else "-"}\n")
                
                if (file.isDirectory) {
                    val children = try {
                        file.listFiles()?.size ?: 0
                    } catch (e: SecurityException) {
                        -1
                    }
                    if (children >= 0) {
                        append("Children: $children items\n")
                    } else {
                        append("Children: (access denied)\n")
                    }
                }
            }
            
            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "path" to pathStr,
                    "type" to type,
                    "size" to file.length(),
                    "modified" to file.lastModified()
                )
            )
            
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to get file info: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
