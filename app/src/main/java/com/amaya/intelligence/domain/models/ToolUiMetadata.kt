package com.amaya.intelligence.domain.models

/**
 * Agnostic categories for AI tools to help UI decide on general styling.
 */
enum class ToolCategory {
    FILE_IO,
    SHELL,
    SEARCH,
    TASK_MANAGEMENT,
    MEMORY,
    WEB,
    SYSTEM,
    UNKNOWN
}

/**
 * Standardized icons for the UI to render, mapped from implementation layer.
 */
enum class ToolInfoIcon {
    READ, WRITE, EDIT, DELETE, LIST, FIND, 
    TASK, RUN, CHECK, SEARCH, WEB_READ, 
    MESSAGE, GENERATE, BROWSER, DOCS, CHUNK,
    FILE, FOLDER, COMMAND, TERMINAL, WORLD, LINK, PERSON, IMAGE, MOUSE, BOOK, ROCKET, BRAIN
}

/**
 * Pure metadata that tells the UI EXACTLY what to show without the UI
 * needing to know which tool is being executed.
 */
data class ToolUiMetadata(
    val category: ToolCategory,
    val label: String,
    val actionIcon: ToolInfoIcon,
    val targetIcon: ToolInfoIcon,
    val badges: List<String> = emptyList(),
    val isHidden: Boolean = false
)
