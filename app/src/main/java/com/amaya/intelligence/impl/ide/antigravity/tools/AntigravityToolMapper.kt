package com.amaya.intelligence.impl.ide.antigravity.tools

import com.amaya.intelligence.impl.common.mappers.ToolUiMapper
import com.amaya.intelligence.domain.models.ToolUiMetadata
import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol

/**
 * Antigravity-specific tool name and argument mapper.
 *
 * Maps Antigravity's internal step type names (CORTEX_STEP_TYPE_*) to
 * Amaya's standard tool names used by ToolCallCard and ToolResultPreview.
 */
object AntigravityToolMapper {

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
     * Maps the raw tool name from Antigravity to a normalized name.
     */
    fun mapToolName(rawName: String): String = when (rawName) {
        // File operations
        AntigravityProtocol.StepTypes.READ_FILE,
        "view_file", "view_file_outline", "view_code_item"
            -> "read_file"

        AntigravityProtocol.StepTypes.WRITE_FILE,
        "write_to_file"
            -> "write_file"

        AntigravityProtocol.StepTypes.EDIT_FILE,
        "replace_file_content", "multi_replace_file_content"
            -> "edit_file"

        // Shell / terminal
        AntigravityProtocol.StepTypes.RUN_COMMAND,
        "run_command"
            -> "run_shell"

        "command_status" -> "check_status_terminal"
        "send_command_input" -> "send_command_input"
        "read_terminal" -> "read_terminal"

        // Search / find
        AntigravityProtocol.StepTypes.SEARCH,
        "grep_search", "find_by_name"
            -> "find_files"

        "list_dir" -> "list_files"

        // Browser / web tools
        "browser_subagent",
        "read_url_content", "search_web"
            -> "browser"

        // Task management
        "task_boundary" -> "task_boundary"
        "notify_user"   -> "notify_user"

        // Image generation
        "generate_image" -> "generate_image"

        // MCP tools
        else -> {
            if (rawName.startsWith("mcp_")) rawName
            else rawName
        }
    }

    /**
     * Normalizes arguments based on the mapped tool name.
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
                mapped["command"] = firstNonNull(
                    mapped,
                    "command",
                    "CommandLine",
                    "commandLine",
                    "submittedCommandLine",
                    "proposedCommandLine",
                    "cmd"
                ) ?: mapped.values.firstOrNull()?.toString()
                mapped["cwd"] = firstNonNull(mapped, "cwd", "Cwd", "DirectoryPath", "searchPath")
            }
            "check_status_terminal" -> {
                mapped["commandId"] = firstNonNull(mapped, "commandId", "CommandId", "ProcessID", "processId", "PID")
                mapped["waitSeconds"] = firstNonNull(mapped, "waitSeconds", "WaitDurationSeconds", "WaitTime") ?: "0"
                mapped["maxChars"] = firstNonNull(mapped, "maxChars", "OutputCharacterCount", "MaxChars")
            }
            "send_command_input" -> {
                mapped["command"] = "Input " + (mapped["Input"] ?: mapped["CommandId"] ?: "")
            }
            "read_terminal" -> {
                mapped["command"] = "Read " + (mapped["ProcessID"] ?: "")
            }
            "read_file" -> {
                mapped["path"] = firstNonNull(mapped, "path", "AbsolutePath", "absolutePath", "File", "file", "filePath")
            }
            "write_file" -> {
                mapped["path"] = firstNonNull(mapped, "path", "TargetFile", "targetFile", "filePath")
                mapped["targetContent"] = firstNonNull(mapped, "targetContent", "TargetContent")
                mapped["replacementContent"] = firstNonNull(mapped, "replacementContent", "ReplacementContent", "CodeContent", "codeContent")
                mapped["replacementChunks"] = firstNonNull(mapped, "replacementChunks", "ReplacementChunks")
            }
            "edit_file" -> {
                mapped["path"] = firstNonNull(mapped, "path", "TargetFile", "targetFile", "filePath")
                mapped["targetContent"] = firstNonNull(mapped, "targetContent", "TargetContent")
                mapped["replacementContent"] = firstNonNull(mapped, "replacementContent", "ReplacementContent", "CodeContent", "codeContent")
                mapped["replacementChunks"] = firstNonNull(mapped, "replacementChunks", "ReplacementChunks")
            }
            "find_files" -> {
                mapped["query"] = firstNonNull(mapped, "query", "Query", "content")
                mapped["path"] = firstNonNull(mapped, "path", "SearchPath", "searchPath", "SearchDirectory", "searchDirectory")
                mapped["pattern"] = mapped["Pattern"] ?: mapped["pattern"]
            }
            "list_files" -> {
                mapped["path"] = firstNonNull(mapped, "path", "DirectoryPath", "directoryPath")
            }
            "browser" -> {
                mapped["task"] = mapped["Task"] ?: mapped["task"] ?: mapped["Url"] ?: mapped["url"] ?: mapped["query"] ?: mapped["task"]
            }
            "view_code_item" -> {
                mapped["path"] = mapped["File"] ?: mapped["file"] ?: mapped["filePath"] ?: mapped["path"]
            }
            "read_url_content" -> {
                mapped["task"] = mapped["Url"] ?: mapped["url"] ?: mapped["task"]
            }
            "search_web" -> {
                mapped["task"] = mapped["query"] ?: mapped["Query"] ?: mapped["task"]
            }
            "task_boundary" -> {
                mapped["title"] = mapped["TaskName"] ?: mapped["taskName"] ?: mapped["title"]
                mapped["summary"] = mapped["TaskSummary"] ?: mapped["taskSummary"] ?: mapped["summary"]
                mapped["taskStatus"] = mapped["TaskStatus"] ?: mapped["taskStatus"] ?: mapped["status_text"]
            }
            "notify_user" -> {
                mapped["content"] = mapped["Message"] ?: mapped["message"] ?: mapped["content"]
            }
            "generate_image" -> {
                mapped["prompt"] = mapped["Prompt"] ?: mapped["prompt"]
            }
        }
        
        // Map generic metadata that applies to all tools
        if (mapped["complexity"] == null) mapped["complexity"] = mapped["Complexity"] ?: mapped["complexity"]
        if (mapped["content"] == null) mapped["content"] = mapped["Description"] ?: mapped["description"] ?: mapped["Instruction"] ?: mapped["instruction"]
        
        // Ensure original_name is preserved
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

    /**
     * Extracts a human-readable result from raw data.
     */
    fun extractToolResult(rawData: Any?): String {
        return when (rawData) {
            is String -> rawData
            is Map<*, *> -> {
                (rawData["output"] ?: rawData["result"] ?: rawData.toString()).toString()
            }
            null -> ""
            else -> rawData.toString()
        }
    }
}
