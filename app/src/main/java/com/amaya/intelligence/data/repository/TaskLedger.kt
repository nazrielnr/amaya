package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.MessageRole

/**
 * Durable state for a session whose older turns have left the context window.
 *
 * This replaces summary-of-summary compaction. The previous design fed each summary back into the
 * next summarization prompt, so every round was a lossy re-encode of an already lossy encode and
 * the user's original request degraded until it was unrecoverable. Here [goal] is copied verbatim
 * from the first user message and is owned by the app — it never enters the model's output space,
 * so it is byte-identical no matter how many compaction rounds run.
 */
internal data class TaskLedger(
    val goal: String,
    val constraints: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val filesTouched: List<String> = emptyList(),
    val checkpoints: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val lastState: String = "",
    val evictedMessages: Int = 0,
    val evictedToolResults: Int = 0
) {
    fun render(): String = buildString {
        appendLine("GOAL: ${goal.take(MAX_GOAL_CHARS)}")
        appendSection("CONSTRAINTS", constraints)
        appendSection("DECISIONS", decisions)
        appendSection("CHECKPOINTS", checkpoints)
        appendSection("FILES TOUCHED", filesTouched)
        appendSection("OPEN QUESTIONS", openQuestions)
        if (lastState.isNotBlank()) appendLine("LAST STATE: $lastState")
        if (evictedMessages > 0 || evictedToolResults > 0) {
            appendLine("EVICTED: $evictedMessages messages, $evictedToolResults tool results (ask the user or use session_search if exact old details matter).")
        }
    }.trim()

    /**
     * Fold a model-produced delta in. List sections are appended and de-duplicated; existing
     * entries are never rewritten or reordered, and [goal] is always carried through untouched.
     */
    fun mergedWith(delta: LedgerDelta, newlyEvicted: Int, newlyEvictedToolResults: Int): TaskLedger = copy(
        constraints = (constraints + delta.constraints).cleaned(),
        decisions = (decisions + delta.decisions).cleaned(),
        checkpoints = (checkpoints + delta.checkpoints).cleaned(),
        filesTouched = (filesTouched + delta.filesTouched).cleaned(),
        // An open question leaves the list only by being answered into DECISIONS.
        openQuestions = (openQuestions + delta.openQuestions)
            .cleaned()
            .filterNot { question -> delta.decisions.any { it.contains(question, ignoreCase = true) } },
        lastState = delta.lastState.ifBlank { lastState },
        evictedMessages = evictedMessages + newlyEvicted,
        evictedToolResults = evictedToolResults + newlyEvictedToolResults
    )

    private fun StringBuilder.appendSection(title: String, entries: List<String>) {
        if (entries.isEmpty()) return
        appendLine("$title:")
        entries.forEach { appendLine("- $it") }
    }

    private fun List<String>.cleaned(): List<String> = asSequence()
        .map { it.trim().removePrefix("- ").trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .toList()
        .takeLast(MAX_ENTRIES_PER_SECTION)

    companion object {
        private const val MAX_GOAL_CHARS = 600
        private const val MAX_ENTRIES_PER_SECTION = 24
    }
}

/** Sections the model is allowed to change. GOAL is deliberately absent. */
internal data class LedgerDelta(
    val constraints: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val checkpoints: List<String> = emptyList(),
    val filesTouched: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val lastState: String = ""
) {
    val isEmpty: Boolean
        get() = constraints.isEmpty() && decisions.isEmpty() && checkpoints.isEmpty() && filesTouched.isEmpty() &&
            openQuestions.isEmpty() && lastState.isBlank()
}

private val LEDGER_HEADING = Regex("(?m)^\\s{0,3}#{1,4}\\s*([A-Z][A-Z ]{2,30}?)\\s*:?\\s*$")

/** Parse the model's section-delta output. Unknown headings are ignored rather than guessed at. */
internal fun parseLedgerDelta(markdown: String): LedgerDelta {
    if (markdown.isBlank()) return LedgerDelta()
    val sections = linkedMapOf<String, StringBuilder>()
    var current: StringBuilder? = null
    markdown.lineSequence().forEach { line ->
        val heading = LEDGER_HEADING.find(line)?.groupValues?.getOrNull(1)?.trim()?.uppercase()
        if (heading != null) {
            current = sections.getOrPut(heading) { StringBuilder() }
        } else {
            current?.appendLine(line)
        }
    }
    fun bullets(name: String): List<String> = sections[name]?.toString()
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.startsWith("- ") || it.startsWith("* ") }
        ?.map { it.removeRange(0, 2).trim() }
        ?.filter { it.isNotBlank() }
        ?.toList()
        .orEmpty()

    return LedgerDelta(
        constraints = bullets("CONSTRAINTS"),
        decisions = bullets("DECISIONS"),
        checkpoints = bullets("CHECKPOINTS"),
        filesTouched = bullets("FILES TOUCHED"),
        openQuestions = bullets("OPEN QUESTIONS"),
        lastState = sections["LAST STATE"]?.toString()?.trim()?.lines()
            ?.filterNot { it.trim().startsWith("- ") }
            ?.joinToString(" ")
            ?.trim()
            .orEmpty()
    )
}

private val RENDERED_SECTIONS = listOf("CONSTRAINTS", "DECISIONS", "CHECKPOINTS", "FILES TOUCHED", "OPEN QUESTIONS")
private val EVICTED_COUNTS = Regex("EVICTED:\\s*(\\d+)\\s*messages?,\\s*(\\d+)\\s*tool results?")

/**
 * Read back a ledger previously produced by [TaskLedger.render].
 *
 * The rendered form is the only copy that survives a process restart — it rides along in the
 * conversation's `[AUTO-COMPACTED ACTIVE CONTEXT]` message. Without this, a cold start would treat
 * the accumulated constraints, decisions and open questions as plain prompt filler and the first
 * compaction of the new session would overwrite them with a ledger holding only the goal.
 */
