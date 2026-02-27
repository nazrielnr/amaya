package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified file transfer tool — replaces separate copy_file and move_file tools.
 * Mode "copy": copy source to destination (source kept).
 * Mode "move": move/rename source to destination (source removed).
 */
@Singleton
class TransferFileTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {

    override val name = "transfer_file"

    override val description = "Copy or move/rename a file or directory. Use mode='copy' to duplicate, mode='move' to rename or relocate."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {

        val sourceStr = arguments["source"] as? String
            ?: return@withContext ToolResult.Error("Missing required argument: source", ErrorType.VALIDATION_ERROR)

        val destStr = arguments["destination"] as? String
            ?: return@withContext ToolResult.Error("Missing required argument: destination", ErrorType.VALIDATION_ERROR)

        val mode = (arguments["mode"] as? String)?.lowercase() ?: "copy"
        val overwrite = arguments["overwrite"] as? Boolean ?: false

        // Validate source (read)
        when (val v = commandValidator.validatePath(sourceStr, isWrite = false)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(v.reason, "Source: $sourceStr")
            is ValidationResult.Allowed -> {}
        }

        // Validate destination (write)
        when (val v = commandValidator.validatePath(destStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(v.reason, "Destination: $destStr")
            is ValidationResult.Allowed -> {}
        }

        val source = File(sourceStr)
        val dest   = File(destStr)

        if (!source.exists()) return@withContext ToolResult.Error("Source not found: $sourceStr", ErrorType.NOT_FOUND)
        if (dest.exists() && !overwrite) return@withContext ToolResult.Error(
            "Destination already exists: $destStr. Use overwrite=true to replace.", ErrorType.VALIDATION_ERROR)

        try {
            // Ensure parent dir exists
            dest.parentFile?.mkdirs()

            when (mode) {
                "move" -> {
                    // Try atomic rename first
                    val renamed = source.renameTo(dest)
                    if (!renamed) {
                        // Fallback: copy then delete
                        if (source.isDirectory) source.copyRecursively(dest, overwrite)
                        else source.copyTo(dest, overwrite)
                        if (source.isDirectory) source.deleteRecursively() else source.delete()
                    }
                    ToolResult.Success(
                        output = "Moved: ${source.name} → ${dest.absolutePath}",
                        metadata = mapOf("source" to sourceStr, "destination" to destStr, "mode" to "move")
                    )
                }
                else -> { // copy
                    if (source.isDirectory) source.copyRecursively(dest, overwrite)
                    else source.copyTo(dest, overwrite)
                    ToolResult.Success(
                        output = "Copied: ${source.name} → ${dest.absolutePath}",
                        metadata = mapOf("source" to sourceStr, "destination" to destStr, "mode" to "copy")
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult.Error("Transfer failed: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}
