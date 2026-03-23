package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Undo the last change to a file by restoring from backup.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class UndoChangeTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "undo_change"
    
    override val description = """
        Undo the last change to a file by restoring from backup.
        
        Backups are created automatically by write_file and edit_file tools.
        
        Arguments:
        - path (string, required): Absolute path to the file to restore
        - list_backups (boolean, optional): List available backups instead of restoring
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )
        
        val listBackups = arguments["list_backups"] as? Boolean ?: false
        
        // Validate path access
        when (val validation = commandValidator.validatePath(pathStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                validation.reason,
                "Restore: $pathStr"
            )
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        val targetFile = File(pathStr)
        val backupDir = File(targetFile.parent, ".backup")
        
        if (!backupDir.exists()) {
            return@withContext ToolResult.Error(
                "No backups found for this file",
                ErrorType.NOT_FOUND
            )
        }
        
        // Find backups for this file
        val fileName = targetFile.name
        val backups = try {
            backupDir.listFiles { file ->
                file.name.startsWith(fileName) && file.name.contains(".bak")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: SecurityException) {
            return@withContext ToolResult.Error(
                "Cannot access backup directory: ${e.message}",
                ErrorType.PERMISSION_ERROR
            )
        }
        
        if (backups.isEmpty()) {
            return@withContext ToolResult.Error(
                "No backups found for: $fileName",
                ErrorType.NOT_FOUND
            )
        }
        
        if (listBackups) {
            val output = buildString {
                append("Available backups for $fileName:\n\n")
                backups.forEachIndexed { index, backup ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(Date(backup.lastModified()))
                    append("${index + 1}. ${backup.name} ($date)\n")
                }
            }
            return@withContext ToolResult.Success(output)
        }
        
        // Restore from most recent backup
        val latestBackup = backups.first()
        
        try {
            latestBackup.copyTo(targetFile, overwrite = true)
            
            ToolResult.Success(
                output = "Restored $fileName from backup: ${latestBackup.name}",
                metadata = mapOf(
                    "restored_from" to latestBackup.absolutePath,
                    "backup_date" to latestBackup.lastModified()
                )
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to restore: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
}
