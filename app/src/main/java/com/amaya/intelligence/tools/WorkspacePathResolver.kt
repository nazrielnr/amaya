package com.amaya.intelligence.tools

import java.io.File

/** Resolves model file paths against a host-owned workspace before validation. */
internal object WorkspacePathResolver {
    private val pathArguments = mapOf(
        "list_files" to setOf("path"),
        "read_file" to setOf("path", "paths"),
        "write_file" to setOf("path"),
        "create_directory" to setOf("path"),
        "delete_file" to setOf("path"),
        "edit_file" to setOf("path"),
        "find_files" to setOf("path")
    )

    fun resolve(toolName: String, arguments: Map<String, Any?>, workspacePath: String?): Result<Map<String, Any?>> = runCatching {
        val keys = pathArguments[toolName] ?: return@runCatching arguments
        val root = workspacePath?.takeIf { it.isNotBlank() }?.let(::File)
        val rootCanonical = root?.canonicalFile
        val resolved = arguments.toMutableMap()

        fun resolvePath(raw: String): String {
            require(raw.isNotBlank()) { "Path is required" }
            require(rootCanonical != null) { "No workspace is selected. Select a workspace before using '$toolName'." }
            require(raw.replace('\\', '/').split('/').none { it == ".." }) { "Path traversal is not allowed: $raw" }
            val target = if (File(raw).isAbsolute) File(raw) else File(rootCanonical, raw)
            val canonical = target.canonicalFile
            require(isWithin(canonical, rootCanonical)) { "Path is outside the active workspace: $raw" }
            return canonical.path
        }

        if ("path" in keys) {
            val rawPath = (resolved["path"] ?: resolved["file_path"] ?: resolved["filePath"] ?: resolved["file"] ?: resolved["target_file"] ?: resolved["TargetFile"]) as? String
            if (!rawPath.isNullOrBlank()) {
                val resolvedPath = resolvePath(rawPath)
                resolved["path"] = resolvedPath
                if (resolved.containsKey("file_path")) resolved["file_path"] = resolvedPath
                if (resolved.containsKey("filePath")) resolved["filePath"] = resolvedPath
                if (resolved.containsKey("file")) resolved["file"] = resolvedPath
            }
            else if (toolName in setOf("list_files", "find_files") && rootCanonical != null) resolved["path"] = rootCanonical.path
        }
        if ("paths" in keys && resolved["paths"] is List<*>) {
            val paths = resolved["paths"] as List<*>
            require(paths.all { it is String && it.isNotBlank() }) { "Every paths item must be a non-blank string" }
            resolved["paths"] = paths.map { resolvePath(it as String) }
        }
        resolved
    }

    private fun isWithin(candidate: File, root: File): Boolean =
        candidate.path == root.path || candidate.path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)
}
