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
) : Tool, ContextAwareTool {

    companion object {
        const val MAX_RESULTS = 100
        const val MAX_DEPTH = 20
        private val IGNORED_DIRECTORIES = setOf(
            ".git", ".gradle", "build", ".idea", "node_modules", ".aistudio", ".cxx", "bin", "obj", ".dart_tool"
        )
    }

    override val name = "find_files"

    override val description = "Find files by name pattern (glob) or search content within files. Use 'pattern' for filename glob (*.kt), or 'content' for grep-style content search."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult = withContext(Dispatchers.IO) {

        // ── Content search mode (replaces search_files) ──────────────────
        val contentQuery = (arguments["content"] ?: arguments["query"] ?: arguments["text"]) as? String
        if (contentQuery != null) {
            val searchPath = (arguments["path"] ?: arguments["dir"] ?: arguments["directory"]) as? String ?: return@withContext ToolResult.Error(
                "Missing required argument: path", ErrorType.VALIDATION_ERROR)
            // FIX #12: Validate path before content search to prevent scanning protected dirs
            when (val v = commandValidator.validatePath(searchPath, isWrite = false)) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(v.reason, ErrorType.SECURITY_VIOLATION)
                is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                    return@withContext ToolResult.RequiresConfirmation(v.reason, searchPath)
                }
                is ValidationResult.Allowed -> { /* proceed */ }
            }
            val caseSensitive = arguments["case_sensitive"] as? Boolean ?: false
            val maxResults = ((arguments["max_results"] as? Number)?.toInt() ?: 50).coerceIn(1, MAX_RESULTS)
            val maxDepth = ((arguments["max_depth"] as? Number)?.toInt() ?: 10).coerceIn(1, MAX_DEPTH)
            val dir = java.io.File(searchPath)
            if (!dir.exists()) return@withContext ToolResult.Error("Path not found: $searchPath", ErrorType.NOT_FOUND)
            if (!dir.isDirectory) return@withContext ToolResult.Error("Path is not a directory: $searchPath", ErrorType.VALIDATION_ERROR)

            val matches = mutableListOf<String>()
            dir.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { directory ->
                    directory.name !in IGNORED_DIRECTORIES && matches.size < maxResults && !java.nio.file.Files.isSymbolicLink(directory.toPath())
                }
                .filter { !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.isFile && it.length() < 5 * 1024 * 1024 }
                .takeWhile { matches.size < maxResults }
                .forEach { file ->
                    try {
                        val lines = file.readLines(Charsets.UTF_8)
                        lines.forEachIndexed { idx, line ->
                            val found = if (caseSensitive) line.contains(contentQuery)
                                        else line.contains(contentQuery, ignoreCase = true)
                            if (found && matches.size < maxResults) {
                                matches.add("${file.absolutePath}:${idx + 1}: ${line.trim()}")
                            }
                        }
                    } catch (_: Exception) {}
                }
            val output = if (matches.isEmpty()) "No matches found for: \"$contentQuery\""
                         else matches.joinToString("\n")
            return@withContext ToolResult.Success(output, metadata = mapOf(
                "query" to contentQuery, "matches" to matches.size, "path" to searchPath))
        }

        val pathStr = (arguments["path"] ?: arguments["dir"] ?: arguments["directory"]) as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )

        val pattern = (arguments["pattern"] ?: arguments["query"] ?: arguments["glob"]) as? String
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
            is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                return@withContext ToolResult.RequiresConfirmation(validation.reason, "Path: $pathStr")
            }
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
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
                onSkipped()
                continue
            }

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
            if (file.isDirectory && file.name !in IGNORED_DIRECTORIES && currentDepth < maxDepth) {
                walkDirectory(baseDir, file, maxDepth, currentDepth + 1, results, pattern, typeFilter, maxResults, onSkipped)
            }
        }
    }

    private fun globToRegex(glob: String): Pattern {
        // FIX 10: Properly escape all regex metacharacters before translating glob syntax.
        // Old code passed literal chars like '+', '(', '$' etc. directly into regex, causing
        // PatternSyntaxException or wrong matches (e.g. "file+.kt" glob matched "fileXXX.kt").
        val regex = buildString {
            append("^")
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*'  -> append(".*")
                    '?'  -> append(".")
                    '{'  -> append("(")
                    '}'  -> append(")")
                    ','  -> append("|")
                    // Escape all other regex metacharacters
                    else -> append(Pattern.quote(c.toString()))
                }
                i++
            }
            append("$")
        }
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
    }
}
