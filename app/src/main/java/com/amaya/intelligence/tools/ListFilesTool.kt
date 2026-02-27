package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * List files in a directory using java.io.File API for Android compatibility.
 */
@Singleton
class ListFilesTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    override val name = "list_files"
    
    override val description = """
        List files and directories in the specified path.
        Uses native APIs for high performance.
        
        Arguments:
        - path (string, required): Absolute path to directory
        - pattern (string, optional): Regex pattern to filter results
        - max_depth (int, optional): Maximum depth to recurse (default: 1)
        - include_hidden (bool, optional): Include hidden files (default: false)
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
        
        val directory = File(pathStr)
        
        if (!directory.exists()) {
            return@withContext ToolResult.Error(
                "Path does not exist: $pathStr",
                ErrorType.NOT_FOUND
            )
        }
        
        if (!directory.isDirectory) {
            return@withContext ToolResult.Error(
                "Path is not a directory: $pathStr",
                ErrorType.VALIDATION_ERROR
            )
        }
        
        // Parse optional arguments
        val patternStr = arguments["pattern"] as? String
        val pattern = patternStr?.let { runCatching { Regex(it) }.getOrNull() }
        val maxDepth = (arguments["max_depth"] as? Number)?.toInt() ?: 1
        val includeHidden = arguments["include_hidden"] as? Boolean ?: false
        
        try {
            val files = mutableListOf<FileInfo>()
            var skippedDirs = 0
            
            walkDirectory(
                baseDir = directory,
                currentDir = directory,
                maxDepth = maxDepth,
                currentDepth = 0,
                includeHidden = includeHidden,
                pattern = pattern,
                files = files,
                onSkipped = { skippedDirs++ }
            )
            
            // Format output as readable text
            val output = buildString {
                appendLine("Directory: $pathStr")
                appendLine("Total: ${files.size} items")
                if (skippedDirs > 0) {
                    appendLine("(Skipped $skippedDirs directories due to access restrictions)")
                }
                appendLine()
                
                files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                    .forEach { file ->
                        val prefix = if (file.isDirectory) "📁" else "📄"
                        val size = if (file.isDirectory) "" else " (${formatSize(file.size)})"
                        appendLine("$prefix ${file.name}$size")
                    }
            }
            
            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "count" to files.size,
                    "skipped_dirs" to skippedDirs,
                    "files" to files
                )
            )
            
        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: ${e.message}",
                ErrorType.SECURITY_VIOLATION
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to list directory: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun walkDirectory(
        baseDir: File,
        currentDir: File,
        maxDepth: Int,
        currentDepth: Int,
        includeHidden: Boolean,
        pattern: Regex?,
        files: MutableList<FileInfo>,
        onSkipped: () -> Unit
    ) {
        if (currentDepth > maxDepth) return
        
        val children = try {
            currentDir.listFiles()
        } catch (e: SecurityException) {
            onSkipped()
            return
        } ?: run {
            onSkipped()
            return
        }
        
        for (file in children) {
            // Skip hidden files if not requested
            if (!includeHidden && file.name.startsWith(".")) continue
            
            // Apply pattern filter
            if (pattern != null && !pattern.matches(file.name)) continue
            
            files.add(FileInfo(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                extension = if (file.isFile) file.extension.takeIf { it.isNotEmpty() } else null
            ))
            
            // Recurse into subdirectories
            if (file.isDirectory && currentDepth < maxDepth) {
                walkDirectory(baseDir, file, maxDepth, currentDepth + 1, includeHidden, pattern, files, onSkipped)
            }
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
    
    private data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val extension: String?
    )
}
