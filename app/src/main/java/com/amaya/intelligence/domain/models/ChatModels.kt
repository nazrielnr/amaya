package com.amaya.intelligence.domain.models


import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.remote.api.ThinkingEffort
import com.amaya.intelligence.tools.TodoItem
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

// ── UI State ─────────────────────────────────────────────────────────────────

enum class SessionPhase { STARTING, THINKING, STREAMING, TOOL, COMPACTING, DELEGATING, WAITING_APPROVAL, COMPLETED, FAILED, STOPPED }

enum class ConversationEventType(val wireName: String, val defaultLabel: String) {
    COMPACTION("compaction", "Compacted"),
    DELEGATION_COMPLETED("delegation_completed", "Delegation")
}

enum class ConversationEventState(val wireName: String, val displayLabel: String) {
    DONE("done", "done"),
    FAILED("failed", "failed")
}

data class ConversationEvent(
    val type: ConversationEventType,
    val label: String,
    val state: ConversationEventState,
    val detail: String,
    val metadata: Map<String, String> = emptyMap()
) {
    val displayLabel: String get() = "--- $label ${state.displayLabel} ---"
}

const val CONVERSATION_EVENT_TYPE_KEY = "eventType"
const val CONVERSATION_EVENT_LABEL_KEY = "eventLabel"
const val CONVERSATION_EVENT_STATE_KEY = "eventState"

fun conversationEventMessage(
    type: ConversationEventType,
    label: String,
    state: ConversationEventState = ConversationEventState.DONE,
    detail: String = "",
    timestamp: Long = System.currentTimeMillis(),
    metadata: Map<String, String> = emptyMap(),
    role: MessageRole = MessageRole.SYSTEM
): UiMessage = UiMessage(
    role = role,
    content = "--- $label ${state.displayLabel} ---",
    timestamp = timestamp,
    metadata = metadata + mapOf(
        "eventDetail" to detail,
        CONVERSATION_EVENT_TYPE_KEY to type.wireName,
        CONVERSATION_EVENT_LABEL_KEY to label,
        CONVERSATION_EVENT_STATE_KEY to state.wireName
    )
)

fun UiMessage.conversationEvent(): ConversationEvent? {
    val type = ConversationEventType.entries.firstOrNull { it.wireName == metadata[CONVERSATION_EVENT_TYPE_KEY] } ?: return null
    val state = ConversationEventState.entries.firstOrNull { it.wireName == metadata[CONVERSATION_EVENT_STATE_KEY] }
        ?: ConversationEventState.DONE
    return ConversationEvent(
        type,
        metadata[CONVERSATION_EVENT_LABEL_KEY].orEmpty().ifBlank { type.defaultLabel },
        state,
        metadata["eventDetail"].orEmpty().ifBlank { content.takeUnless { it.trim().startsWith("---") }.orEmpty() },
        metadata
    )
}

fun UiMessage.conversationEventProviderContent(): String? {
    val event = conversationEvent() ?: return null
    return buildString {
        append(event.displayLabel)
        metadata["sourceAgentName"]?.takeIf(String::isNotBlank)?.let { append("\nSource Agent: ").append(it) }
        metadata["targetAgentName"]?.takeIf(String::isNotBlank)?.let { append("\nDelegated Agent: ").append(it) }
        if (event.type == ConversationEventType.DELEGATION_COMPLETED) {
            append("\nThis is the final delivered result for this delegation. Use it directly; do not delegate this task again.")
        }
        event.detail.takeIf(String::isNotBlank)?.let {
            append("\nResult detail:\n").append(it)
        }
    }
}

data class RunningSession(
    val conversationId: Long,
    val title: String,
    val mode: AssistantMode,
    val ownerId: String?,
    val agentId: Long?,
    val status: String,
    val detail: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDelegating: Boolean = false,
    val approvalId: String? = null,
    val approvalLabel: String? = null,
    val approvalRisk: String? = null,
    val phase: SessionPhase = SessionPhase.STARTING,
    val latestAssistantMessage: String = "",
    val completedDelegates: Int = 0,
    val totalDelegates: Int = 0,
    val activeDelegateName: String? = null,
    val notificationTitle: String = title,
    val notificationSender: String = "AI",
    val notificationThreadKey: String = "chat:$conversationId"
)

