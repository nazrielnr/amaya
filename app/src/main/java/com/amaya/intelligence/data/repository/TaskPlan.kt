package com.amaya.intelligence.data.repository

import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoStatus

/**
 * Scheme E (plan-then-execute): a host-side snapshot of the model's plan, mirrored from the
 * visible todo list (TodoRepository). Kept in loop state so the plan survives context
 * compaction, and injected into the system prompt every iteration so the model stays pinned
 * to its plan even after long tool sequences.
 */
data class TaskPlan(
    val steps: List<TaskPlanStep>
) {
    val doneCount: Int get() = steps.count { it.status == TodoStatus.COMPLETED }
    val inProgressCount: Int get() = steps.count { it.status == TodoStatus.IN_PROGRESS }

    /** Compact section appended to the system prompt. Bounded so it can never bloat the prompt. */
    fun renderSection(): String = buildString {
        append("[ACTIVE PLAN — you committed to these steps. Update progress with update_todo (merge=true), revise with update_todo (merge=false) when blocked.]")
        if (steps.isEmpty()) {
            append("\n(no plan set — for multi-step work, set one first with update_todo)")
            return@buildString
        }
        steps.forEachIndexed { index, step ->
            val mark = when (step.status) {
                TodoStatus.COMPLETED -> "[x]"
                TodoStatus.IN_PROGRESS -> "[>]"
                TodoStatus.PENDING -> "[ ]"
            }
            append("\n").append(index + 1).append(". ").append(mark).append(' ')
                .append(step.content.take(PLAN_STEP_DISPLAY_CHARS))
        }
        append("\nDone ").append(doneCount).append('/').append(steps.size)
    }

    companion object {
        /** Hard cap on steps the host will pin into the prompt. */
        const val MAX_PLAN_STEPS = 8
        private const val PLAN_STEP_DISPLAY_CHARS = 140

        fun from(items: List<TodoItem>): TaskPlan =
            TaskPlan(items.asSequence()
                // Honour the cap by dropping the tail of the todo list, never by rewriting statuses.
                .filter { it.content?.isNotBlank() == true }
                .take(MAX_PLAN_STEPS)
                .map { TaskPlanStep(status = it.status, content = it.content.orEmpty()) }
                .toList())
    }
}

data class TaskPlanStep(
    val status: TodoStatus,
    val content: String
)
