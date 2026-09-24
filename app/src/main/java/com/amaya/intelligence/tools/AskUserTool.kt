package com.amaya.intelligence.tools

import javax.inject.Inject

/**
 * Host-gated clarification tool. The model calls this when the request is ambiguous and the
 * answer changes what it should do. The host suspends the tool loop, shows the question in the
 * chat UI, and resumes with the user's free-text answer as the tool result.
 *
 * Non-destructive by design: it never runs an action and never enters the approval flow, so a
 * stuck agent can ask instead of guessing. A dismissed question returns an error the model can
 * recover from ("proceed with your best assumption or state what is missing").
 */
class AskUserTool @Inject constructor() : ContextAwareTool {
    val name: String = "ask_user"
    val description: String =
        "Ask the user a short question when the request is ambiguous, choices conflict, or a prerequisite is missing. The turn pauses and resumes with the user's answer."

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext
    ): ToolResult {
        val question = (arguments["question"] as? String)?.trim().orEmpty()
        if (question.isBlank()) {
            return ToolResult.Error("ask_user requires a non-blank 'question'.", ErrorType.VALIDATION_ERROR)
        }
        if (question.length > MAX_QUESTION_CHARS) {
            return ToolResult.Error(
                "ask_user question is too long (${question.length} > $MAX_QUESTION_CHARS). Shorten it to one sentence.",
                ErrorType.VALIDATION_ERROR
            )
        }
        val options = (arguments["options"] as? List<*>)?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?.take(MAX_OPTIONS)
            .orEmpty()
        val answer = context.onClarificationRequired(
            ClarificationRequest(
                toolCallId = context.toolCallId.orEmpty(),
                question = question,
                options = options
            )
        )
        val trimmed = answer?.trim().orEmpty()
        return if (trimmed.isNotEmpty()) {
            ToolResult.Success("User answered: $trimmed")
        } else {
            ToolResult.Error(
                "The user dismissed the question. Do not repeat it; proceed with the best available assumption and state the assumption, or explain what is missing and stop.",
                ErrorType.PERMISSION_ERROR,
                recoverable = true
            )
        }
    }

    private companion object {
        const val MAX_QUESTION_CHARS = 200
        const val MAX_OPTIONS = 6
    }
}