data class ChatUiState(
    val messages:         List<UiMessage> = emptyList(),
    /** Model-visible state, retained when the user clears only rendered history. */
    val contextMessages:  List<UiMessage> = emptyList(),
    val isLoading:        Boolean         = false,
    val isLoadingHistory: Boolean         = false,
    val isStreaming:      Boolean         = false,
    val isCompressing:    Boolean         = false,
    /**
     * Host-driven context compaction during a turn. Kept separate from [isCompressing] because the
     * send button doubles as "cancel compression" for the user-initiated path, and there is no
     * cancellable job behind an automatic compaction.
     */
    val isAutoCompacting: Boolean         = false,
    val error:            String?         = null,
    val selectedModel:    String          = "",
    val activeProjectId:  Long?           = null,
    val workspacePath:    String?         = null,
    val assistantMode:    AssistantMode   = AssistantMode.CHAT,
    val ownerId:          String?         = null,
    val agentId:          Long?           = null,
    val totalInputTokens:  Int            = 0,
    val totalOutputTokens: Int            = 0,
    val isLoadingConversations: Boolean   = false,
    val modelOptions:  List<ModelOption> = emptyList(),
    val activeModelKey: String            = "",
    val conversationId: String?           = null,
    val conversationMode: ConversationMode = ConversationMode.PLANNING,
    val conversationModeId: String?        = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sessionMode: com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode = com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode.LOCAL,
    val serverIp: String? = null,
    /** Global reasoning effort from the chat bulb. */
    val effort: ThinkingEffort = ThinkingEffort.MEDIUM
)



data class ComposerReferences(
    val agentIds: List<Long> = emptyList(),
    val workspacePaths: List<String> = emptyList(),
    val commands: List<String> = emptyList()
)

/** Agent reference ID is group-local; database IDs never enter model-owned Markdown. */
fun agentMentionMarkdown(agentId: Long, name: String): String =
    "[@${name.replace("[", "").replace("]", "")}](agent:$agentId)"

fun workspaceMentionMarkdown(path: String): String {
    val normalized = path.replace('\\', '/')
    val label = normalized.substringAfterLast('/').ifBlank { normalized }
    return "[@${label.replace("[", "").replace("]", "")}](workspace:${encodeComposerReference(normalized)})"
}

fun commandMarkdown(command: String): String {
    val normalized = command.trim().removePrefix("/")
    return "[/$normalized](command:$normalized)"
}

fun parseComposerReferences(text: String): ComposerReferences = ComposerReferences(
    agentIds = AGENT_REFERENCE.findAll(text).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList(),
    workspacePaths = WORKSPACE_REFERENCE.findAll(text).mapNotNull { decodeComposerReference(it.groupValues[1]) }.distinct().toList(),
    commands = COMMAND_REFERENCE.findAll(text).map { it.groupValues[1] }.distinct().toList()
)

