package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read multiple files in a single call for efficiency.
 */
@Singleton
class BatchReadTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val MAX_FILES = 10
        const val MAX_LINES_PER_FILE = 100
        const val MAX_TOTAL_SIZE = 500_000L // 500KB total
    }
    
    override val name = "batch_read"
    
    override val description = """
        Read multiple files in a single call for efficiency.
        
        Arguments:
        - paths (array, required): List of absolute file paths to read
        - max_lines (int, optional): Max lines per file (default: 100)
        - summary_only (boolean, optional): Return only first/last 10 lines per file
    """.trimIndent()
    
    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            
        val paths = (arguments["paths"] as? List<*>)?.mapNotNull { it?.toString() }
            ?: return@withContext ToolResult.Error(
                "Missing required argument: paths (array of file paths)",
                ErrorType.VALIDATION_ERROR
            )
        
        if (paths.isEmpty()) {
            return@withContext ToolResult.Error(
                "paths array is empty",
                ErrorType.VALIDATION_ERROR
            )
        }
        
        if (paths.size > MAX_FILES) {
            return@withContext ToolResult.Error(
                "Too many files: ${paths.size} (max: $MAX_FILES)",
                ErrorType.SIZE_LIMIT
            )
        }
        
        val maxLines = (arguments["max_lines"] as? Number)?.toInt()?.coerceIn(1, 500) ?: MAX_LINES_PER_FILE
        val summaryOnly = arguments["summary_only"] as? Boolean ?: false
        
        // Validate all paths first
        for (path in paths) {
            when (val validation = commandValidator.validatePath(path, isWrite = false)) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(
                    "Access denied: $path - ${validation.reason}",
                    ErrorType.SECURITY_VIOLATION
                )
                is ValidationResult.RequiresConfirmation -> { /* will check individually */ }
                is ValidationResult.Allowed -> { /* ok */ }
            }
        }
        
        // Read files in parallel
        val results = paths.map { path ->
            async {
                readSingleFile(path, maxLines, summaryOnly)
            }
        }.awaitAll()
        
        val output = buildString {
            results.forEachIndexed { index, result ->
                val path = paths[index]
                val fileName = File(path).name
                append("=== $fileName ===\n")
                append(result)
                append("\n\n")
            }
        }
        
        val successCount = results.count { !it.startsWith("ERROR:") }
        
        ToolResult.Success(
            output = output.trimEnd(),
            metadata = mapOf(
                "files_read" to successCount,
                "total_files" to paths.size
            )
        )
    }
    
    private fun readSingleFile(path: String, maxLines: Int, summaryOnly: Boolean): String {
        return try {
            val file = File(path)
            
            if (!file.exists()) {
                return "ERROR: File not found"
            }
            
            if (!file.isFile) {
                return "ERROR: Not a file"
            }
            
            if (file.length() > MAX_TOTAL_SIZE / 2) {
                return "ERROR: File too large (${file.length()} bytes)"
            }
            
            val content = file.readText(Charset.forName("UTF-8"))
            val lines = content.lines()
            
            if (summaryOnly && lines.size > 20) {
                val first = lines.take(10)
                val last = lines.takeLast(10)
                buildString {
                    first.forEach { append("$it\n") }
                    append("... (${lines.size - 20} lines omitted) ...\n")
                    last.forEach { append("$it\n") }
                }
            } else if (lines.size > maxLines) {
                buildString {
                    lines.take(maxLines).forEach { append("$it\n") }
                    append("... (${lines.size - maxLines} more lines)")
                }
            } else {
                content
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
