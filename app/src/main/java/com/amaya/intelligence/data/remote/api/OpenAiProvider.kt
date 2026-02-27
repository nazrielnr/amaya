package com.amaya.intelligence.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI-compatible AI provider.
 * 
 * This provider works with:
 * - OpenAI API (api.openai.com)
 * - Local models via Ollama, LM Studio, etc.
 * - Any OpenAI-compatible endpoint
 * 
 * The base URL is configurable to allow switching between providers.
 */
@Singleton
class OpenAiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsProvider: () -> AiSettings
) : AiProvider {
    
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
    
    override val name = "OpenAI Compatible"
    
    // Models are configured per-agent by the user; no hardcoded list needed
    override val supportedModels = emptyList<String>()
    
    override fun isConfigured(): Boolean {
        val settings = settingsProvider()
        return settings.openaiApiKey.isNotBlank() || settings.openaiBaseUrl != DEFAULT_BASE_URL
    }
    
    override suspend fun chat(request: ChatRequest): Flow<ChatResponse> = callbackFlow {
        val settings = settingsProvider()
        val baseUrl = settings.openaiBaseUrl.ifBlank { DEFAULT_BASE_URL }
        
        // Build request body
        val openaiRequest = buildOpenAiRequest(request)
        val jsonBody = moshi.adapter(OpenAiRequest::class.java).toJson(openaiRequest)
        
        val httpRequestBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
        
        // Add auth header if API key is set
        if (settings.openaiApiKey.isNotBlank()) {
            httpRequestBuilder.addHeader("Authorization", "Bearer ${settings.openaiApiKey}")
        }
        
        val httpRequest = httpRequestBuilder.build()
        
        if (request.stream) {
            // Streaming request
            val eventSourceFactory = EventSources.createFactory(httpClient)
            
            val listener = object : EventSourceListener() {
                private val toolCallBuilders = mutableMapOf<Int, OpenAiToolCallBuilder>()
                private var receivedDone = false
                
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (data == "[DONE]") {
                        receivedDone = true
                        // Tool calls are already emitted on finish_reason="tool_calls"
                        // Only emit Done here
                        trySend(ChatResponse.Done())
                        close()
                        return
                    }
                    
                    try {
                        val chunk = moshi.adapter(OpenAiStreamChunk::class.java)
                            .fromJson(data) ?: return
                        
                        chunk.choices.firstOrNull()?.let { choice ->
                            // Handle text delta
                            choice.delta.content?.let { content ->
                                trySend(ChatResponse.TextDelta(content))
                            }
                            
                            // Handle tool calls
                            choice.delta.toolCalls?.forEach { toolCall ->
                                val index = toolCall.index ?: 0
                                
                                if (toolCall.id != null) {
                                    // New tool call starting
                                    toolCallBuilders[index] = OpenAiToolCallBuilder(
                                        id = toolCall.id,
                                        name = toolCall.function?.name ?: ""
                                    )
                                }
                                
                                // Append arguments
                                toolCallBuilders[index]?.let { builder ->
                                    toolCall.function?.name?.let { name ->
                                        if (name.isNotEmpty()) builder.name = name
                                    }
                                    toolCall.function?.arguments?.let { args ->
                                        builder.argumentsBuilder.append(args)
                                    }
                                }
                            }
                            
                            // Check finish reason
                            choice.finishReason?.let { reason ->
                                when (reason) {
                                    "tool_calls" -> {
                                        // Tool calls are complete, emit them
                                        toolCallBuilders.values.forEach { builder ->
                                            if (builder.isComplete()) {
                                                trySend(ChatResponse.ToolCall(
                                                    id = builder.id,
                                                    name = builder.name,
                                                    arguments = parseJsonArgs(builder.argumentsBuilder.toString())
                                                ))
                                            }
                                        }
                                        // Clear builders after emitting
                                        toolCallBuilders.clear()
                                    }
                                    "stop" -> {
                                        trySend(ChatResponse.Done(finishReason = reason))
                                    }
                                }
                            }
                        }
                        
                        // Handle usage in final chunk
                        chunk.usage?.let { usage ->
                            trySend(ChatResponse.Done(
                                usage = TokenUsage(usage.promptTokens, usage.completionTokens)
                            ))
                        }
                        
                    } catch (e: Exception) {
                        trySend(ChatResponse.Error("Failed to parse chunk: ${e.message}"))
                    }
                }
                
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    // If we already received [DONE], this is just the connection closing normally
                    if (receivedDone) {
                        close()
                        return
                    }
                    
                    // Connection reset with 200 is often just the server closing the stream
                    if (response?.code == 200 && t?.message?.contains("Connection reset", ignoreCase = true) == true) {
                        android.util.Log.w("OpenAiProvider", "Connection reset after successful response, treating as done")
                        trySend(ChatResponse.Done())
                        close()
                        return
                    }
                    
                    val responseBody = try { response?.body?.string() } catch (e: Exception) { null }
                    android.util.Log.e("OpenAiProvider", "Request failed - code: ${response?.code}, body: $responseBody, error: ${t?.message}")
                    val message = responseBody ?: t?.message ?: response?.message ?: "Unknown error"
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
                
                val openaiResponse = moshi.adapter(OpenAiResponse::class.java)
                    .fromJson(body ?: "")
                
                openaiResponse?.choices?.firstOrNull()?.let { choice ->
                    choice.message.content?.let { content ->
                        trySend(ChatResponse.TextDelta(content))
                    }
                    
                    choice.message.toolCalls?.forEach { toolCall ->
                        trySend(ChatResponse.ToolCall(
                            id = toolCall.id,
                            name = toolCall.function.name,
                            arguments = parseJsonArgs(toolCall.function.arguments)
                        ))
                    }
                }
                
                trySend(ChatResponse.Done(
                    usage = openaiResponse?.usage?.let {
                        TokenUsage(it.promptTokens, it.completionTokens)
                    }
                ))
                
            } catch (e: Exception) {
                trySend(ChatResponse.Error("Request failed: ${e.message}"))
            }
            
            close()
        }
    }.flowOn(Dispatchers.IO)
    
    private fun buildOpenAiRequest(request: ChatRequest): OpenAiRequest {
        val messages = mutableListOf<OpenAiMessage>()
        
        // Add system prompt
        request.systemPrompt?.let { systemPrompt ->
            messages.add(OpenAiMessage(
                role = "system",
                content = systemPrompt
            ))
        }
        
        // Add conversation messages
        request.messages.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> {
                    messages.add(OpenAiMessage(
                        role = "user",
                        content = msg.content
                    ))
                }
                MessageRole.ASSISTANT -> {
                    val aiToolCalls = msg.toolCalls?.takeIf { it.isNotEmpty() }?.map { call ->
                        OpenAiToolCall(
                            id = call.id,
                            type = "function",
                            function = OpenAiFunction(
                                name = call.name,
                                arguments = moshi.adapter(Map::class.java).toJson(call.arguments)
                            )
                        )
                    }
                    messages.add(OpenAiMessage(
                        role = "assistant",
                        content = msg.content ?: (if (aiToolCalls != null) "" else null),
                        toolCalls = aiToolCalls
                    ))
                }
                MessageRole.TOOL -> {
                    msg.toolResult?.let { result ->
                        messages.add(OpenAiMessage(
                            role = "tool",
                            content = result.content,
                            toolCallId = result.toolCallId
                        ))
                    }
                }
                MessageRole.SYSTEM -> { /* Already handled above */ }
            }
        }
        
        val tools = request.tools.map { tool ->
            OpenAiToolDef(
                type = "function",
                function = OpenAiFunctionDef(
                    name = tool.name,
                    description = tool.description,
                    parameters = OpenAiParameters(
                        type = "object",
                        properties = tool.parameters.properties.mapValues { (_, prop) ->
                            OpenAiPropertyDef(
                                type = prop.type,
                                description = prop.description,
                                enum = prop.enum,
                                items = prop.items?.let { OpenAiItemsDef(it.type) }
                            )
                        },
                        required = tool.parameters.required
                    )
                )
            )
        }
        
        return OpenAiRequest(
            model = request.model,
            messages = messages,
            tools = tools.takeIf { it.isNotEmpty() },
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            stream = request.stream,
            streamOptions = if (request.stream) OpenAiStreamOptions(includeUsage = true) else null
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
    
    private class OpenAiToolCallBuilder(
        var id: String = "",
        var name: String = ""
    ) {
        val argumentsBuilder = StringBuilder()
        
        fun isComplete() = id.isNotEmpty() && name.isNotEmpty()
    }
}

