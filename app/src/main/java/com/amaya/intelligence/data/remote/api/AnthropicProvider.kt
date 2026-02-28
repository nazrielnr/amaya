package com.amaya.intelligence.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anthropic (Claude) AI provider.
 * 
 * API Documentation: https://docs.anthropic.com/en/api/messages
 * 
 * KEY FEATURES:
 * - Native tool use support via tool_use content blocks
 * - Streaming via Server-Sent Events
 * - Strong system prompt support
 */
@Singleton
class AnthropicProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsProvider: () -> AiSettings
) : AiProvider {
    
    companion object {
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val API_VERSION = "2023-06-01"
    }
    
    override val name = "Anthropic (Claude)"
    
    // Models are configured per-agent by the user; no hardcoded list needed
    override val supportedModels = emptyList<String>()
    
    override fun isConfigured(): Boolean {
        return settingsProvider().anthropicApiKey.isNotBlank()
    }
    
    override suspend fun chat(request: ChatRequest): Flow<ChatResponse> = callbackFlow {
        val settings = settingsProvider()
        
        if (settings.anthropicApiKey.isBlank()) {
            trySend(ChatResponse.Error("Anthropic API key not configured", "AUTH_ERROR"))
            close()
            return@callbackFlow
        }
        
        // Build request body
        val anthropicRequest = buildAnthropicRequest(request)
        val jsonBody = moshi.adapter(AnthropicRequest::class.java).toJson(anthropicRequest)
        
        val httpRequest = Request.Builder()
            .url("$BASE_URL/messages")
            .addHeader("x-api-key", settings.anthropicApiKey)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("content-type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        if (request.stream) {
            // Streaming request
            val eventSourceFactory = EventSources.createFactory(httpClient)
            
            val listener = object : EventSourceListener() {
                private val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
                private var currentToolIndex = -1
                
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (data == "[DONE]") {
                        trySend(ChatResponse.Done())
                        close()
                        return
                    }
                    
                    try {
                        val event = moshi.adapter(AnthropicStreamEvent::class.java)
                            .fromJson(data) ?: return
                        
                        when (event.type) {
                            "content_block_start" -> {
                                event.contentBlock?.let { block ->
                                    if (block.type == "tool_use") {
                                        currentToolIndex = event.index ?: 0
                                        toolCallBuilders[currentToolIndex] = ToolCallBuilder(
                                            id = block.id ?: "",
                                            name = block.name ?: ""
                                        )
                                    }
                                }
                            }
                            
                            "content_block_delta" -> {
                                event.delta?.let { delta ->
                                    when (delta.type) {
                                        "text_delta" -> {
                                            delta.text?.let { text ->
                                                trySend(ChatResponse.TextDelta(text))
                                            }
                                        }
                                        "input_json_delta" -> {
                                            delta.partialJson?.let { json ->
                                                toolCallBuilders[currentToolIndex]?.appendJson(json)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            "content_block_stop" -> {
                                toolCallBuilders[event.index]?.let { builder ->
                                    val args = parseJsonArgs(builder.jsonBuilder.toString())
                                    trySend(ChatResponse.ToolCall(
                                        id = builder.id,
                                        name = builder.name,
                                        arguments = args
                                    ))
                                }
                            }
                            
                            "message_stop" -> {
                                trySend(ChatResponse.Done())
                                close()
                            }
                            
                            "message_delta" -> {
                                event.usage?.let { usage ->
                                    // Usage info comes at the end
                                }
                            }
                            
                            "error" -> {
                                event.error?.let { error ->
                                    trySend(ChatResponse.Error(
                                        error.message ?: "Unknown error",
                                        error.type
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        trySend(ChatResponse.Error("Failed to parse event: ${e.message}"))
                    }
                }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    val message = t?.message ?: response?.message ?: "Unknown error"
                    trySend(ChatResponse.Error(message, retryable = true))
                    close()
                }
                
                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            }
            
            val eventSource = eventSourceFactory.newEventSource(httpRequest, listener)
            
            awaitClose {
                eventSource.cancel()
            }
        } else {
            // Non-streaming request
            try {
                val response = httpClient.newCall(httpRequest).execute()
                val body = response.body?.string()
                
                if (!response.isSuccessful) {
                    trySend(ChatResponse.Error("API error: ${response.code} - $body"))
                    close()
                    return@callbackFlow
                }
                
                val anthropicResponse = moshi.adapter(AnthropicResponse::class.java)
                    .fromJson(body ?: "")
                
                // Process content blocks
                anthropicResponse?.content?.forEach { block ->
                    when (block.type) {
                        "text" -> {
                            trySend(ChatResponse.TextDelta(block.text ?: ""))
                        }
                        "tool_use" -> {
                            trySend(ChatResponse.ToolCall(
                                id = block.id ?: "",
                                name = block.name ?: "",
                                arguments = block.input ?: emptyMap()
                            ))
                        }
                    }
                }
                
                trySend(ChatResponse.Done(
                    usage = anthropicResponse?.usage?.let {
                        TokenUsage(it.inputTokens, it.outputTokens)
                    }
                ))
                
            } catch (e: Exception) {
                trySend(ChatResponse.Error("Request failed: ${e.message}"))
            }
            
            close()
        }
    }.flowOn(Dispatchers.IO)
    
    private fun buildAnthropicRequest(request: ChatRequest): AnthropicRequest {
        val messages = request.messages.mapNotNull { msg ->
            when (msg.role) {
                MessageRole.USER -> AnthropicMessage(
                    role = "user",
                    content = listOf(AnthropicContentBlock(type = "text", text = msg.content))
                )
                MessageRole.ASSISTANT -> {
                    val content = mutableListOf<AnthropicContentBlock>()
                    msg.content?.let { content.add(AnthropicContentBlock(type = "text", text = it)) }
                    msg.toolCalls?.forEach { call ->
                        content.add(AnthropicContentBlock(
                            type = "tool_use",
                            id = call.id,
                            name = call.name,
                            input = call.arguments
                        ))
                    }
                    AnthropicMessage(role = "assistant", content = content)
                }
                MessageRole.TOOL -> {
                    msg.toolResult?.let { result ->
                        AnthropicMessage(
                            role = "user",
                            content = listOf(AnthropicContentBlock(
                                type = "tool_result",
                                toolUseId = result.toolCallId,
                                content = result.content,
                                isError = result.isError
                            ))
                        )
                    }
                }
                MessageRole.SYSTEM -> null // Handled separately
            }
        }
        
        val tools = request.tools.map { tool ->
            AnthropicTool(
                name = tool.name,
                description = tool.description,
                inputSchema = AnthropicInputSchema(
                    type = "object",
                    properties = tool.parameters.properties.mapValues { (_, prop) ->
                        AnthropicProperty(
                            type = prop.type,
                            description = prop.description,
                            enum = prop.enum
                        )
                    },
                    required = tool.parameters.required
                )
            )
        }
        
        return AnthropicRequest(
            model = request.model,
            messages = messages,
            system = request.systemPrompt,
            tools = tools.takeIf { it.isNotEmpty() },
            maxTokens = request.maxTokens,
            stream = request.stream
        )
    }
    
    private fun parseJsonArgs(json: String): Map<String, Any?> {
        return try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            moshi.adapter<Map<String, Any?>>(type).fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private class ToolCallBuilder(val id: String, val name: String) {
        val jsonBuilder = StringBuilder()
        fun appendJson(json: String) { jsonBuilder.append(json) }
    }
}

// ============================================================================
// ANTHROPIC API DATA CLASSES
// ============================================================================

@JsonClass(generateAdapter = true)
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val tools: List<AnthropicTool>? = null,
    @Json(name = "max_tokens") val maxTokens: Int = 8192,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContentBlock>
)

@JsonClass(generateAdapter = true)
data class AnthropicContentBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any?>? = null,
    @Json(name = "tool_use_id") val toolUseId: String? = null,
    val content: String? = null,
    @Json(name = "is_error") val isError: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class AnthropicTool(
    val name: String,
    val description: String,
    @Json(name = "input_schema") val inputSchema: AnthropicInputSchema
)

@JsonClass(generateAdapter = true)
data class AnthropicInputSchema(
    val type: String = "object",
    val properties: Map<String, AnthropicProperty>,
    val required: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AnthropicProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContentBlock>,
    val usage: AnthropicUsage?
)

@JsonClass(generateAdapter = true)
data class AnthropicUsage(
    @Json(name = "input_tokens") val inputTokens: Int,
    @Json(name = "output_tokens") val outputTokens: Int
)

@JsonClass(generateAdapter = true)
data class AnthropicStreamEvent(
    val type: String,
    val index: Int? = null,
    @Json(name = "content_block") val contentBlock: AnthropicContentBlock? = null,
    val delta: AnthropicDelta? = null,
    val usage: AnthropicUsage? = null,
    val error: AnthropicError? = null
)

@JsonClass(generateAdapter = true)
data class AnthropicDelta(
    val type: String? = null,
    val text: String? = null,
    @Json(name = "partial_json") val partialJson: String? = null
)

@JsonClass(generateAdapter = true)
data class AnthropicError(
    val type: String? = null,
    val message: String? = null
)
