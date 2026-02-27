package com.amaya.intelligence.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TodoStatus {
    PENDING, IN_PROGRESS, COMPLETED;

    companion object {
        fun fromString(s: String): TodoStatus = when (s.lowercase().replace("-", "_")) {
            "in_progress", "inprogress", "active" -> IN_PROGRESS
            "completed", "done", "finished"       -> COMPLETED
            else                                   -> PENDING
        }
    }
}

data class TodoItem(
    val id: Int,
    val status: TodoStatus,
    val content: String? = null,
    val activeForm: String? = null
)

@Singleton
class TodoRepository @Inject constructor() {

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())
    val items: StateFlow<List<TodoItem>> = _items.asStateFlow()

    fun getItems(): List<TodoItem> = _items.value

    /** Replace all items with the new list. */
    fun replaceItems(newItems: List<TodoItem>) {
        _items.value = newItems
    }

    /**
     * Merge incoming items into the existing list by id.
     * - If id exists: update only non-null fields from incoming item.
     * - If id does not exist: append as new item.
     */
    fun mergeItems(incoming: List<TodoItem>) {
        val current = _items.value.toMutableList()
        for (inItem in incoming) {
            val idx = current.indexOfFirst { it.id == inItem.id }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = existing.copy(
                    status     = inItem.status,
                    content    = inItem.content ?: existing.content,
                    activeForm = inItem.activeForm ?: existing.activeForm
                )
            } else {
                current.add(inItem)
            }
        }
        // Sort by id so the list is always ordered
        _items.value = current.sortedBy { it.id }
    }

    /** Clear all todos (called on new conversation). */
    fun clear() {
        _items.value = emptyList()
    }
}
