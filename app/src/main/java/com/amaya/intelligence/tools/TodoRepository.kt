package com.amaya.intelligence.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        // FIX #14: Use update{} to ensure atomic compare-and-set, preventing lost updates
        _items.value = newItems
    }

    /**
     * Merge incoming items into the existing list by id.
     * - If id exists: update only non-null fields from incoming item.
     * - If id does not exist: append as new item.
     *
     * FIX #14: Uses MutableStateFlow.update{} for atomic read-modify-write,
     * preventing lost updates when two coroutines merge concurrently.
     */
    fun mergeItems(incoming: List<TodoItem>) {
        _items.update { current ->
            val mutable = current.toMutableList()
            for (inItem in incoming) {
                val idx = mutable.indexOfFirst { it.id == inItem.id }
                if (idx >= 0) {
                    val existing = mutable[idx]
                    mutable[idx] = existing.copy(
                        status     = inItem.status,
                        content    = inItem.content ?: existing.content,
                        activeForm = inItem.activeForm ?: existing.activeForm
                    )
                } else {
                    mutable.add(inItem)
                }
            }
            // Sort by id so the list is always ordered
            mutable.sortedBy { it.id }
        }
    }

    /** Clear all todos (called on new conversation). */
    fun clear() {
        _items.value = emptyList()
    }
}
