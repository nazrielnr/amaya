package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Create directories using java.io.File API for Android compatibility.
 */
@Singleton
class CreateDirectoryTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "create_directory"
    
    override val description = """
        Create a directory (and any necessary parent directories).
        
        Arguments:
        - path (string, required): Absolute path of directory to create
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = 
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
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
                "Path: $pathStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val file = File(pathStr)
        
        try {
            if (file.exists()) {
                if (file.isDirectory) {
                    return@withContext ToolResult.Success(
                        output = "Directory already exists: $pathStr",
                        metadata = mapOf("path" to pathStr, "created" to false)
                    )
                } else {
                    return@withContext ToolResult.Error(
                        "Path exists but is not a directory: $pathStr",
                        ErrorType.VALIDATION_ERROR
                    )
                }
            }
            
            // Create directory and all parent directories
            val created = file.mkdirs()
            
            if (created) {
                ToolResult.Success(
                    output = "Created directory: $pathStr",
                    metadata = mapOf("path" to pathStr, "created" to true)
                )
            } else {
                ToolResult.Error(
                    "Failed to create directory: $pathStr",
                    ErrorType.EXECUTION_ERROR
                )
            }
            
        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: ${e.message}",
                ErrorType.PERMISSION_ERROR
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to create directory: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
}
