package com.amaya.intelligence.data.remote.api

import kotlinx.coroutines.flow.Flow

/**
 * Unified interface for AI providers.
 * 
 * All AI providers (Anthropic, OpenAI, Gemini) implement this interface
 * to allow seamless switching between providers.
 */
interface AiProvider {
    
    /**
     * Provider name for display and logging.
     */
    val name: String
    
    /**
     * List of supported models for this provider.
     */
    val supportedModels: List<String>
    
    /**
     * Send a chat request and receive streaming responses.
     * 
     * @param request The chat request with messages, tools, and settings
     * @return Flow of chat responses (text deltas, tool calls, or done signals)
     */
    suspend fun chat(request: ChatRequest): Flow<ChatResponse>
    
    /**
     * Check if the provider is properly configured (API key set, etc.)
     */
    fun isConfigured(): Boolean
}

/**
 * Chat request to an AI provider.
 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val systemPrompt: String? = null,
    val tools: List<AiToolDefinition> = emptyList(),
    val maxTokens: Int = 8192,
    val temperature: Float = 0.7f,
    val stream: Boolean = true
)

/**
 * A message in the conversation.
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCallMessage>? = null,
    val toolResult: ToolResultMessage? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * A tool call made by the assistant.
 */
data class ToolCallMessage(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>,
    val metadata: Map<String, String> = emptyMap() // For provider-specific data
)

/**
 * Result of a tool execution.
 */
data class ToolResultMessage(
    val toolCallId: String,
    val content: String,
    val isError: Boolean = false,
    val metadata: Map<String, String> = emptyMap() // For provider-specific data
)

/**
 * Streaming response from an AI provider.
 */
sealed class ChatResponse {
    
    /**
     * A text chunk from the assistant's response.
     */
    data class TextDelta(val text: String) : ChatResponse()
    
    /**
     * The assistant wants to call a tool.
     */
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: Map<String, Any?>,
        val metadata: Map<String, String> = emptyMap() // For provider-specific data like thoughtSignature
    ) : ChatResponse()
    
    /**
     * The response is complete.
     */
    data class Done(
        val usage: TokenUsage? = null,
        val finishReason: String? = null
    ) : ChatResponse()
    
    /**
     * An error occurred.
     */
    data class Error(
        val message: String,
        val code: String? = null,
        val retryable: Boolean = false
    ) : ChatResponse()
}

/**
 * Token usage statistics.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/**
 * Tool definition in provider-agnostic format.
 */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: AiToolParameters
)

data class AiToolParameters(
    val type: String = "object",
    val properties: Map<String, AiToolProperty>,
    val required: List<String> = emptyList()
)

data class AiToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: Any? = null,
    val items: AiToolPropertyItems? = null  // For array types
)

data class AiToolPropertyItems(
    val type: String = "string"
)
