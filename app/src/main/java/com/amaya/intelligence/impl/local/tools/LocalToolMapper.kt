package com.amaya.intelligence.impl.local.tools

import com.amaya.intelligence.impl.common.mappers.ToolUiMapper
import com.amaya.intelligence.domain.models.ToolUiMetadata

/**
 * Local-specific tool name and argument mapper.
 * Maps local AI tool names to Amaya's standard format.
 */
object LocalToolMapper {

    private fun firstNonNull(map: Map<String, Any?>, vararg keys: String): Any? {
        keys.forEach { key ->
            if (map.containsKey(key)) {
                val value = map[key]
                if (value != null && value.toString().isNotBlank()) return value
            }
        }
        return null
    }

    /**
     * Maps local tool names to normalized names.
     * Local tools already use standard naming, so this is mostly pass-through.
     */
    fun mapToolName(rawName: String): String = when (rawName) {
        // File operations - normalize to standard names
        "read_file", "view_file", "read" -> "read_file"
        "write_file", "write", "create_file" -> "write_file"
        "edit_file", "replace_file_content", "multi_replace_file_content" -> "edit_file"
        "delete_file", "delete" -> "delete_file"
        "list_files", "list_dir", "ls" -> "list_files"
        
        // Shell/terminal
        "run_shell", "run_command", "shell", "execute" -> "run_shell"
        "command_status", "check_status" -> "check_status_terminal"
        "read_terminal", "read_output" -> "read_terminal"
        
        // Search
        "find_files", "find", "search_files" -> "find_files"
        "grep_search", "grep", "search" -> "grep_search"
        
        // Web
        "search_web", "web_search" -> "search_web"
        "read_url_content", "fetch_url" -> "read_url_content"
        "browser" -> "browser"
        
        // Task management
        "task_boundary", "task" -> "task_boundary"
        "notify_user", "notify" -> "notify_user"
        
        // Image generation
        "generate_image", "image" -> "generate_image"
        
        // MCP tools - pass through
        else -> if (rawName.startsWith("mcp_")) rawName else rawName
    }

    /**
     * Normalizes arguments based on the tool name.
     */
    fun mapToolArgs(toolName: String, args: Map<String, Any?>): Map<String, Any?> {
        val normalizedName = mapToolName(toolName)
        val mapped = args.toMutableMap()

        mapped["summary"] = firstNonNull(
            mapped,
            "summary",
            "Summary",
            "Description",
            "description",
            "Instruction",
            "instruction",
            "TaskSummary",
            "taskSummary"
        )
        
        when (normalizedName) {
            "run_shell" -> {
                mapped["command"] = firstNonNull(mapped, "command", "cmd", "CommandLine", "commandLine")
                mapped["cwd"] = firstNonNull(mapped, "cwd", "Cwd", "DirectoryPath", "directory")
            }
            "check_status_terminal" -> {
                mapped["commandId"] = firstNonNull(mapped, "commandId", "CommandId", "ProcessID", "processId", "PID")
                mapped["waitSeconds"] = firstNonNull(mapped, "waitSeconds", "WaitDurationSeconds", "WaitTime") ?: "0"
                mapped["maxChars"] = firstNonNull(mapped, "maxChars", "OutputCharacterCount", "MaxChars")
            }
            "read_file" -> {
                mapped["path"] = firstNonNull(mapped, "path", "file", "filePath", "AbsolutePath")
            }
            "write_file", "edit_file" -> {
                mapped["path"] = firstNonNull(mapped, "path", "file", "filePath", "TargetFile")
                mapped["targetContent"] = firstNonNull(mapped, "targetContent", "TargetContent")
                mapped["replacementContent"] = firstNonNull(
                    mapped,
                    "replacementContent",
                    "ReplacementContent",
                    "CodeContent",
                    "codeContent"
                )
                mapped["replacementChunks"] = firstNonNull(mapped, "replacementChunks", "ReplacementChunks")
            }
            "find_files", "grep_search" -> {
                mapped["query"] = firstNonNull(mapped, "query", "pattern", "search", "Query")
                mapped["path"] = firstNonNull(mapped, "path", "directory", "SearchPath", "SearchDirectory")
            }
            "list_files" -> {
                mapped["path"] = firstNonNull(mapped, "path", "directory", "DirectoryPath")
            }
            "task_boundary" -> {
                mapped["taskStatus"] = firstNonNull(mapped, "taskStatus", "TaskStatus", "status", "status_text")
            }
        }
        
        // Preserve original name
        mapped["original_name"] = args["original_name"] ?: toolName
        
        return mapped
    }

    /**
     * Gets UI metadata for rendering.
     */
    fun getUiMetadata(toolName: String, args: Map<String, Any?>, metadata: Map<String, String>? = null): ToolUiMetadata {
        val normalizedName = mapToolName(toolName)
        val normalizedArgs = mapToolArgs(toolName, args)
        return ToolUiMapper.getToolUiMetadata(normalizedName, normalizedArgs, metadata)
    }
}