// ============================================================================
// OPENAI API DATA CLASSES
// ============================================================================

@JsonClass(generateAdapter = true)
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val tools: List<OpenAiToolDef>? = null,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val stream: Boolean = false,
    @Json(name = "stream_options") val streamOptions: OpenAiStreamOptions? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiStreamOptions(
    @Json(name = "include_usage") val includeUsage: Boolean = true
)

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    val role: String,
    val content: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<OpenAiToolCall>? = null,
    @Json(name = "tool_call_id") val toolCallId: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiToolDef(
    val type: String = "function",
    val function: OpenAiFunctionDef
)

@JsonClass(generateAdapter = true)
data class OpenAiFunctionDef(
    val name: String,
    val description: String,
    val parameters: OpenAiParameters
)

@JsonClass(generateAdapter = true)
data class OpenAiParameters(
    val type: String = "object",
    val properties: Map<String, OpenAiPropertyDef>,
    val required: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenAiPropertyDef(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val items: OpenAiItemsDef? = null  // For array types
)

@JsonClass(generateAdapter = true)
data class OpenAiItemsDef(
    val type: String = "string"
)

@JsonClass(generateAdapter = true)
data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunction,
    val index: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiFunction(
    val name: String,
    val arguments: String
)

@JsonClass(generateAdapter = true)
data class OpenAiResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiChoice>,
    val usage: OpenAiUsage?
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val index: Int,
    val message: OpenAiMessage,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class OpenAiUsage(
    @Json(name = "prompt_tokens") val promptTokens: Int,
    @Json(name = "completion_tokens") val completionTokens: Int,
    @Json(name = "total_tokens") val totalTokens: Int
)

@JsonClass(generateAdapter = true)
data class OpenAiStreamChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiStreamChoice>,
    val usage: OpenAiUsage? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiStreamChoice(
    val index: Int,
    val delta: OpenAiDelta,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @Json(name = "tool_calls") val toolCalls: List<OpenAiDeltaToolCall>? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiDeltaToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = null,
    val function: OpenAiDeltaFunction? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiDeltaFunction(
    val name: String? = null,
    val arguments: String? = null
)
