package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.RiskLevel
import com.amaya.intelligence.domain.security.ValidationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central executor for all AI tools.
 * 
 * This class:
 * 1. Routes tool calls to the appropriate handler
 * 2. Applies security validation
 * 3. Handles confirmation flows
 * 4. Provides tool definitions for AI prompts
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val listFilesTool: ListFilesTool,
    private val readFileTool: ReadFileTool,
    private val writeFileTool: WriteFileTool,
    private val createDirectoryTool: CreateDirectoryTool,
    private val deleteFileTool: DeleteFileTool,
    private val runShellTool: RunShellTool,
    private val transferFileTool: TransferFileTool,  // replaces copy_file + move_file
    private val editFileTool: EditFileTool,           // now includes apply_diff
    private val findFilesTool: FindFilesTool,         // now includes search_files
    private val undoChangeTool: UndoChangeTool,
    // Memory & reminder tools
    private val createReminderTool: CreateReminderTool,
    private val updateMemoryTool: UpdateMemoryTool,
    // Todo tool
    private val updateTodoTool: UpdateTodoTool,
    // Subagent tool
    private val invokeSubagentsTool: InvokeSubagentsTool,
    private val commandValidator: CommandValidator
) {
    
    private val tools: Map<String, Tool> by lazy {
        mapOf(
            listFilesTool.name      to listFilesTool,
            readFileTool.name       to readFileTool,
            writeFileTool.name      to writeFileTool,
            createDirectoryTool.name to createDirectoryTool,
            deleteFileTool.name     to deleteFileTool,
            runShellTool.name       to runShellTool,
            transferFileTool.name   to transferFileTool,
            editFileTool.name       to editFileTool,
            findFilesTool.name      to findFilesTool,
            undoChangeTool.name     to undoChangeTool,
            createReminderTool.name to createReminderTool,
            updateMemoryTool.name   to updateMemoryTool,
            updateTodoTool.name     to updateTodoTool,
            invokeSubagentsTool.name to invokeSubagentsTool
        )
    }
    
    /**
     * Execute a tool by name with the given arguments.
     * 
     * @param toolName Name of the tool to execute
     * @param arguments Map of argument name to value
     * @param workspacePath Optional workspace path to use as default working directory
     * @param onConfirmationRequired Callback when user confirmation is needed
     * @return Result of the tool execution
     */
    suspend fun execute(
        toolName: String,
        arguments: Map<String, Any?>,
        workspacePath: String? = null,
        toolCallId: String? = null,
        onEvent: (suspend (Any) -> Unit)? = null,
        onConfirmationRequired: suspend (ConfirmationRequest) -> Boolean = { false }
    ): ToolResult {
        val tool = tools[toolName]
            ?: return ToolResult.Error(
                "Unknown tool: $toolName. Available: ${tools.keys.joinToString()}",
                ErrorType.VALIDATION_ERROR
            )

        // Wire up InvokeSubagentsTool with event emitter for live progress
        if (tool is InvokeSubagentsTool && onEvent != null) {
            tool.eventEmitter = onEvent
            tool.currentToolCallId = toolCallId
        }
        
        // Auto-inject workspace as default working_dir for run_shell
        val finalArguments = if (toolName == "run_shell" && 
            workspacePath != null && 
            arguments["working_dir"] == null) {
            arguments + ("working_dir" to workspacePath)
        } else {
            arguments
        }
        
        // Pre-validate the tool call
        val validation = commandValidator.validateToolCall(toolName, finalArguments)
        
        when (validation) {
            is ValidationResult.Denied -> {
                return ToolResult.Error(
                    validation.reason,
                    ErrorType.SECURITY_VIOLATION
                )
            }
            
            is ValidationResult.RequiresConfirmation -> {
                val confirmed = onConfirmationRequired(
                    ConfirmationRequest(
                        toolName = toolName,
                        reason = validation.reason,
                        details = finalArguments.toString(),
                        riskLevel = validation.riskLevel
                    )
                )
                
                if (!confirmed) {
                    return ToolResult.Error(
                        "User declined: ${validation.reason}",
                        ErrorType.PERMISSION_ERROR
                    )
                }
            }
            
            is ValidationResult.Allowed -> { /* proceed */ }
        }
        
        // Execute the tool
        val result = tool.execute(finalArguments)
        
        // Handle nested confirmation requests from tools
        if (result is ToolResult.RequiresConfirmation) {
            val confirmed = onConfirmationRequired(
                ConfirmationRequest(
                    toolName = toolName,
                    reason = result.reason,
                    details = result.details,
                    riskLevel = RiskLevel.MEDIUM
                )
            )
            
            if (!confirmed) {
                return ToolResult.Error(
                    "User declined: ${result.reason}",
                    ErrorType.PERMISSION_ERROR
                )
            }
            
            // Retry execution after confirmation
            return tool.execute(finalArguments)
        }
        
        return result
    }
    
    /**
     * Get all available tools.
     */
    fun getTools(): List<Tool> = tools.values.toList()
    
    /**
     * Get tool definitions for AI prompts (JSON Schema format).
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "list_files",
                description = "List files and directories in the specified path using native APIs for high performance.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to directory", required = true),
                    ToolParameter("pattern", "string", "Regex pattern to filter results", required = false),
                    ToolParameter("max_depth", "integer", "Maximum depth to recurse (default: 1)", required = false),
                    ToolParameter("include_hidden", "boolean", "Include hidden files (default: false)", required = false)
                )
            ),
            ToolDefinition(
                name = "read_file",
                description = "Read one or multiple text files. Single: pass 'path'. Batch: pass 'paths' array (max 10). Info-only: pass 'info_only=true' for file metadata.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to single file", required = false),
                    ToolParameter("paths", "array", "Array of absolute paths for batch read (max 10)", required = false, items = "string"),
                    ToolParameter("start_line", "integer", "Start reading from this line (1-indexed)", required = false),
                    ToolParameter("end_line", "integer", "Stop reading at this line (inclusive)", required = false),
                    ToolParameter("info_only", "boolean", "Return only metadata (size, modified, permissions) instead of content", required = false),
                    ToolParameter("max_lines", "integer", "Max lines per file in batch mode (default: 100)", required = false)
                )
            ),
            ToolDefinition(
                name = "write_file",
                description = "Write content to a file with atomic operations and automatic backup. Always creates a backup before writing.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to the file", required = true),
                    ToolParameter("content", "string", "Content to write", required = true),
                    ToolParameter("create_backup", "boolean", "Create backup before write (default: true)", required = false),
                    ToolParameter("validate_syntax", "boolean", "Validate code syntax (default: true for code files)", required = false),
                    ToolParameter("create_dirs", "boolean", "Create parent directories if needed (default: true)", required = false),
                    ToolParameter("append", "boolean", "Append instead of overwrite (default: false)", required = false)
                )
            ),
            ToolDefinition(
                name = "create_directory",
                description = "Create a directory and any necessary parent directories.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path of directory to create", required = true)
                )
            ),
            ToolDefinition(
                name = "delete_file",
                description = "Safely delete a file or directory by moving it to trash. Can be recovered from .trash directory.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to file or directory", required = true),
                    ToolParameter("permanent", "boolean", "Permanently delete (dangerous, default: false)", required = false)
                )
            ),
            ToolDefinition(
                name = "run_shell",
                description = """Run a shell command. Supports:
                    - Git operations (git status, git diff, git commit)
                    - Complex text search (grep with regex)
                    - Build tools (gradle, make)
                    
                    Do NOT use for basic file operations - use native tools instead.""".trimIndent(),
                parameters = listOf(
                    ToolParameter("command", "string", "The shell command to run", required = true),
                    ToolParameter("working_dir", "string", "Working directory for the command", required = false),
                    ToolParameter("timeout_ms", "integer", "Timeout in milliseconds (default: 30000, max: 300000)", required = false)
                )
            ),
            ToolDefinition(
                name = "transfer_file",
                description = "Copy or move/rename a file or directory. mode='copy' duplicates, mode='move' relocates/renames.",
                parameters = listOf(
                    ToolParameter("source", "string", "Absolute path to source file/directory", required = true),
                    ToolParameter("destination", "string", "Absolute path to destination", required = true),
                    ToolParameter("mode", "string", "Operation: 'copy' or 'move' (default: copy)", required = false, enum = listOf("copy", "move")),
                    ToolParameter("overwrite", "boolean", "Overwrite if destination exists (default: false)", required = false)
                )
            ),
            ToolDefinition(
                name = "edit_file",
                description = "Edit a file by replacing text or applying a unified diff. Use 'old_content'+'new_content' for text replacement, or 'diff' for patch mode.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to the file", required = true),
                    ToolParameter("old_content", "string", "Exact text to find and replace", required = false),
                    ToolParameter("new_content", "string", "Text to replace with", required = false),
                    ToolParameter("diff", "string", "Unified diff content (@@ hunks) to apply as patch", required = false),
                    ToolParameter("all_occurrences", "boolean", "Replace all occurrences (default: false)", required = false),
                    ToolParameter("dry_run", "boolean", "Preview changes without saving (default: false)", required = false)
                )
            ),
            ToolDefinition(
                name = "find_files",
                description = "Find files by name pattern (glob) or search content within files. Use 'pattern' for filename glob, or 'content' for grep-style search.",
                parameters = listOf(
                    ToolParameter("path", "string", "Directory path to search in", required = true),
                    ToolParameter("pattern", "string", "Glob pattern to match filenames (e.g., *.kt)", required = false),
                    ToolParameter("content", "string", "Text to search for inside files (grep mode)", required = false),
                    ToolParameter("case_sensitive", "boolean", "Case-sensitive content search (default: false)", required = false),
                    ToolParameter("type", "string", "Filter: 'file', 'directory', or 'all' (default: all)", required = false),
                    ToolParameter("max_depth", "integer", "Maximum search depth (default: 10)", required = false),
                    ToolParameter("max_results", "integer", "Maximum results (default: 50)", required = false)
                )
            ),
            ToolDefinition(
                name = "undo_change",
                description = "Undo the last change to a file by restoring from backup.",
                parameters = listOf(
                    ToolParameter("path", "string", "Absolute path to the file to restore", required = true),
                    ToolParameter("list_backups", "boolean", "List available backups instead of restoring", required = false)
                )
            ),
            // ── Subagent tool ──────────────────────────────────────────────────────
            ToolDefinition(
                name = "invoke_subagents",
                description = "Spawn up to 4 independent AI subagents running IN PARALLEL. " +
                    "Each subagent has its own task and access to all file tools. " +
                    "Use for independent sub-tasks (reading multiple folders, auditing different layers). " +
                    "Subagents do NOT see conversation history — include ALL context in task. " +
                    "Returns combined summary from all subagents.",
                parameters = listOf(
                    ToolParameter(
                        name = "subagents",
                        type = "array",
                        description = "List of subagent tasks. Each: {task_name: string (≤5 words), task: string (full self-contained prompt)}",
                        required = true,
                        items = "object"
                    )
                )
            ),
            // ── Todo / Task list tool ──────────────────────────────────────────────
            ToolDefinition(
                name = "update_todo",
                description = "Update the live task list shown above the chat input. " +
                    "Call at START of multi-step task with merge=false to set full plan, then merge=true to update progress. " +
                    "Status: 'pending', 'in_progress', 'completed'.",
                parameters = listOf(
                    ToolParameter("merge", "boolean",
                        "true=merge by id into existing list, false=replace all items.", required = true),
                    ToolParameter("todos", "array",
                        "Todo items. Each: {id: int, status: string, content: string, active_form: string (optional)}",
                        required = true, items = "object")
                )
            ),
            // ── Memory & Reminder tools ────────────────────────────────────────────
            ToolDefinition(
                name = "create_reminder",
                description = "Schedule a reminder via Android notification at a specific time. " +
                    "Use when user asks to be reminded about something.",
                parameters = listOf(
                    ToolParameter("title", "string", "Short reminder title", required = true),
                    ToolParameter("message", "string", "Full reminder message for the notification", required = true),
                    ToolParameter("datetime", "string", "ISO format YYYY-MM-DDTHH:MM (e.g. 2026-02-27T17:00)", required = true),
                    ToolParameter("repeat", "string", "Recurrence: 'once' (default), 'daily', or 'weekly'", required = false,
                        enum = listOf("once", "daily", "weekly")),
                    ToolParameter("conversation_id", "integer",
                        "Current conversation ID so reply appears in this chat when reminder fires.", required = false)
                )
            ),
            ToolDefinition(
                name = "update_memory",
                description = "Persist important info for future sessions. Call when user shares name, preferences, goals, or asks to remember something.",
                parameters = listOf(
                    ToolParameter("content", "string", "What to remember — clear self-contained sentence", required = true),
                    ToolParameter("target", "string", "'daily' = today's log, 'long' = MEMORY.md permanent storage",
                        required = false, enum = listOf("daily", "long")),
                    ToolParameter("section", "string", "Section in MEMORY.md (default: 'Important Facts'). Only for target='long'",
                        required = false)
                )
            )
        )
    }
}

/**
 * Request for user confirmation.
 */
data class ConfirmationRequest(
    val toolName: String,
    val reason: String,
    val details: String,
    val riskLevel: RiskLevel
)

/**
 * Tool definition for AI prompts.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
)

/**
 * Parameter definition for a tool.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val default: Any? = null,
    val enum: List<String>? = null,
    val items: String? = null  // For array types: item type (e.g., "string")
)
