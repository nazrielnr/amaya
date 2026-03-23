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
                actionIcon = ToolInfoIcon.EDIT,
                targetIcon = ToolInfoIcon.FILE,
                badges = listOf("DELETE")
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
            "search_web" -> ToolUiMetadata(
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
            "browser", "browser_subagent", "chrome-devtools" -> ToolUiMetadata(
                category = ToolCategory.WEB,
                label = safeArgs["task"]?.toString()?.take(30) ?: safeArgs["TaskName"]?.toString() ?: "Browser",
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
                actionIcon = ToolInfoIcon.BRAIN,
                targetIcon = ToolInfoIcon.GENERATE,
                badges = listOf("THINKING")
            )

            // -- Internal Tasks ------------------------------------------------
            "update_todo" -> ToolUiMetadata(
                category = ToolCategory.SYSTEM,
                label = "To-Do List",
                actionIcon = ToolInfoIcon.TASK,
                targetIcon = ToolInfoIcon.FILE,
                isHidden = true
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
