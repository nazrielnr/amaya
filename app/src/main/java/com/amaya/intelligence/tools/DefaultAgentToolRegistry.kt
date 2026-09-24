package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.models.AgentCapabilityProfile
import com.amaya.intelligence.domain.models.AssistantMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAgentToolRegistry @Inject constructor(
    private val listFilesTool: ListFilesTool,
    private val readFileTool: ReadFileTool,
    private val writeFileTool: WriteFileTool,
    private val createDirectoryTool: CreateDirectoryTool,
    private val deleteFileTool: DeleteFileTool,
    private val runShellTool: RunShellTool,
    private val editFileTool: EditFileTool,
    private val findFilesTool: FindFilesTool,
    private val createReminderTool: CreateReminderTool,
    private val updateMemoryTool: UpdateMemoryTool,
    private val memoryManageTool: MemoryManageTool,
    private val agentMemoryTool: AgentMemoryTool,
    private val skillViewTool: SkillViewTool,
    private val skillManageTool: SkillManageTool,
    private val recommendationManageTool: RecommendationManageTool,
    private val sessionSearchTool: SessionSearchTool,
    private val updateTodoTool: UpdateTodoTool,
    private val invokeSubagentsTool: InvokeSubagentsTool,
    private val delegateAgentTool: DelegateAgentTool,
    private val webSearchTool: WebSearchTool,
    private val browserUseToolset: BrowserUseToolset
) : AgentToolRegistry {

    private val registrations = ConcurrentHashMap<String, ToolRegistration>()

    init {
        registerBuiltIns()
    }

    private fun registerBuiltIns() {
        register(ToolRegistration(
            name = listFilesTool.name,
            tool = listFilesTool,
            definition = ToolDefinition(
                name = "list_files",
                description = "List files and directories in the active workspace using native APIs for high performance.",
                parameters = listOf(
                    ToolParameter("path", "string", "Optional path relative to the active workspace; omit for its root", required = false),
                    ToolParameter("pattern", "string", "Regex pattern to filter results", required = false),
                    ToolParameter("max_depth", "integer", "Maximum depth to recurse (default: 1)", required = false),
                    ToolParameter("include_hidden", "boolean", "Include hidden files (default: false)", required = false)
                )
            ),
            isWorkspaceRequired = true,
            isReadOnlyAllowed = true
        ))

        register(ToolRegistration(
            name = readFileTool.name,
            tool = readFileTool,
            definition = ToolDefinition(
                name = "read_file",
                description = "Read file content from the active workspace. Supports reading full content, line ranges, or line counts.",
                parameters = listOf(
                    ToolParameter("path", "string", "Path to the file relative to the active workspace", required = true),
                    ToolParameter("start_line", "integer", "Optional 1-based start line (inclusive)", required = false),
                    ToolParameter("end_line", "integer", "Optional 1-based end line (inclusive)", required = false),
                    ToolParameter("line_count", "boolean", "If true, returns only the total line count without file content", required = false)
                )
            ),
            isWorkspaceRequired = true,
            isReadOnlyAllowed = true
        ))

        register(ToolRegistration(
            name = writeFileTool.name,
            tool = writeFileTool,
            definition = ToolDefinition(
                name = "write_file",
                description = "Write or append content to a file in the active workspace. Automatically creates parent directories.",
                parameters = listOf(
                    ToolParameter("path", "string", "Path to the file relative to the active workspace", required = true),
                    ToolParameter("content", "string", "Content to write", required = true),
                    ToolParameter("append", "boolean", "Append instead of overwrite (default: false)", required = false)
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = createDirectoryTool.name,
            tool = createDirectoryTool,
            definition = ToolDefinition(
                name = "create_directory",
                description = "Create a directory and any necessary parent directories in the active workspace.",
                parameters = listOf(
                    ToolParameter("path", "string", "Directory path to create relative to the active workspace", required = true)
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = deleteFileTool.name,
            tool = deleteFileTool,
            definition = ToolDefinition(
                name = "delete_file",
                description = "Delete a file or directory in the active workspace. Use recursive=true for non-empty directories.",
                parameters = listOf(
                    ToolParameter("path", "string", "Path to delete relative to the active workspace", required = true),
                    ToolParameter("recursive", "boolean", "Delete recursively (default: false)", required = false)
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = runShellTool.name,
            tool = runShellTool,
            definition = ToolDefinition(
                name = "run_shell",
                description = "Execute a shell command in the active workspace. Commands are executed within the workspace context.",
                parameters = listOf(
                    ToolParameter("command", "string", "Command to execute", required = true),
                    ToolParameter("timeout_seconds", "integer", "Timeout in seconds (default: 60)", required = false)
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = editFileTool.name,
            tool = editFileTool,
            definition = ToolDefinition(
                name = "edit_file",
                description = "Edit a file in the active workspace. Supports exact text replacement, context-based patching, or unified diff (@@ hunks).",
                parameters = listOf(
                    ToolParameter("path", "string", "Path to the file relative to the active workspace", required = true),
                    ToolParameter("old_content", "string", "Exact text to find and replace", required = false),
                    ToolParameter("new_content", "string", "Text to replace with", required = false),
                    ToolParameter("diff", "string", "Unified diff content (@@ hunks) to apply as patch (text files only)", required = false),
                    ToolParameter("all_occurrences", "boolean", "Replace all occurrences (default: false)", required = false),
                    ToolParameter("dry_run", "boolean", "Preview changes without saving (default: false)", required = false)
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = findFilesTool.name,
            tool = findFilesTool,
            definition = ToolDefinition(
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
            isWorkspaceRequired = true,
            isReadOnlyAllowed = true
        ))

        register(ToolRegistration(
            name = createReminderTool.name,
            tool = createReminderTool,
            definition = ToolDefinition(
                name = "create_reminder",
                description = "Schedule a reminder via Android notification at a specific time. Use when user asks to be reminded about something.",
                parameters = listOf(
                    ToolParameter("title", "string", "Short reminder title", required = true),
                    ToolParameter("message", "string", "Full reminder message for the notification", required = true),
                    ToolParameter("datetime", "string", "ISO format YYYY-MM-DDTHH:MM (e.g. 2026-02-27T17:00)", required = true),
                    ToolParameter("reminder_type", "string", "ONE_TIME or RECURRING (default ONE_TIME)", required = false, enum = listOf("ONE_TIME", "RECURRING")),
                    ToolParameter("recurrence_pattern", "string", "DAILY, WEEKLY, MONTHLY (required if RECURRING)", required = false, enum = listOf("DAILY", "WEEKLY", "MONTHLY")),
                    ToolParameter("category", "string", "Optional category for grouping", required = false)
                )
            )
        ))

        register(ToolRegistration(
            name = updateMemoryTool.name,
            tool = updateMemoryTool
        ))

        register(ToolRegistration(
            name = memoryManageTool.name,
            tool = memoryManageTool,
            definition = ToolDefinition(
                name = "memory_manage",
                description = "Review, search, or update active durable memory records. Use to inspect what Amaya knows or resolve outdated facts.",
                parameters = listOf(
                    ToolParameter("title", "string", "List/search header, 3-5 words explaining why memory is opened (e.g. Review saved preferences)", required = false),
                    ToolParameter("action", "string", "list, search, update", required = true,
                        enum = listOf("list", "search", "update")),
                    ToolParameter("id", "string", "Memory id for update", required = false),
                    ToolParameter("expected_version", "integer", "Version returned by list/search; required for update to prevent overwriting newer memory", required = false),
                    ToolParameter("query", "string", "Search query for list/search", required = false),
                    ToolParameter("type", "string", "user_profile or workspace_fact", required = false,
                        enum = listOf("user_profile", "workspace_fact")),
                    ToolParameter("content", "string", "Replacement content for update", required = false),
                    ToolParameter("limit", "integer", "Max results, default 20", required = false)
                )
            )
        ))

        register(ToolRegistration(
            name = agentMemoryTool.name,
            tool = agentMemoryTool
        ))

        register(ToolRegistration(
            name = skillViewTool.name,
            tool = skillViewTool,
            definition = ToolDefinition(
                name = "skill_view",
                description = "Load full content and metadata for one relevant reusable skill. Use when the skill index says a skill may apply.",
                parameters = listOf(ToolParameter("name", "string", "Skill name", required = true))
            ),
            isReadOnlyAllowed = true
        ))

        register(ToolRegistration(
            name = skillManageTool.name,
            tool = skillManageTool,
            definition = ToolDefinition(
                name = "skill_manage",
                description = "Create, update, patch, archive, or delete reusable procedural skills only when the user explicitly asks to manage or save a skill/workflow. Do not create trivial or duplicate skills; never store credentials.",
                parameters = listOf(
                    ToolParameter("action", "string", "create, update, patch, archive, delete", required = true,
                        enum = listOf("create", "update", "patch", "archive", "delete")),
                    ToolParameter("name", "string", "Skill name", required = true),
                    ToolParameter("content", "string", "SKILL.md content or patch text", required = false),
                    ToolParameter("description", "string", "Short skill description for create", required = false),
                    ToolParameter("reason", "string", "Why this skill is being created or changed", required = false),
                    ToolParameter("summary", "string", "What was added or changed", required = false),
                    ToolParameter("tags", "array", "Skill tags", required = false, items = "string")
                )
            )
        ))

        register(ToolRegistration(
            name = recommendationManageTool.name,
            tool = recommendationManageTool
        ))

        register(ToolRegistration(
            name = sessionSearchTool.name,
            tool = sessionSearchTool,
            definition = ToolDefinition(
                name = "session_search",
                description = "Search previous sessions by query. Use this for recall instead of injecting old sessions in the prompt.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search query", required = true),
                    ToolParameter("limit", "integer", "Max results, default 10", required = false)
                )
            ),
            isReadOnlyAllowed = true
        ))

        register(ToolRegistration(
            name = updateTodoTool.name,
            tool = updateTodoTool,
            definition = ToolDefinition(
                name = "update_todo",
                description = "Maintain the plan/task list shown above the chat input. " +
                    "For any multi-step task, first call with merge=false to set your full plan (steps as todo items), " +
                    "then merge=true to update progress as you complete steps. " +
                    "When a step keeps failing, revise the plan (merge=false) or switch approach before retrying. " +
                    "Status: 'pending', 'in_progress', 'completed'.",
                parameters = listOf(
                    ToolParameter("merge", "boolean",
                        "true=merge by id into existing list, false=replace all items (use this to set or revise the full plan).", required = true),
                    ToolParameter("todos", "array",
                        "Todo items. Each: {id: int, status: string, content: string, active_form: string (optional)}",
                        required = true, items = "object")
                )
            )
        ))

        register(ToolRegistration(
            name = invokeSubagentsTool.name,
            tool = invokeSubagentsTool,
            definition = ToolDefinition(
                name = "invoke_subagents",
                description = "Spawn up to 4 independent AI subagents running IN PARALLEL. " +
                    "Each subagent has its own task and read-only workspace research tools. " +
                    "Use for independent sub-tasks (reading multiple folders, auditing different layers). " +
                    "Subagents do NOT see conversation history — include ALL context in task. " +
                    "Returns combined summary from all subagents.",
                parameters = listOf(
                    ToolParameter(
                        name = "title",
                        type = "string",
                        description = "Short title for this parallel work shown in UI header (e.g. 'Auditing codebase'). Required.",
                        required = true
                    ),
                    ToolParameter(
                        name = "subagents",
                        type = "array",
                        description = "List of subagent tasks. Each: {task_name: string (≤5 words), task: string (full self-contained prompt)}",
                        required = true,
                        items = "object"
                    )
                )
            ),
            isWorkspaceRequired = true
        ))

        register(ToolRegistration(
            name = delegateAgentTool.name,
            tool = delegateAgentTool
        ))

        register(ToolRegistration(
            name = webSearchTool.name,
            tool = webSearchTool,
            definition = ToolDefinition(
                name = "web_search",
                description = "Search the web and fetch result pages as extracted readable text only. Safe for deep research: the AI may call many web_search tools in parallel. Returns one JSON object with search_results and pages[].text; raw DOM, HTML, screenshots, and browser state are omitted. Use urls[] to fetch known links directly, or query to discover links first. Tries DuckDuckGo then Bing by default; set search_provider to bing to reverse the fallback order.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search query. Optional when urls is provided.", required = false),
                    ToolParameter("urls", "array", "Known URLs to fetch directly. Can be combined with query results.", required = false, items = "string"),
                    ToolParameter("max_results", "integer", "Maximum search results to collect (default: ${WebSearchTool.DEFAULT_MAX_RESULTS}, max: ${WebSearchTool.MAX_MAX_RESULTS}).", required = false),
                    ToolParameter("max_pages", "integer", "Maximum result/URL pages to fetch and extract (default: max_results, max: ${WebSearchTool.MAX_MAX_PAGES}).", required = false),
                    ToolParameter("max_chars_per_page", "integer", "Maximum extracted text characters per page (default: ${WebSearchTool.DEFAULT_MAX_CHARS_PER_PAGE}, max: ${WebSearchTool.MAX_MAX_CHARS_PER_PAGE}).", required = false)
                )
            ),
            isReadOnlyAllowed = true
        ))

        browserUseToolset.tools.forEach { browserTool ->
            register(ToolRegistration(
                name = browserTool.name,
                tool = browserTool
            ))
        }
    }

    override fun register(registration: ToolRegistration) {
        registrations[registration.name] = registration
    }

    override fun unregister(name: String): Boolean = registrations.remove(name) != null

    override fun getTool(name: String): Tool? = registrations[name]?.tool

    override fun getModelCallableTools(): List<Tool> =
        registrations.values.map { it.tool }.filter { it.visibility == ToolVisibility.MODEL }

    override fun getReadOnlyToolDefinitions(): List<ToolDefinition> = getToolDefinitions()
        .filter { it.name in setOf("read_file", "workspace_search", "web_search", "memory", "skill") }
        .map { definition ->
            when (definition.name) {
                "memory" -> definition.copy(parameters = listOf(
                    ToolParameter("operation", "string", "recall_sessions", enum = listOf("recall_sessions")),
                    ToolParameter("query", "string", "Session recall query")
                ))
                "skill" -> definition.copy(parameters = listOf(
                    ToolParameter("operation", "string", "view", enum = listOf("view")),
                    ToolParameter("skill_id", "string", "Skill id/name")
                ))
                else -> definition
            }
        }

    override fun isAllowedInReadOnlyMode(handlerName: String): Boolean =
        registrations[handlerName]?.isReadOnlyAllowed == true

    override fun getToolDefinitions(
        mode: AssistantMode,
        agentCapabilityProfile: AgentCapabilityProfile?,
        delegationAgentIds: List<Long>
    ): List<ToolDefinition> {
        val compositeDefinitions = listOf(
            ToolDefinition(
                name = "workspace_search",
                description = "Search the active workspace. Use list to list a directory (omit path for root), find_name for filename patterns, and find_text for content.",
                parameters = listOf(
                    ToolParameter("operation", "string", "list, find_name, or find_text", enum = listOf("list", "find_name", "find_text")),
                    ToolParameter("path", "string", "Optional path relative to the active workspace", required = false),
                    ToolParameter("query", "string", "Required for find_name/find_text", required = false),
                    ToolParameter("max_depth", "integer", "Maximum depth", required = false),
                    ToolParameter("max_results", "integer", "Maximum results", required = false)
                )
            ),
            ToolDefinition(
                name = "workspace_change",
                description = "Change files in the active workspace. Use write, append, replace, patch, mkdir, or delete.",
                parameters = listOf(
                    ToolParameter("operation", "string", "write, append, replace, patch, mkdir, or delete", enum = listOf("write", "append", "replace", "patch", "mkdir", "delete")),
                    ToolParameter("path", "string", "Path relative to the active workspace", required = true),
                    ToolParameter("content", "string", "Content for write/append", required = false),
                    ToolParameter("old_text", "string", "Exact text for replace", required = false),
                    ToolParameter("new_text", "string", "Replacement text", required = false),
                    ToolParameter("diff", "string", "Unified diff for patch", required = false)
                )
            ),
            ToolDefinition(
                name = "delegate_agent",
                description = "Delegate one focused task to a named persistent Agent in this group. Use the group-local agent_id from the host directory. This is different from invoke_subagents, which creates temporary parallel read-only workers.",
                parameters = listOf(
                    ToolParameter("title", "string", "Short delegation title, 2-5 words; do not repeat the task", required = true),
                    ToolParameter(
                        "agent_id",
                        "integer",
                        "Group-local member ID from the active agent directory; IDs restart at 1 per group${delegationAgentIds.takeIf(List<Long>::isNotEmpty)?.joinToString(prefix = ": ").orEmpty()}",
                        required = true
                    ),
                    ToolParameter("task", "string", "Focused task with all needed context", required = true)
                )
            ),
            ToolDefinition(
                name = "memory",
                description = "Manage active saved memory or recall previous sessions. Update requires the version returned by list/search.",
                parameters = listOf(
                    ToolParameter("operation", "string", "save, list, search, update, or recall_sessions", enum = listOf("save", "list", "search", "update", "recall_sessions")),
                    ToolParameter("id", "string", "Memory id for update", required = false),
                    ToolParameter("expected_version", "integer", "Required for update", required = false),
                    ToolParameter("content", "string", "Memory content", required = false),
                    ToolParameter("query", "string", "Search or recall query", required = false),
                    ToolParameter("title", "string", "Short memory title or list/search header", required = false),
                    ToolParameter("type", "string", "user_profile or workspace_fact", required = false,
                        enum = listOf("user_profile", "workspace_fact")),
                    ToolParameter("reason", "string", "Specific reason this memory is durable", required = false),
                    ToolParameter("confidence", "number", "0.0-1.0 confidence", required = false),
                    ToolParameter("ttl_days", "integer", "Optional time-to-live in days. Default is determined by volatility (e.g. 14 for perishable, 90 for moderate, indefinite for stable)", required = false),
                    ToolParameter("volatility", "string", "Volatility class: stable (user profile/preferences), moderate (project decisions/facts), or perishable (environment/temporary states)", required = false, enum = listOf("stable", "moderate", "perishable")),
                    ToolParameter("limit", "integer", "Max list/search results", required = false)
                )
            ),
            ToolDefinition(
                name = "skill",
                description = "View or manage reusable skills.",
                parameters = listOf(
                    ToolParameter("operation", "string", "view, create, update, patch, archive, or delete", enum = listOf("view", "create", "update", "patch", "archive", "delete")),
                    ToolParameter("name", "string", "Skill name", required = false),
                    ToolParameter("skill_id", "string", "Skill id/name for view", required = false),
                    ToolParameter("content", "string", "Skill content or patch", required = false)
                )
            ),
            ToolDefinition(
                name = "reminder",
                description = "Schedule a reminder.",
                parameters = listOf(
                    ToolParameter("operation", "string", "create", enum = listOf("create")),
                    ToolParameter("title", "string", "Reminder title"),
                    ToolParameter("message", "string", "Reminder message"),
                    ToolParameter("datetime", "string", "ISO local datetime")
                )
            ),
            ToolDefinition(
                name = "ask_user",
                description = "Ask the user a short question when the request is ambiguous, choices conflict, or a prerequisite is missing. " +
                    "The turn pauses and resumes with the user's answer. Use it instead of guessing; do not use it for approvals or " +
                    "trivial questions you can resolve from context.",
                parameters = listOf(
                    ToolParameter("question", "string", "Short, specific question (one sentence, max ~200 chars)", required = true),
                    ToolParameter("options", "array", "Optional answer choices the user can pick quickly", required = false, items = "string")
                )
            )
        )

        val individualDefinitions = registrations.values.mapNotNull { it.definition }
        val browserDefinitions = browserUseToolset.getToolDefinitions()
        val allDefinitions = compositeDefinitions + individualDefinitions + browserDefinitions

        return allDefinitions.mapNotNull { definition ->
            val exposed = exposeToolDefinition(definition, mode)
            exposed.takeIf { assistantModeAllowsCapability(exposed.name, mode, agentCapabilityProfile) }
        }
    }
}