private fun encodeComposerReference(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("%2F", "/")

private fun decodeComposerReference(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrNull()?.takeIf(String::isNotBlank)

private val AGENT_REFERENCE = Regex("""\]\(agent:(\d+)\)""")
private val WORKSPACE_REFERENCE = Regex("""\]\(workspace:([^)]+)\)""")
private val COMMAND_REFERENCE = Regex("""\]\(command:([a-zA-Z0-9_-]+)\)""")

enum class ConversationMode(val wireValue: String) {
    PLANNING("planning"),
    FAST("fast");

    companion object {
        fun fromWireValue(value: String?): ConversationMode {
            return if (value.equals("fast", ignoreCase = true)) FAST else PLANNING
        }
    }
}

// ── Message Timeline ───────────────────────────────────────────────────────────

sealed class MessageStep {
    abstract val id: String

    data class Thinking(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val isStreaming: Boolean = true,
        val startedAt: Long? = null,
        val durationMs: Long? = null
    ): MessageStep()

    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val content: String,
        val formattedContent: String? = null
    ): MessageStep()

    data class ToolCall(
        override val id: String = UUID.randomUUID().toString(),
        val execution: ToolExecution
    ): MessageStep()

    /** A non-provider timeline marker shown inside the live work summary. */
    data class Event(
        override val id: String = UUID.randomUUID().toString(),
        val event: ConversationEvent
    ): MessageStep()
}

// ── Message ──────────────────────────────────────────────────────────────────

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val formattedContent: String? = null, // Pre-formatted (e.g., Markdown or code blocks)
    val thinking: String? = null,
    val isThinking: Boolean = false,
    val thinkingStartedAt: Long? = null,
    /** Thinking duration in ms, captured when the turn ends so the
     *  "Thought for Xs" label survives reloads without recomputing from
     *  startedAt/completedAt (which may be absent for remote/inline tags). */
    val thinkingDurationMs: Long? = null,
    val intent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val toolExecutions: List<ToolExecution> = emptyList(),
    val steps: List<MessageStep> = emptyList(),
    val todoItems: List<TodoItem> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<MessageAttachment> = emptyList(),
    val responseItems: List<String> = emptyList(),
    /** Ordered provider/tool history. UI fields remain a projection of these items. */
    val canonicalHistory: List<String> = emptyList()
)

val UiMessage.isEdited: Boolean
    get() = metadata["isEdited"] == "true"

val UiMessage.editedAt: Long?
    get() = metadata["editedAt"]?.toLongOrNull()

internal fun List<MessageStep>.appendThinking(delta: String, nowMs: Long = System.currentTimeMillis()): List<MessageStep> {
    val current = lastOrNull() as? MessageStep.Thinking
    if (current != null) {
        return dropLast(1) + current.copy(text = current.text + delta, isStreaming = true)
    }
    return finishThinking(nowMs) + MessageStep.Thinking(text = delta, startedAt = nowMs)
}

internal fun List<MessageStep>.appendText(delta: String): List<MessageStep> {
    val current = lastOrNull() as? MessageStep.Text
    return if (current == null) this + MessageStep.Text(content = delta)
    else dropLast(1) + current.copy(content = current.content + delta)
}

internal fun List<MessageStep>.finishThinking(nowMs: Long = System.currentTimeMillis()): List<MessageStep> {
    val index = indexOfLast { it is MessageStep.Thinking && it.isStreaming }
    if (index < 0) return this
    val current = this[index] as MessageStep.Thinking
    return toMutableList().apply {
        this[index] = current.copy(
            isStreaming = false,
            durationMs = (nowMs - (current.startedAt ?: nowMs)).coerceAtLeast(0L)
        )
    }
}

data class MessageAttachment(
    val mimeType: String,
    val dataBase64: String,
    val fileName: String = ""
)

// ── Tool Execution ───────────────────────────────────────────────────────────

data class ToolExecution(
    val toolCallId: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING,
    val children: List<SubagentExecution> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val uiMetadata: ToolUiMetadata? = null // Metadata for "Dumb UI" rendering
)

data class SubagentExecution(
    val index: Int,
    val taskName: String,
    val prompt: String,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING
)

enum class ToolStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR
}

data class ModelOption(
    val id: String,
    val name: String,
    val modelId: String,
    val connectionId: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val tagTitle: String? = null,
    val quotaLabel: String? = null,
    val resetTime: String? = null,
    val isRemote: Boolean = false,
    val iconType: String = "default",
    val supportsImages: Boolean = false
)



// ── Workspace & Projects ──────────────────────────────────────────────────

data class ProjectFileEntry(
    val name: String,
    val path: String,
    val type: String, // "file" or "directory"
    val size: Long
)

data class RemoteWorkspace(
    val name: String,
    val path: String,
    val isCurrent: Boolean
)
