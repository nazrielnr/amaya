package com.amaya.intelligence.impl.common.mappers

import com.amaya.intelligence.domain.models.ToolUiMetadata
import com.amaya.intelligence.domain.models.ToolCategory
import com.amaya.intelligence.domain.models.ToolInfoIcon

object ToolUiMapper {
    fun getToolUiMetadata(name: String, args: Map<String, Any?>?, metadata: Map<String, String>? = null): ToolUiMetadata {
        val safeArgs = args ?: emptyMap()
        val safeMeta = metadata ?: emptyMap()
        
        return when (name) {
            // -- Files ---------------------------------------------------------
            "read_file", "view_file" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = cleanArtifact(fileName(if (name == "view_file") "AbsolutePath" else "path", safeArgs)),
                actionIcon = ToolInfoIcon.READ,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("READ")
            )
            "write_file", "write_to_file", "create_file" -> {
                val isOverwrite = safeArgs["Overwrite"] == true || safeArgs["overwrite"] == true
                ToolUiMetadata(
                    category = ToolCategory.FILE_IO,
                    label = cleanArtifact(fileName(if (name == "write_to_file") "TargetFile" else "path", safeArgs)),
                    actionIcon = ToolInfoIcon.WRITE,
                    targetIcon = ToolInfoIcon.FILE,
                    badges = if (isOverwrite) listOf("WRITE", "OVERWRITE") else listOf("WRITE")
                )
            }
            "edit_file", "replace_file_content", "multi_replace_file_content" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = cleanArtifact(fileName(if (name == "edit_file") "path" else "TargetFile", safeArgs)),
                actionIcon = ToolInfoIcon.EDIT,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("EDIT")
            )
            "delete_file" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = cleanArtifact(fileName("path", safeArgs)),
                actionIcon = ToolInfoIcon.DELETE,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("DELETE")
            )
            "create_directory" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = cleanArtifact(fileName("path", safeArgs)).ifBlank { "Directory" },
                actionIcon = ToolInfoIcon.FOLDER,
                targetIcon = ToolInfoIcon.FOLDER,
                badges = listOf("CREATE")
            )
            "undo_change" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = cleanArtifact(fileName("path", safeArgs)).ifBlank { "Restore file" },
                actionIcon = ToolInfoIcon.EDIT,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("RESTORE")
            )
            "list_files", "list_dir" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = fileName(if (name == "list_files") "path" else "DirectoryPath", safeArgs),
                actionIcon = ToolInfoIcon.LIST,
                targetIcon = ToolInfoIcon.FOLDER,
                badges = listOf("LIST")
            )
            "find_files", "grep_search", "find_by_name" -> ToolUiMetadata(
                category = ToolCategory.SEARCH,
                label = (safeArgs["query"] ?: safeArgs["Query"] ?: safeArgs["Pattern"] ?: "").toString().take(20),
                actionIcon = ToolInfoIcon.FIND,
                targetIcon = ToolInfoIcon.SEARCH,
                badges = listOf("FIND")
            )

            // -- Tasks ---------------------------------------------------------
            "invoke_subagents" -> ToolUiMetadata(
                category = ToolCategory.TASK_MANAGEMENT,
                label = safeText(safeArgs["title"], 120) ?: "Parallel work",
                actionIcon = ToolInfoIcon.ROCKET,
                targetIcon = ToolInfoIcon.PERSON,
                badges = listOf("SUBAGENTS")
            )
            "delegate_agent" -> ToolUiMetadata(
                category = ToolCategory.TASK_MANAGEMENT,
                label = safeText(safeArgs["title"], 80) ?: "Delegation",
                actionIcon = ToolInfoIcon.ROCKET,
                targetIcon = ToolInfoIcon.PERSON,
                badges = listOf("DELEGATE")
            )
            "create_reminder" -> ToolUiMetadata(
                category = ToolCategory.TASK_MANAGEMENT,
                label = safeText(safeArgs["title"], 120) ?: "Reminder",
                actionIcon = ToolInfoIcon.TASK,
                targetIcon = ToolInfoIcon.MESSAGE,
                badges = listOf("REMINDER")
            )
            "session_search" -> ToolUiMetadata(
                category = ToolCategory.SEARCH,
                label = safeText(safeArgs["query"], 120) ?: "Previous sessions",
                actionIcon = ToolInfoIcon.SEARCH,
                targetIcon = ToolInfoIcon.MESSAGE,
                badges = listOf("RECALL")
            )
            "task_boundary" -> {
                val rawMode = (safeArgs["Mode"]?.toString() ?: "TASK").uppercase()
                val mode = if (rawMode.contains("%SAME%") || rawMode.contains("SAME")) "UPDATE" else rawMode
                ToolUiMetadata(
                    category = ToolCategory.SYSTEM,
                    label = safeText(safeArgs["TaskName"] ?: safeArgs["title"] ?: safeArgs["TaskStatus"], 500) ?: "Task",
                    actionIcon = ToolInfoIcon.TASK,
                    targetIcon = ToolInfoIcon.COMMAND,
                    badges = listOf(mode)
                )
            }

            // -- Shell/Terminal ---------------------------------------------------------
            "run_shell", "run_command" -> {
                val cmd = (safeArgs["command"] ?: safeArgs["CommandLine"] ?: "").toString()
                val firstToken = cmd.trim().removeSurrounding("\"").removeSurrounding("'")
                    .split(" ").firstOrNull()?.substringAfterLast("/")?.substringAfterLast("\\") ?: "Shell"
                ToolUiMetadata(
                    category = ToolCategory.SHELL,
                    label = firstToken,
                    actionIcon = ToolInfoIcon.RUN,
                    targetIcon = ToolInfoIcon.COMMAND,
                    badges = listOf("RUN")
                )
            }
            "command_status", "check_status_terminal" -> {
                val rawId = (safeArgs["CommandId"] ?: safeArgs["ProcessID"] ?: safeArgs["commandId"] ?: "").toString()
                val cmdHint = (
                    safeArgs["command"] ?: safeArgs["CommandLine"] ?: safeArgs["CommandHint"] ?: safeArgs["program"] ?:
                    safeMeta["command"] ?: safeMeta["CommandLine"] ?: safeMeta["cmd"] ?: safeMeta["program"] ?:
                    safeMeta["command_line"] ?: ""
                ).toString().trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("Status ").trim()

                val pid = (
                    safeArgs["Pid"] ?: safeArgs["PID"] ?: safeArgs["processId"] ?: safeArgs["ProcessID"] ?:
                    safeMeta["pid"] ?: safeMeta["PID"] ?: safeMeta["processId"] ?: safeMeta["ProcessId"] ?: ""
                ).toString()
                val pidSuffix = if (pid.isNotBlank()) " ($pid)" else ""

                val label = when {
                    cmdHint.isNotBlank() && cmdHint != rawId -> "${cmdHint.take(60).trim()}$pidSuffix"
                    rawId.isNotBlank() -> rawId.take(8)
                    else -> "Process"
                }
                ToolUiMetadata(
                    category = ToolCategory.SHELL,
                    label = label,
                    actionIcon = ToolInfoIcon.CHECK,
                    targetIcon = ToolInfoIcon.TERMINAL,
                    badges = listOf("CHECK")
                )
            }
            "read_terminal" -> ToolUiMetadata(
                category = ToolCategory.SHELL,
                label = (safeArgs["Name"] ?: safeArgs["ProcessID"] ?: "Output").toString(),
                actionIcon = ToolInfoIcon.READ,
                targetIcon = ToolInfoIcon.TERMINAL,
                badges = listOf("READ")
            )

            // -- Web ---------------------------------------------------------
            "search_web", "web_search" -> ToolUiMetadata(
                category = ToolCategory.WEB,
                label = safeArgs["query"]?.toString()?.take(30) ?: "Search",
                actionIcon = ToolInfoIcon.SEARCH,
                targetIcon = ToolInfoIcon.WORLD,
                badges = listOf("SEARCH")
            )
            "read_url_content" -> {
                val url = safeArgs["Url"]?.toString() ?: ""
                val domain = url.substringAfter("://").substringBefore("/")
                ToolUiMetadata(
                    category = ToolCategory.WEB,
                    label = domain,
                    actionIcon = ToolInfoIcon.WEB_READ,
                    targetIcon = ToolInfoIcon.LINK,
                    badges = listOf("WEB-READ")
                )
            }

            // -- Meta ---------------------------------------------------------
            "ask_user" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = safeText(safeArgs["question"], 60) ?: "Question for user",
                actionIcon = ToolInfoIcon.MESSAGE,
                targetIcon = ToolInfoIcon.PERSON,
                badges = listOf("ASK")
            )
            "notify_user" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "User",
                actionIcon = ToolInfoIcon.MESSAGE,
                targetIcon = ToolInfoIcon.PERSON,
                badges = listOf("MESSAGE")
            )
            "generate_image" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = safeArgs["ImageName"]?.toString() ?: safeArgs["Prompt"]?.toString()?.take(20) ?: "Image",
                actionIcon = ToolInfoIcon.GENERATE,
                targetIcon = ToolInfoIcon.IMAGE,
                badges = listOf("GENERATE")
            )
            "browser", "browser_subagent", "chrome-devtools",
            "open_url", "new_page", "close_page", "click_element", "type_text", "clear_input",
            "scroll_page", "get_dom", "get_visible_text", "get_screenshot", "find_element",
            "wait_for_element", "go_back", "go_forward", "reload_page", "cancel_action" -> ToolUiMetadata(
                category = ToolCategory.WEB,
                label = safeArgs["url"]?.toString()?.substringAfter("://")?.substringBefore("/")
                    ?: safeArgs["selector"]?.toString()?.take(30)
                    ?: safeArgs["query"]?.toString()?.take(30)
                    ?: safeArgs["task"]?.toString()?.take(30)
                    ?: safeArgs["TaskName"]?.toString()
                    ?: "Browser",
                actionIcon = ToolInfoIcon.BROWSER,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("BROWSER")
            )
            "context7" -> ToolUiMetadata(
                category = ToolCategory.WEB,
                label = safeArgs["libraryId"]?.toString()?.substringAfterLast("/") ?: "Docs",
                actionIcon = ToolInfoIcon.DOCS,
                targetIcon = ToolInfoIcon.BOOK,
                badges = listOf("DOCS")
            )

            // -- Thinking -----------------------------------------------------
            "thinking" -> ToolUiMetadata(
                category = ToolCategory.TASK_MANAGEMENT,
                label = safeText(safeMeta["thoughtTitle"] ?: safeArgs["thoughtTitle"], 50) ?: "Thinking",
                actionIcon = ToolInfoIcon.LIGHTBULB,
                targetIcon = ToolInfoIcon.GENERATE,
                badges = listOf("THINKING")
            )

            // -- Memory --------------------------------------------------------
            "update_memory" -> ToolUiMetadata(
                category = ToolCategory.MEMORY,
                label = safeText(safeArgs["title"] ?: safeArgs["content"], 64) ?: "Memory",
                actionIcon = ToolInfoIcon.BRAIN,
                targetIcon = ToolInfoIcon.PERSON,
                badges = listOf((safeArgs["action"]?.toString() ?: "SAVE").uppercase())
            )
            "memory_manage" -> {
                val action = safeArgs["action"]?.toString()?.lowercase().orEmpty()
                val target = safeText(safeArgs["title"] ?: safeArgs["content"] ?: safeArgs["query"] ?: safeArgs["id"], 56)
                val label = when (action) {
                    "update", "replace" -> "Update: ${target ?: "memory"}"
                    "search" -> target ?: "Search memory"
                    "list" -> target ?: "Review memory"
                    else -> target ?: "Memory"
                }
                ToolUiMetadata(
                    category = ToolCategory.MEMORY,
                    label = label,
                    actionIcon = ToolInfoIcon.BRAIN,
                    targetIcon = ToolInfoIcon.PERSON,
                    badges = listOf((safeArgs["action"]?.toString() ?: "MEMORY").uppercase())
                )
            }

            // -- Skills --------------------------------------------------------
            "skill_view" -> ToolUiMetadata(
                category = ToolCategory.SKILL,
                label = "View: ${safeText(safeArgs["name"], 64) ?: "skill"}",
                actionIcon = ToolInfoIcon.BOOK,
                targetIcon = ToolInfoIcon.BOOK,
                badges = listOf("VIEW")
            )
            "skill_manage" -> {
                val action = safeArgs["action"]?.toString()?.lowercase().orEmpty()
                val name = safeText(safeArgs["name"], 64) ?: "skill"
                val verb = when (action) {
                    "create" -> "Create"
                    "update" -> "Update"
                    "patch" -> "Patch"
                    "archive" -> "Archive"
                    "delete" -> "Delete"
                    else -> "Skill"
                }
                ToolUiMetadata(
                    category = ToolCategory.SKILL,
                    label = "$verb: $name",
                    actionIcon = ToolInfoIcon.BOOK,
                    targetIcon = ToolInfoIcon.BOOK,
                    badges = listOf((safeArgs["action"]?.toString() ?: "SKILL").uppercase())
                )
            }

            // -- Internal Tasks ------------------------------------------------
            "update_todo" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "To-Do List",
                actionIcon = ToolInfoIcon.TASK,
                targetIcon = ToolInfoIcon.FILE,
                isHidden = true
            )

            // -- Windows Bridge Tools ------------------------------------------
            "screen.capture" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Screen Capture",
                actionIcon = ToolInfoIcon.IMAGE,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "window.list" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Window List",
                actionIcon = ToolInfoIcon.LIST,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "window.focus" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Focus Windows Window",
                actionIcon = ToolInfoIcon.MOUSE,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "window.close" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Close Windows Window",
                actionIcon = ToolInfoIcon.COMMAND,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "app.open" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Open Windows App",
                actionIcon = ToolInfoIcon.COMMAND,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "mouse.click" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Mouse Click",
                actionIcon = ToolInfoIcon.MOUSE,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "keyboard.type" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Keyboard Type",
                actionIcon = ToolInfoIcon.COMMAND,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "keyboard.hotkey" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Hotkey",
                actionIcon = ToolInfoIcon.COMMAND,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "clipboard.write" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows Clipboard Write",
                actionIcon = ToolInfoIcon.WRITE,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS")
            )
            "file.list" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = safeArgs["path"]?.toString()?.substringAfterLast("\\")?.substringAfterLast("/") ?: "Windows Files",
                actionIcon = ToolInfoIcon.LIST,
                targetIcon = ToolInfoIcon.FOLDER,
                badges = listOf("WINDOWS", "LIST")
            )
            "file.read" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = safeArgs["path"]?.toString()?.substringAfterLast("\\")?.substringAfterLast("/") ?: "Windows File",
                actionIcon = ToolInfoIcon.READ,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("WINDOWS", "READ")
            )
            "file.write" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = safeArgs["path"]?.toString()?.substringAfterLast("\\")?.substringAfterLast("/") ?: "Windows File",
                actionIcon = ToolInfoIcon.WRITE,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("WINDOWS", "WRITE")
            )
            "file.edit" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = safeArgs["path"]?.toString()?.substringAfterLast("\\")?.substringAfterLast("/") ?: "Windows File",
                actionIcon = ToolInfoIcon.EDIT,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("WINDOWS", "EDIT")
            )
            "file.delete" -> ToolUiMetadata(
                category = ToolCategory.FILE_IO,
                label = safeArgs["path"]?.toString()?.substringAfterLast("\\")?.substringAfterLast("/") ?: "Windows File",
                actionIcon = ToolInfoIcon.DELETE,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("WINDOWS", "DELETE")
            )
            "ui.tree" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Windows UI Tree",
                actionIcon = ToolInfoIcon.LIST,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS", "UI")
            )
            "ui.find_text" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Find Windows UI Text",
                actionIcon = ToolInfoIcon.SEARCH,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS", "UI")
            )
            "ui.click_element" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "Click Windows UI Element",
                actionIcon = ToolInfoIcon.MOUSE,
                targetIcon = ToolInfoIcon.MOUSE,
                badges = listOf("WINDOWS", "UI")
            )
            "shell.run" -> {
                val cmd = (safeArgs["command"] ?: "").toString()
                val firstToken = cmd.trim().split(" ").firstOrNull()?.substringAfterLast("\\") ?: "Shell"
                ToolUiMetadata(
                    category = ToolCategory.SHELL,
                    label = firstToken,
                    actionIcon = ToolInfoIcon.RUN,
                    targetIcon = ToolInfoIcon.COMMAND,
                    badges = listOf("WINDOWS", "RUN")
                )
            }
            "shell.cancel" -> ToolUiMetadata(
                category = ToolCategory.SHELL,
                label = "Cancel Windows Command",
                actionIcon = ToolInfoIcon.CHECK,
                targetIcon = ToolInfoIcon.COMMAND,
                badges = listOf("WINDOWS", "CANCEL")
            )

            else -> {
                val displayName = name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                ToolUiMetadata(
                    category = ToolCategory.UNKNOWN,
                    label = displayName,
                    actionIcon = ToolInfoIcon.TASK,
                    targetIcon = ToolInfoIcon.FILE,
                    badges = listOf(name.substringBefore("_").uppercase())
                )
            }
        }
    }

    private fun cleanArtifact(text: String): String {
        return when (text.substringAfterLast("/").substringAfterLast("\\").removeSuffix(".md").lowercase()) {
            "task" -> "Tasks"
            "walkthrough" -> "Walkthrough"
            "implementation_plan" -> "Implementation Plan"
            else -> text.substringAfterLast("/").substringAfterLast("\\").removeSuffix(".md")
        }
    }

    private fun fileName(key: String, args: Map<String, Any?>?): String {
        val path = args?.get(key)?.toString() ?: ""
        return path.substringAfterLast("/").substringAfterLast("\\")
    }

    private fun safeText(v: Any?, max: Int): String? {
        val s = v?.toString() ?: return null
        if (s.contains("%SAME%")) return null
        return if (s.length > max) s.take(max) + "…" else s
    }
}
