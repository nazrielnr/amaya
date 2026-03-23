package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.domain.models.ToolCategory
import com.amaya.intelligence.domain.models.ToolExecution
import com.amaya.intelligence.domain.models.ToolInfoIcon

internal fun ToolExecution.isSyntheticThinkingCard(): Boolean {
    if (uiMetadata?.badges?.any { it.equals("THINKING", ignoreCase = true) } == true) return true
    if (uiMetadata?.actionIcon == ToolInfoIcon.BRAIN) return true
    return uiMetadata?.label.equals("Thinking", ignoreCase = true)
}

internal fun ToolExecution.isShellTool(): Boolean {
    return uiMetadata?.category == ToolCategory.SHELL
}

internal fun ToolExecution.isTaskBoundaryTool(): Boolean {
    return arguments.containsKey("taskStatus") || arguments.containsKey("TaskStatus")
}

internal fun ToolExecution.hasCanonicalFileDiff(): Boolean {
    if (uiMetadata?.category != ToolCategory.FILE_IO) return false
    return arguments.containsKey("targetContent") ||
        arguments.containsKey("TargetContent") ||
        arguments.containsKey("replacementContent") ||
        arguments.containsKey("ReplacementContent") ||
        arguments.containsKey("CodeContent") ||
        arguments.containsKey("codeContent") ||
        arguments.containsKey("replacementChunks") ||
        arguments.containsKey("ReplacementChunks")
}

    internal fun Map<String, Any?>.hasCanonicalFileDiff(): Boolean {
        return containsKey("targetContent") ||
        containsKey("TargetContent") ||
        containsKey("replacementContent") ||
        containsKey("ReplacementContent") ||
        containsKey("CodeContent") ||
        containsKey("codeContent") ||
        containsKey("replacementChunks") ||
        containsKey("ReplacementChunks")
    }

