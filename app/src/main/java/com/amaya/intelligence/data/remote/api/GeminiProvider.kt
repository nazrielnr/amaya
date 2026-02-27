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
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Gemini AI provider.
 * 
 * API Documentation: https://ai.google.dev/gemini-api/docs
 * 
 * KEY FEATURES:
 * - Function calling via functionDeclarations
 * - Streaming via streamGenerateContent endpoint
 * - Multimodal support (though we only use text here)
 */
@Singleton
class GeminiProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsProvider: () -> AiSettings
) : AiProvider {
    
    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
    
    override val name = "Google Gemini"
    
    // Models are configured per-agent by the user; no hardcoded list needed
    override val supportedModels = emptyList<String>()
    
    override fun isConfigured(): Boolean {
        return settingsProvider().geminiApiKey.isNotBlank()
    }
    
    override suspend fun chat(request: ChatRequest): Flow<ChatResponse> = callbackFlow {
        val settings = settingsProvider()
        
        if (settings.geminiApiKey.isBlank()) {
            trySend(ChatResponse.Error("Gemini API key not configured", "AUTH_ERROR"))
            close()
            return@callbackFlow
        }
        
        // Build request body
        val geminiRequest = buildGeminiRequest(request)
        val jsonBody = moshi.adapter(GeminiRequest::class.java).toJson(geminiRequest)
        
        val endpoint = if (request.stream) "streamGenerateContent" else "generateContent"
        val url = "$BASE_URL/models/${request.model}:$endpoint?key=${settings.geminiApiKey}"
        
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        if (request.stream) {
            // Gemini uses newline-delimited JSON for streaming
            try {
                val response = httpClient.newCall(httpRequest).execute()
                
                if (!response.isSuccessful) {
                    val body = response.body?.string()
                    trySend(ChatResponse.Error("API error: ${response.code} - $body"))
                    close()
                    return@callbackFlow
                }
                
                val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                val jsonBuffer = StringBuilder()
                var bracketCount = 0
                var inString = false
                var prevChar: Char? = null
                
                reader.use { bufferedReader ->
                    var char: Int
                    while (bufferedReader.read().also { char = it } != -1) {
                        val c = char.toChar()
                        
                        // Skip array brackets at root level
                        if (bracketCount == 0 && (c == '[' || c == ']' || c == ',')) {
                            continue
                        }
                        
                        jsonBuffer.append(c)
                        
                        // Track string state
                        if (c == '"' && prevChar != '\\') {
                            inString = !inString
                        }
                        
                        if (!inString) {
                            when (c) {
                                '{' -> bracketCount++
                                '}' -> {
                                    bracketCount--
                                    if (bracketCount == 0) {
                                        // Complete JSON object
                                        val json = jsonBuffer.toString().trim()
                                        jsonBuffer.clear()
                                        
                                        if (json.isNotEmpty() && json.startsWith("{")) {
                                            processGeminiChunk(json)?.let { response ->
                                                trySend(response)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        prevChar = c
                    }
                }
                
                trySend(ChatResponse.Done())
                
            } catch (e: Exception) {
                trySend(ChatResponse.Error("Stream error: ${e.message}"))
            }
            
            close()
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
                
                val geminiResponse = moshi.adapter(GeminiResponse::class.java)
                    .fromJson(body ?: "")
                
                geminiResponse?.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                    part.text?.let { text ->
                        trySend(ChatResponse.TextDelta(text))
                    }
                    
                    part.functionCall?.let { functionCall ->
                        trySend(ChatResponse.ToolCall(
                            id = "call_${System.currentTimeMillis()}",
                            name = functionCall.name,
                            arguments = functionCall.args ?: emptyMap()
                        ))
                    }
                }
                
                trySend(ChatResponse.Done(
                    usage = geminiResponse?.usageMetadata?.let {
                        TokenUsage(it.promptTokenCount, it.candidatesTokenCount)
                    }
                ))
                
            } catch (e: Exception) {
                trySend(ChatResponse.Error("Request failed: ${e.message}"))
            }
            
            close()
        }
    }.flowOn(Dispatchers.IO)
    
    private fun processGeminiChunk(json: String): ChatResponse? {
        return try {
            android.util.Log.d("GeminiProvider", "Processing chunk: ${json.take(200)}")
            val chunk = moshi.adapter(GeminiResponse::class.java).fromJson(json)
            android.util.Log.d("GeminiProvider", "Parsed chunk - candidates: ${chunk?.candidates?.size}")
            
            // Collect thoughtSignature from parts (it comes separately from text and functionCall)
            var thoughtSignature: String? = null
            chunk?.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                if (part.thoughtSignature != null) {
                    thoughtSignature = part.thoughtSignature
                    android.util.Log.d("GeminiProvider", "Found thoughtSignature: ${part.thoughtSignature.take(50)}")
                }
            }
            
            chunk?.candidates?.firstOrNull()?.content?.parts?.forEach { part ->
                android.util.Log.d("GeminiProvider", "Part text: ${part.text?.take(100)}")
                
                // Skip empty text parts that only contain thoughtSignature
                part.text?.let { text ->
                    if (text.isNotEmpty()) {
                        return ChatResponse.TextDelta(text)
                    }
                }
                
                part.functionCall?.let { functionCall ->
                    val metadata = if (thoughtSignature != null) {
                        mapOf("thoughtSignature" to thoughtSignature!!)
                    } else {
                        emptyMap()
                    }
                    return ChatResponse.ToolCall(
                        id = "call_${System.currentTimeMillis()}",
                        name = functionCall.name,
                        arguments = functionCall.args ?: emptyMap(),
                        metadata = metadata
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("GeminiProvider", "Parse error: ${e.message}")
            null
        }
    }
    
    private fun buildGeminiRequest(request: ChatRequest): GeminiRequest {
        val contents = mutableListOf<GeminiContent>()
        
        // Convert messages to Gemini format
        request.messages.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> {
                    contents.add(GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = msg.content))
                    ))
                }
                MessageRole.ASSISTANT -> {
                    val parts = mutableListOf<GeminiPart>()
                    msg.content?.let { 
                        if (it.isNotEmpty()) {
                            parts.add(GeminiPart(text = it)) 
                        }
                    }
                    msg.toolCalls?.forEach { call ->
                        // Include thoughtSignature if present in metadata
                        val thoughtSig = call.metadata["thoughtSignature"]
                        parts.add(GeminiPart(
                            functionCall = GeminiFunctionCall(
                                name = call.name,
                                args = call.arguments
                            ),
                            thoughtSignature = thoughtSig
                        ))
                    }
                    if (parts.isNotEmpty()) {
                        contents.add(GeminiContent(role = "model", parts = parts))
                    }
                }
                MessageRole.TOOL -> {
                    msg.toolResult?.let { result ->
                        // Include thoughtSignature from metadata if present
                        val thoughtSig = result.metadata["thoughtSignature"]
                        // Get function name from metadata (Gemini needs name, not ID)
                        val functionName = result.metadata["toolName"] ?: result.toolCallId
                        val responseParts = mutableListOf<GeminiPart>()
                        responseParts.add(GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = functionName,
                                response = mapOf("result" to result.content)
                            )
                        ))
                        // Add thoughtSignature as a separate part if present
                        if (thoughtSig != null) {
                            responseParts.add(GeminiPart(
                                text = "",
                                thoughtSignature = thoughtSig
                            ))
                        }
                        contents.add(GeminiContent(
                            role = "user",
                            parts = responseParts
                        ))
                    }
                }
                MessageRole.SYSTEM -> { /* Handled in systemInstruction */ }
            }
        }
        
        val tools = if (request.tools.isNotEmpty()) {
            listOf(GeminiTools(
                functionDeclarations = request.tools.map { tool ->
                    GeminiFunctionDeclaration(
                        name = tool.name,
                        description = tool.description,
                        parameters = GeminiSchema(
                            type = "OBJECT",
                            properties = tool.parameters.properties.mapValues { (_, prop) ->
                                GeminiPropertySchema(
                                    type = prop.type.uppercase(),
                                    description = prop.description,
                                    enum = prop.enum
                                )
                            },
                            required = tool.parameters.required
                        )
                    )
                }
            ))
        } else null
        
        return GeminiRequest(
            contents = contents,
            systemInstruction = request.systemPrompt?.let {
                GeminiContent(parts = listOf(GeminiPart(text = it)))
            },
            tools = tools,
            generationConfig = GeminiGenerationConfig(
                maxOutputTokens = request.maxTokens,
                temperature = request.temperature
            )
        )
    }
}

// ============================================================================
// GEMINI API DATA CLASSES
// ============================================================================

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTools>? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    val thoughtSignature: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class GeminiTools(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiSchema
)

@JsonClass(generateAdapter = true)
data class GeminiSchema(
    val type: String = "OBJECT",
    val properties: Map<String, GeminiPropertySchema>,
    val required: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeminiPropertySchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int = 4096,
    val temperature: Float = 0.7f
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0
)
