package com.opencode.mobile.tools

import com.opencode.mobile.domain.security.CommandValidator
import com.opencode.mobile.domain.security.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search for text content within files - like grep.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class SearchFilesTool @Inject constructor(
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        const val MAX_FILES = 5000
        const val MAX_RESULTS = 100
        const val MAX_FILE_SIZE = 1024 * 1024L // 1MB
        
        val SKIP_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp",
            "mp3", "mp4", "avi", "mov", "mkv", "wav",
            "zip", "tar", "gz", "rar", "7z",
            "pdf", "doc", "docx", "xls", "xlsx",
            "exe", "dll", "so", "dylib", "class", "jar",
            "apk", "aab", "dex"
        )
    }
    
    override val name = "search_files"
    
    override val description = """
        Search for text or patterns within files in a directory.
        Returns matching lines with file paths and line numbers.
        
        Arguments:
        - query (string, required): Text or regex pattern to search for
        - path (string, required): Directory path to search in
        - regex (boolean, optional): Treat query as regex (default: false)
        - case_insensitive (boolean, optional): Ignore case (default: true)
        - include (string, optional): File pattern to include (e.g., "*.kt")
        - exclude (string, optional): File pattern to exclude (e.g., "*.test.kt")
        - max_results (int, optional): Maximum results to return (default: 50)
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            
        val query = arguments["query"] as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: query",
                ErrorType.VALIDATION_ERROR
            )
        
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
        
        val useRegex = arguments["regex"] as? Boolean ?: false
        val caseInsensitive = arguments["case_insensitive"] as? Boolean ?: true
        val includePattern = arguments["include"] as? String
        val excludePattern = arguments["exclude"] as? String
        val maxResults = (arguments["max_results"] as? Number)?.toInt()?.coerceIn(1, MAX_RESULTS) ?: 50
        
        try {
            val pattern = if (useRegex) {
                val flags = if (caseInsensitive) Pattern.CASE_INSENSITIVE else 0
                Pattern.compile(query, flags)
            } else {
                val escapedQuery = Pattern.quote(query)
                val flags = if (caseInsensitive) Pattern.CASE_INSENSITIVE else 0
                Pattern.compile(escapedQuery, flags)
            }
            
            val includeGlob = includePattern?.let { globToRegex(it) }
            val excludeGlob = excludePattern?.let { globToRegex(it) }
            
            val results = mutableListOf<SearchResult>()
            var filesSearched = 0
            var filesSkipped = 0
            var dirsSkipped = 0
            
            // Use recursive walk with java.io.File for Android compatibility
            searchDirectory(
                baseDir = directory,
                currentDir = directory,
                pattern = pattern,
                includeGlob = includeGlob,
                excludeGlob = excludeGlob,
                maxResults = maxResults,
                results = results,
                filesSearched = { filesSearched++ },
                filesSkipped = { filesSkipped++ },
                dirsSkipped = { dirsSkipped++ }
            )
            
            val output = buildString {
                if (results.isEmpty()) {
                    append("No matches found for: $query")
                } else {
                    append("Found ${results.size} matches:\n\n")
                    results.forEach { result ->
                        append("${result.file}:${result.line}: ${result.content}\n")
                    }
                }
                if (dirsSkipped > 0) {
                    append("\n(Skipped $dirsSkipped directories due to access restrictions)")
                }
            }
            
            ToolResult.Success(
                output = output,
                metadata = mapOf(
                    "matches" to results.size,
                    "files_searched" to filesSearched,
                    "files_skipped" to filesSkipped,
                    "dirs_skipped" to dirsSkipped,
                    "query" to query
                )
            )
            
        } catch (e: Exception) {
            ToolResult.Error(
                "Search failed: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }
    
    private fun searchDirectory(
        baseDir: File,
        currentDir: File,
        pattern: Pattern,
        includeGlob: Pattern?,
        excludeGlob: Pattern?,
        maxResults: Int,
        results: MutableList<SearchResult>,
        filesSearched: () -> Unit,
        filesSkipped: () -> Unit,
        dirsSkipped: () -> Unit,
        depth: Int = 0
    ) {
        if (results.size >= maxResults || depth > 20) return
        
        val files = try {
            currentDir.listFiles()
        } catch (e: SecurityException) {
            dirsSkipped()
            return
        } ?: run {
            dirsSkipped()
            return
        }
        
        for (file in files) {
            if (results.size >= maxResults) break
            
            if (file.isDirectory) {
                searchDirectory(baseDir, file, pattern, includeGlob, excludeGlob, maxResults, results, filesSearched, filesSkipped, dirsSkipped, depth + 1)
                continue
            }
            
            if (!file.isFile) continue
            
            // Skip binary files
            val ext = file.extension.lowercase()
            if (SKIP_EXTENSIONS.contains(ext)) continue
            
            // Apply include/exclude filters
            val name = file.name
            val includeMatch = includeGlob?.let { it.matcher(name).matches() } ?: true
            val excludeMatch = excludeGlob?.let { it.matcher(name).matches() } ?: false
            if (!includeMatch || excludeMatch) continue
            
            // Skip large files
            if (file.length() > MAX_FILE_SIZE) {
                filesSkipped()
                continue
            }
            
            try {
                filesSearched()
                val content = file.readText(Charset.forName("UTF-8"))
                val relativePath = file.absolutePath.removePrefix(baseDir.absolutePath).removePrefix("/")
                
                content.lines().forEachIndexed { lineIndex, line ->
                    if (results.size >= maxResults) return@forEachIndexed
                    
                    if (pattern.matcher(line).find()) {
                        results.add(SearchResult(
                            file = relativePath,
                            line = lineIndex + 1,
                            content = line.take(200)
                        ))
                    }
                }
            } catch (e: Exception) {
                filesSkipped()
            }
        }
    }
    
    private fun globToRegex(glob: String): Pattern {
        val regex = buildString {
            append("^")
            glob.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    else -> append(char)
                }
            }
            append("$")
        }
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    }
    
    private data class SearchResult(
        val file: String,
        val line: Int,
        val content: String
    )
}
