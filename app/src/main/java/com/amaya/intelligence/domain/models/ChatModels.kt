package com.amaya.intelligence.domain.models

import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.tools.TodoItem
import java.util.UUID

// ── UI State ─────────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages:         List<UiMessage> = emptyList(),
    val isLoading:        Boolean         = false,
    val isStreaming:      Boolean         = false,
    val error:            String?         = null,
    val selectedModel:    String          = "",
    val availableModels:  List<ModelInfo> = emptyList(),
    val activeProjectId:  Long?           = null,
    val workspacePath:    String?         = null,
    val totalInputTokens:  Int            = 0,
    val totalOutputTokens: Int            = 0,
    val isLoadingConversations: Boolean   = false,
    val agentConfigs:  List<AgentSelectorItem> = emptyList(),
    val activeAgentId: String             = "",
    val conversationId: String?           = null,
    val conversationMode: ConversationMode = ConversationMode.PLANNING,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sessionMode: com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode = com.amaya.intelligence.domain.ai.IntelligenceSessionManager.SessionMode.LOCAL,
    val serverIp: String? = null
)

data class ModelInfo(
    val id: String,
    val label: String,
    val isRecommended: Boolean = false,
    val quota: Double = 0.0,
    val quotaLabel: String? = null,
    val resetTime: String? = null,
    val tagTitle: String? = null,
    val supportsImages: Boolean = false
)

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
    
    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val content: String,
        val formattedContent: String? = null
    ): MessageStep()
    
    data class ToolCall(
        override val id: String = UUID.randomUUID().toString(),
        val execution: ToolExecution
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
    val intent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val toolExecutions: List<ToolExecution> = emptyList(),
    val steps: List<MessageStep> = emptyList(),
    val todoItems: List<TodoItem> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val attachments: List<MessageAttachment> = emptyList()
)

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

data class AgentSelectorItem(
    val id: String,
    val name: String,
    val modelId: String,
    val tagTitle: String? = null,
    val quotaStr: String? = null,
    val quotaLabel: String? = null,
    val resetTime: String? = null,
    val isRemote: Boolean = false,
    val iconType: String = "default" // "gpt", "gemini", "claude", "default"
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
