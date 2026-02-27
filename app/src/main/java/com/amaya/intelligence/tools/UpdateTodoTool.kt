package com.amaya.intelligence.tools

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool that allows the AI to update a shared todo/task list visible in the UI.
 *
 * The AI calls this tool to communicate its current plan and progress to the user.
 * Items are shown above the chat input as a collapsible bar.
 *
 * Status values: "pending" | "in_progress" | "completed"
 */
@Singleton
class UpdateTodoTool @Inject constructor(
    private val todoRepository: TodoRepository
) : Tool {

    override val name = "update_todo"
    override val description = "Update the task list shown to the user above the chat input. " +
        "Use this to communicate your plan and progress. " +
        "Call with merge=true to update specific items, merge=false to replace all items. " +
        "Status values: 'pending', 'in_progress', 'completed'."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return try {
            val merge = arguments["merge"] as? Boolean ?: true
            @Suppress("UNCHECKED_CAST")
            val todosRaw = arguments["todos"] as? List<Map<String, Any?>>
                ?: return ToolResult.Error("Missing 'todos' argument", ErrorType.VALIDATION_ERROR)

            val items = todosRaw.mapNotNull { map ->
                val id = when (val rawId = map["id"]) {
                    is Int -> rawId
                    is Double -> rawId.toInt()
                    is String -> rawId.toIntOrNull() ?: return@mapNotNull null
                    else -> return@mapNotNull null
                }
                val statusStr = map["status"] as? String ?: "pending"
                val status = TodoStatus.fromString(statusStr)
                val content = map["content"] as? String
                val activeForm = map["active_form"] as? String

                TodoItem(id = id, status = status, content = content, activeForm = activeForm)
            }

            if (merge) {
                todoRepository.mergeItems(items)
            } else {
                todoRepository.replaceItems(items)
            }

            val total = todoRepository.getItems().size
            val done = todoRepository.getItems().count { it.status == TodoStatus.COMPLETED }
            ToolResult.Success("Todo updated: $done/$total completed")
        } catch (e: Exception) {
            ToolResult.Error("Failed to update todo: ${e.message}", ErrorType.EXECUTION_ERROR)
        }
    }
}