internal fun parseRenderedLedger(text: String, fallbackGoal: String): TaskLedger? {
    if (text.isBlank()) return null
    val lines = text.lines()
    val goal = lines.firstOrNull { it.startsWith("GOAL:") }?.removePrefix("GOAL:")?.trim()
    val lastState = lines.firstOrNull { it.startsWith("LAST STATE:") }?.removePrefix("LAST STATE:")?.trim()
    val counts = EVICTED_COUNTS.find(text)

    // Bullet scanning stops at LAST STATE. Its text is free prose that may itself contain "- "
    // lines, and letting those fall through would inject them into whichever section was open.
    val bulletLines = lines.indexOfFirst { it.startsWith("LAST STATE:") }
        .takeIf { it >= 0 }
        ?.let { lines.subList(0, it) }
        ?: lines
    val bullets = linkedMapOf<String, MutableList<String>>()
    var current: MutableList<String>? = null
    bulletLines.forEach { line ->
        val heading = RENDERED_SECTIONS.firstOrNull { line.trim() == "$it:" }
        when {
            heading != null -> current = bullets.getOrPut(heading) { mutableListOf() }
            line.startsWith("- ") -> current?.add(line.removePrefix("- ").trim())
            line.isBlank() -> Unit
            else -> current = null
        }
    }
    val ledger = TaskLedger(
        goal = goal?.takeIf(String::isNotBlank) ?: fallbackGoal,
        constraints = bullets["CONSTRAINTS"].orEmpty(),
        decisions = bullets["DECISIONS"].orEmpty(),
        checkpoints = bullets["CHECKPOINTS"].orEmpty(),
        filesTouched = bullets["FILES TOUCHED"].orEmpty(),
        openQuestions = bullets["OPEN QUESTIONS"].orEmpty(),
        lastState = lastState.orEmpty(),
        evictedMessages = counts?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
        evictedToolResults = counts?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    )
    return ledger.takeIf { goal != null || bullets.isNotEmpty() || !lastState.isNullOrBlank() }
}

private val PATH_ARGUMENT_KEYS = setOf("path", "file_path", "filepath", "file", "paths", "target", "directory")

/**
 * Deterministic ledger update that needs no model call.
 *
 * Used when the summarizer is unavailable, times out, or returns nothing. Committing a lossy
 * context plan with no record at all is never acceptable — that is how evicted turns used to
 * disappear silently.
 */
internal fun mechanicalLedger(
    current: TaskLedger?,
    goal: String,
    evicted: List<ChatMessage>,
    evictedToolResults: Int
): TaskLedger {
    val base = current ?: TaskLedger(goal = goal)
    val files = evicted
        .flatMap { it.toolCalls.orEmpty() }
        .flatMap { call ->
            call.arguments
                .filterKeys { it.lowercase() in PATH_ARGUMENT_KEYS }
                .values
                .mapNotNull { value -> (value as? String)?.takeIf(String::isNotBlank) }
        }
    val lastAssistant = evicted.lastOrNull { it.role == MessageRole.ASSISTANT && !it.content.isNullOrBlank() }
        ?.content
        ?.trim()
        ?.take(300)
    val lastTool = evicted.lastOrNull { it.role == MessageRole.TOOL && it.toolResult != null }?.toolResult
    val lastState = listOfNotNull(
        lastAssistant?.let { "Last assistant output before eviction: $it" },
        lastTool?.let { "Last tool result: ${if (it.isError) "error" else "ok"} — ${it.content.trim().take(200)}" }
    ).joinToString(" | ").ifBlank { base.lastState }

    return base.copy(
        goal = base.goal.ifBlank { goal },
        filesTouched = (base.filesTouched + files).distinctBy { it.lowercase() }.takeLast(24),
        lastState = lastState,
        evictedMessages = base.evictedMessages + evicted.size,
        evictedToolResults = base.evictedToolResults + evictedToolResults
    )
}

/**
 * Render evicted activity newest-first.
 *
 * The old transcript builder concatenated everything oldest-first and then took the head, so an
 * overflowing eviction discarded the most recent activity — backwards for a summary whose job is
 * to describe current state and next steps.
 */
internal fun evictionTranscript(evicted: List<ChatMessage>, maxChars: Int, perMessageCap: Int = 4_000): String {
    if (maxChars <= 0) return ""
    val chunks = ArrayDeque<String>()
    var used = 0
    for (message in evicted.asReversed()) {
        val chunk = buildString {
            appendLine("${message.role}:")
            message.content?.takeIf(String::isNotBlank)?.let { appendLine(it.take(perMessageCap)) }
            message.toolCalls.orEmpty().forEach { call ->
                appendLine("tool_call ${call.name}: ${call.arguments.toString().take(perMessageCap)}")
            }
            message.toolResult?.let { result ->
                appendLine("tool_result ${result.toolCallId}${if (result.isError) " (error)" else ""}: ${result.content.take(perMessageCap)}")
            }
        }
        if (used + chunk.length > maxChars) {
            chunks.addFirst("… [older evicted activity elided]\n")
            break
        }
        chunks.addFirst(chunk)
        used += chunk.length
    }
    return chunks.joinToString("").trim()
}

/** The original request, copied verbatim. Never model-generated. */
internal fun conversationGoal(messages: List<ChatMessage>, fallback: String): String =
    messages.firstOrNull {
        it.role == MessageRole.USER && !it.content.isNullOrBlank() && it.content != STREAM_CONTINUATION_PROMPT
    }?.content?.trim()?.takeIf(String::isNotBlank) ?: fallback.trim()
