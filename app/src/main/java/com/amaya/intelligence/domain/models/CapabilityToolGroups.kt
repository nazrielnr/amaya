package com.amaya.intelligence.domain.models

/**
 * Single source of truth for "capability category → model-visible tool names".
 *
 * Both the agent config UI (detail sheet per category) and
 * [AgentCapabilityProfile.allows] must read from this object so the advertised
 * tool schema never disagrees with what the UI claims the agent can do.
 */
object CapabilityToolGroups {

    /** Model-facing tool names in model-call order. */
    val workspace: List<String> = listOf(
        "workspace_search", "list_files", "read_file", "find_files",
        "workspace_change", "write_file", "edit_file", "create_directory", "delete_file"
    )

    val terminal: List<String> = listOf("run_shell")

    val browser: List<String> = listOf("browser")

    val subagents: List<String> = listOf("delegate_agent", "invoke_subagents")

    val webSearch: List<String> = listOf("web_search")

    val skills: List<String> = listOf("skill", "skill_view", "skill_manage")

    val reminders: List<String> = listOf("reminder", "create_reminder")

    val todo: List<String> = listOf("update_todo")

    /** Pseudo-entry shown in the config sheet for the whole MCP category. */
    val mcp: List<String> = listOf("mcp")

    /** Real model-facing MCP tools use McpClientManager.TOOL_PREFIX ("mcp__"). */
    const val MCP_TOOL_PREFIX = "mcp__"

    val allGroups: List<Pair<String, List<String>>> = listOf(
        "workspace" to workspace,
        "terminal" to terminal,
        "browser" to browser,
        "subagents" to subagents,
        "web_search" to webSearch,
        "skills" to skills,
        "reminders" to reminders,
        "todo" to todo,
        "mcp" to mcp
    )

    fun groupFor(toolName: String): String? {
        if (toolName.startsWith(MCP_TOOL_PREFIX)) return "mcp"
        return allGroups.firstOrNull { (_, tools) -> toolName in tools }?.first
    }

    /** Short UI description shown next to each tool checkbox in the config sheet. */
    fun descriptionFor(toolName: String): String = when (toolName) {
        "workspace_search" -> "List directories, find files by name, grep file content"
        "list_files" -> "List files and directories in the workspace"
        "read_file" -> "Read files and document formats"
        "find_files" -> "Find files by name pattern or content search"
        "workspace_change" -> "Write, append, replace, patch, mkdir, delete"
        "write_file" -> "Write or append content to a file"
        "edit_file" -> "Replace text or apply patches"
        "create_directory" -> "Create directories"
        "delete_file" -> "Delete files or directories"
        "run_shell" -> "Execute shell commands inside the workspace"
        "browser" -> "Control the local browser (open pages, click, type, extract)"
        "delegate_agent" -> "Dispatch one task to a named group member"
        "invoke_subagents" -> "Spawn temporary parallel read-only research workers"
        "web_search" -> "Search the web and read pages as text"
        "skill" -> "View or manage reusable skills"
        "skill_view" -> "Load a saved skill's full procedure"
        "skill_manage" -> "Create, update, archive, or delete skills"
        "reminder" -> "Schedule an Android reminder"
        "create_reminder" -> "Schedule an Android reminder"
        "update_todo" -> "Maintain the live task list above the chat input"
        "mcp" -> "External MCP servers configured in Settings"
        else -> toolName
    }
}
