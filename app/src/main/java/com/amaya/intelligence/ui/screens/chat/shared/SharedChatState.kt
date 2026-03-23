package com.amaya.intelligence.ui.screens.chat.shared

import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.domain.models.AgentSelectorItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared state for both local and remote chat modes.
 * Contains common data needed by all chat sessions.
 */
data class SharedChatState(
    val messages: List<UiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val activeAgentId: String? = null,
    val agentConfigs: List<AgentSelectorItem> = emptyList(),
    val selectedModel: String = "",
    val error: String? = null,
    val workspacePath: String? = null
)

/**
 * Actions available in shared chat context.
 */
interface SharedChatActions {
    fun sendMessage(content: String)
    fun stopGeneration()
    fun clearConversation()
    fun setSelectedAgent(agentId: String)
    fun clearError()
}
