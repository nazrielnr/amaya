package com.amaya.intelligence.ui.screens.chat.local

import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.tools.TodoItem

/**
 * State specific to local chat mode.
 * Contains local-only features like reminders, todos, and local project files.
 */
data class LocalChatState(
    val projectFiles: List<ProjectFileEntry> = emptyList(),
    val reminderCount: Int = 0,
    val todoItems: List<TodoItem> = emptyList(),
    val attachedFilePath: String? = null
)

/**
 * Actions available in local chat context.
 */
interface LocalChatActions {
    fun attachFile(path: String)
    fun clearAttachment()
    fun loadProjectFiles(path: String)
}
