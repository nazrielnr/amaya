package com.amaya.intelligence.domain.models

data class AgentCapabilityProfile(
    val workspace: Boolean = true,
    val terminal: Boolean = true,
    val browser: Boolean = true,
    val subagents: Boolean = true,
    val webSearch: Boolean = true,
    val skills: Boolean = true,
    val reminders: Boolean = true,
    val todo: Boolean = true,
    // Default true: MCP tools were always advertised to agents before this switch existed,
    // so legacy profiles (no `mcp=` segment) keep working unchanged after decode.
    val mcp: Boolean = true,
    /** Model-facing tool names excluded within an enabled category (per-tool overrides). */
    val excludedTools: Set<String> = emptySet()
) {
    fun allows(toolName: String): Boolean {
        // In agent groups the generic memory tools are replaced by agent_memory; the raw
        // names must never reach the model even with every category enabled.
        if (toolName in BLOCKED_IN_AGENT_PROFILE) return false
        // Host-safe / identity tools (ask_user, agent_memory, skill index, …) are
        // never capability-gated: only tools listed in a category here are profile-controlled.
        val category = CapabilityToolGroups.groupFor(toolName) ?: return true
        val categoryEnabled = when (category) {
            "workspace" -> workspace
            "terminal" -> terminal
            "browser" -> browser
            "subagents" -> subagents
            "web_search" -> webSearch
            "skills" -> skills
            "reminders" -> reminders
            "todo" -> todo
            "mcp" -> mcp
            else -> true
        }
        if (!categoryEnabled) return false
        return toolName !in excludedTools
    }

    /** Model-facing tool names are expressed as `tool=<name>=off` segments. */
    fun encode(): String {
        val segments = mutableListOf(
            "workspace=$workspace",
            "terminal=$terminal",
            "browser=$browser",
            "subagents=$subagents",
            "web_search=$webSearch",
            "skills=$skills",
            "reminders=$reminders",
            "todo=$todo",
            "mcp=$mcp"
        )
        excludedTools.sorted().forEach { segments.add("tool=$it=off") }
        return segments.joinToString(";")
    }

    companion object {
        /** Memory tools the host swaps for agent_memory in AGENT mode (see exposeToolDefinition). */
        private val BLOCKED_IN_AGENT_PROFILE = setOf("memory", "update_memory", "memory_manage")

        fun decode(value: String?): AgentCapabilityProfile {
            val pairs = value.orEmpty().split(';').mapNotNull { part ->
                val pieces = part.split('=')
                pieces.takeIf { it.size >= 2 }?.let { it[0] to pieces.drop(1) }
            }
            val flags = pairs.filter { (key, _) -> key != "tool" }.associate { (key, rest) -> key to rest.firstOrNull() }
            fun flag(name: String): Boolean = flags[name]?.toBooleanStrictOrNull() ?: true
            // "tool=write_file=off"; tolerate a bare "tool=write_file" as off too.
            val excluded = pairs.filter { (key, rest) -> key == "tool" && rest.lastOrNull() != "on" }
                .map { (_, rest) -> rest.first() }
                .toSet()
            return AgentCapabilityProfile(
                workspace = flag("workspace"),
                terminal = flag("terminal"),
                browser = flag("browser"),
                subagents = flag("subagents"),
                webSearch = flag("web_search"),
                skills = flag("skills"),
                reminders = flag("reminders"),
                todo = flag("todo"),
                mcp = flag("mcp"),
                excludedTools = excluded
            )
        }
    }
}
