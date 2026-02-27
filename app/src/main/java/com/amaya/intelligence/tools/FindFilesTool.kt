package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Find files by name pattern (glob) in a directory.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class FindFilesTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val MAX_RESULTS = 100
        const val MAX_DEPTH = 20
    }
    
    override val name = "find_files"
    
    override val description = """
        Find files by name pattern in a directory.
        
        Arguments:
        - path (string, required): Directory path to search in
        - pattern (string, required): Glob pattern (e.g., "*.kt", "Test*.java", "*.{js,ts}")
        - type (string, optional): Filter by type - "file", "directory", or "all" (default: "all")
        - max_depth (int, optional): Maximum search depth (default: 10)
        - max_results (int, optional): Maximum results (default: 50)
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            
        val pathStr = arguments["path"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )
        
        val pattern = arguments["pattern"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: pattern",
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
        
        val typeFilter = arguments["type"] as? String ?: "all"
        val maxDepth = (arguments["max_depth"] as? Number)?.toInt()?.coerceIn(1, MAX_DEPTH) ?: 10
        val maxResults = (arguments["max_results"] as? Number)?.toInt()?.coerceIn(1, MAX_RESULTS) ?: 50
        
        try {
            val regex = globToRegex(pattern)
            val results = mutableListOf<String>()
            var skippedDirs = 0
            
            // Use recursive walk with java.io.File for Android compatibility
            walkDirectory(directory, directory, maxDepth, 0, results, regex, typeFilter, maxResults) { skippedDirs++ }
            
            val output = if (results.isEmpty()) {
                buildString {
                    append("No files found matching pattern: $pattern")
                    if (skippedDirs > 0) {
                        append("\n(Skipped $skippedDirs directories due to access restrictions)")
                    }
                }
            } else {
                buildString {
                    append("Found ${results.size} file(s):\n\n")
                    results.forEach { append("$it\n") }
                    if (skippedDirs > 0) {
                        append("\n(Skipped $skippedDirs directories due to access restrictions)")
                    }
                }
            }
            
            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "pattern" to pattern,
                    "count" to results.size,
                    "skipped_dirs" to skippedDirs
                )
            )
            
        } catch (e: Exception) {
            ToolResult.Error(
                "Find failed: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun walkDirectory(
        baseDir: File,
        currentDir: File,
        maxDepth: Int,
        currentDepth: Int,
        results: MutableList<String>,
        pattern: Pattern,
        typeFilter: String,
        maxResults: Int,
        onSkipped: () -> Unit
    ) {
        if (currentDepth > maxDepth || results.size >= maxResults) return
        
        val files = try {
            currentDir.listFiles()
        } catch (e: SecurityException) {
            onSkipped()
            return
        } ?: run {
            onSkipped()
            return
        }
        
        for (file in files) {
            if (results.size >= maxResults) break
            
            val matchesType = when (typeFilter) {
                "file" -> file.isFile
                "directory" -> file.isDirectory
                else -> true
            }
            
            if (matchesType && pattern.matcher(file.name).matches()) {
                val relativePath = file.absolutePath.removePrefix(baseDir.absolutePath).removePrefix("/")
                results.add(relativePath)
            }
            
            // Recurse into subdirectories
            if (file.isDirectory && currentDepth < maxDepth) {
                walkDirectory(baseDir, file, maxDepth, currentDepth + 1, results, pattern, typeFilter, maxResults, onSkipped)
            }
        }
    }
    
    private fun globToRegex(glob: String): Pattern {
        val regex = buildString {
            append("^")
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    '{' -> append("(")
                    '}' -> append(")")
                    ',' -> append("|")
                    else -> append(c)
                }
                i++
            }
            append("$")
        }
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    }
}
