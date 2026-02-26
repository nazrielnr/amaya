package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Move (cut & paste) or rename a file or directory.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class MoveFileTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "move_file"
    
    override val description = """
        Move or rename a file or directory.
        Can be used to:
        - Move file to different directory
        - Rename file in same directory
        - Both move and rename at once
        
        Arguments:
        - source (string, required): Absolute path to source file/directory
        - destination (string, required): Absolute path to destination
        - overwrite (bool, optional): Overwrite if destination exists (default: false)
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = 
        withContext(Dispatchers.IO) {
            
        val sourceStr = arguments["source"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: source",
                ErrorType.VALIDATION_ERROR
            )
        
        val destStr = arguments["destination"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: destination",
                ErrorType.VALIDATION_ERROR
            )
        
        val overwrite = arguments["overwrite"] as? Boolean ?: false
        
        // Validate source path (read + write for move)
        when (val validation = commandValidator.validatePath(sourceStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Source: $sourceStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        // Validate destination path (write)
        when (val validation = commandValidator.validatePath(destStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Destination: $destStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val source = File(sourceStr)
        val destination = File(destStr)
        
        if (!source.exists()) {
            return@withContext ToolResult.Error(
                "Source does not exist: $sourceStr",
                ErrorType.NOT_FOUND
            )
        }
        
        if (destination.exists() && !overwrite) {
            return@withContext ToolResult.Error(
                "Destination already exists: $destStr. Use overwrite=true to replace.",
                ErrorType.VALIDATION_ERROR
            )
        }
        
        try {
            // Create parent directories if needed
            destination.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            
            // Delete destination if overwriting
            if (destination.exists() && overwrite) {
                if (destination.isDirectory) {
                    destination.deleteRecursively()
                } else {
                    destination.delete()
                }
            }
            
            // Try rename first (atomic if on same filesystem)
            val renamed = source.renameTo(destination)
            
            if (!renamed) {
                // Fallback: copy and delete
                if (source.isDirectory) {
                    source.copyRecursively(destination, overwrite = true)
                    source.deleteRecursively()
                } else {
                    source.copyTo(destination, overwrite = true)
                    source.delete()
                }
            }
            
            val action = if (source.parentFile?.absolutePath == destination.parentFile?.absolutePath) "renamed" else "moved"
            ToolResult.Success(
                output = "Successfully $action: $sourceStr -> $destStr",
                metadata = mapOf("source" to sourceStr, "destination" to destStr, "action" to action)
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to move: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
}
