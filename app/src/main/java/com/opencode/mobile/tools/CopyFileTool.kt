package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copy file or directory to a new location.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class CopyFileTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "copy_file"
    
    override val description = """
        Copy a file or directory to a new location.
        
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
        
        // Validate source path (read)
        when (val validation = commandValidator.validatePath(sourceStr, isWrite = false)) {
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
            if (source.isDirectory) {
                source.copyRecursively(destination, overwrite = overwrite)
                ToolResult.Success(
                    output = "Successfully copied directory: $sourceStr -> $destStr",
                    metadata = mapOf("source" to sourceStr, "destination" to destStr)
                )
            } else {
                source.copyTo(destination, overwrite = overwrite)
                ToolResult.Success(
                    output = "Successfully copied file: $sourceStr -> $destStr",
                    metadata = mapOf("source" to sourceStr, "destination" to destStr)
                )
            }
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to copy: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
}
