package com.amaya.intelligence.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ModelLatencyResult(
    val modelId: String,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val sampleResponse: String? = null,
    val errorMessage: String? = null
) {
    fun formatLatency(): String = if (isSuccess) "${latencyMs} ms" else "Failed"
}

@Singleton
class ProviderModelService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    suspend fun testAndListModels(
        providerId: String,
        baseUrlOverride: String,
        apiKey: String
    ): Result<List<ConfiguredModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = AmayaProviderRegistry.require(providerId)
            require(!provider.isSubscription) { "Subscription models must come from the authenticated runtime" }
            if (provider.credentialRequired) require(apiKey.isNotBlank()) { "API key is required" }
            val baseUrl = resolveBaseUrl(provider, baseUrlOverride)
            val request = when {
                provider.id == "github_models" -> githubModelsRequest(apiKey)
                provider.adapter in setOf(ProviderAdapter.OPENAI_RESPONSES, ProviderAdapter.OPENAI_COMPATIBLE) -> openAiModelsRequest(baseUrl, apiKey)
                provider.adapter == ProviderAdapter.ANTHROPIC -> anthropicModelsRequest(baseUrl, apiKey)
                provider.adapter == ProviderAdapter.GEMINI -> geminiModelsRequest(baseUrl, apiKey)
                else -> error("Subscription models must come from the authenticated runtime")
            }
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.readUtf8Limited(
                    if (response.isSuccessful) MAX_REMOTE_BODY_BYTES else MAX_ERROR_BODY_BYTES
                ).orEmpty()
                if (!response.isSuccessful) {
                    error(providerError(provider, response.code, body, response.message))
                }
                parseModels(provider.adapter, body)
            }
        }
    }

    suspend fun testModelLatency(
        providerId: String,
        baseUrlOverride: String,
        apiKey: String,
        modelId: String
    ): ModelLatencyResult = withContext(Dispatchers.IO) {
        val provider = AmayaProviderRegistry.require(providerId)
        if (provider.isSubscription) {
            return@withContext ModelLatencyResult(
                modelId = modelId,
                latencyMs = 0,
                isSuccess = false,
                errorMessage = "Subscription models cannot be pinged directly"
            )
        }
        if (provider.credentialRequired && apiKey.isBlank()) {
            return@withContext ModelLatencyResult(
                modelId = modelId,
                latencyMs = 0,
                isSuccess = false,
                errorMessage = "API key is required"
            )
        }

        val baseUrl = resolveBaseUrl(provider, baseUrlOverride)
        val request = when {
            provider.adapter in setOf(ProviderAdapter.OPENAI_RESPONSES, ProviderAdapter.OPENAI_COMPATIBLE) ->
                openAiTestRequest(baseUrl, apiKey, modelId)
            provider.adapter == ProviderAdapter.ANTHROPIC ->
                anthropicTestRequest(baseUrl, apiKey, modelId)
            provider.adapter == ProviderAdapter.GEMINI ->
                geminiTestRequest(baseUrl, apiKey, modelId)
            else -> {
                return@withContext ModelLatencyResult(
                    modelId = modelId,
                    latencyMs = 0,
                    isSuccess = false,
                    errorMessage = "Unsupported provider adapter"
                )
            }
        }

        val startNano = System.nanoTime()
        try {
            val callClient = httpClient.newBuilder()
                .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            callClient.newCall(request).execute().use { response ->
                val elapsedMs = ((System.nanoTime() - startNano) / 1_000_000).coerceAtLeast(1)
                val body = response.body?.readUtf8Limited(
                    if (response.isSuccessful) MAX_REMOTE_BODY_BYTES else MAX_ERROR_BODY_BYTES
                ).orEmpty()

                if (!response.isSuccessful) {
                    val errorMsg = providerError(provider, response.code, body, response.message)
                    ModelLatencyResult(
                        modelId = modelId,
                        latencyMs = elapsedMs,
                        isSuccess = false,
                        errorMessage = errorMsg
                    )
                } else {
                    val sample = extractSampleResponse(provider.adapter, body)
                    ModelLatencyResult(
                        modelId = modelId,
                        latencyMs = elapsedMs,
                        isSuccess = true,
                        sampleResponse = sample
                    )
                }
            }
        } catch (e: Exception) {
            val elapsedMs = ((System.nanoTime() - startNano) / 1_000_000).coerceAtLeast(1)
            ModelLatencyResult(
                modelId = modelId,
                latencyMs = elapsedMs,
                isSuccess = false,
                errorMessage = e.message ?: "Network error"
            )
        }
    }

    private fun openAiTestRequest(baseUrl: HttpUrl, apiKey: String, modelId: String): Request {
        val url = baseUrl.newBuilder().addPathSegment("chat").addPathSegment("completions").build()
        val isReasoning = modelId.startsWith("o1") || modelId.startsWith("o3")
        val json = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
            if (isReasoning) {
                put("max_completion_tokens", 10)
            } else {
                put("max_tokens", 1)
            }
            put("stream", false)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body)
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder.build()
    }

    private fun anthropicTestRequest(baseUrl: HttpUrl, apiKey: String, modelId: String): Request {
        val url = baseUrl.newBuilder().addPathSegment("messages").build()
        val json = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
            put("max_tokens", 1)
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body)
            .build()
    }

    private fun geminiTestRequest(baseUrl: HttpUrl, apiKey: String, modelId: String): Request {
        val cleanModel = if (modelId.startsWith("models/")) modelId.removePrefix("models/") else modelId
        val url = baseUrl.newBuilder()
            .addPathSegment("models")
            .addPathSegment("$cleanModel:generateContent")
            .build()
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "ping")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1)
            })
        }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()
    }

    private fun extractSampleResponse(adapter: ProviderAdapter, body: String): String? = runCatching {
        val json = JSONObject(body)
        when (adapter) {
            ProviderAdapter.OPENAI_RESPONSES, ProviderAdapter.OPENAI_COMPATIBLE -> {
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
            }
            ProviderAdapter.ANTHROPIC -> {
                json.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            }
            ProviderAdapter.GEMINI -> {
                json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }.getOrNull()

    fun validateConnectionUrl(providerId: String, baseUrlOverride: String): Result<String> = runCatching {
        val provider = AmayaProviderRegistry.require(providerId)
        resolveBaseUrl(provider, baseUrlOverride).toString().trimEnd('/')
    }

    private fun resolveBaseUrl(provider: ProviderConfig, override: String): HttpUrl {
        val raw = if (provider.isCustom) override.trim() else provider.defaultBaseUrl.orEmpty()
        require(raw.isNotBlank()) { "Base URL is required" }
        val url = raw.toHttpUrlOrNull() ?: error("Enter a valid URL including https://")
        require(url.username.isEmpty() && url.password.isEmpty()) { "Base URL must not contain credentials" }
        require(url.query == null && url.fragment == null) { "Base URL must not contain a query or fragment" }
        require(
            !url.encodedPath.trimEnd('/').endsWith("/models") &&
                !url.encodedPath.trimEnd('/').endsWith("/chat/completions")
        ) { "Base URL must point to the API root, such as https://example.com/v1" }
        if (url.scheme == "http" && !isPrivateHost(url.host)) {
            error("Public provider URLs must use HTTPS")
        }
        return url
    }

    private fun openAiModelsRequest(baseUrl: HttpUrl, apiKey: String): Request {
        val builder = Request.Builder()
            .url(modelsUrl(baseUrl))
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder.build()
    }

    private fun anthropicModelsRequest(baseUrl: HttpUrl, apiKey: String): Request = Request.Builder()
        .url(modelsUrl(baseUrl))
        .header("Accept", "application/json")
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .build()

    private fun geminiModelsRequest(baseUrl: HttpUrl, apiKey: String): Request = Request.Builder()
        .url(modelsUrl(baseUrl))
        .header("Accept", "application/json")
        .header("x-goog-api-key", apiKey)
        .build()

    private fun githubModelsRequest(apiKey: String): Request = Request.Builder()
        .url("https://models.github.ai/catalog/models")
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer $apiKey")
        .build()

    private fun modelsUrl(baseUrl: HttpUrl): HttpUrl =
        baseUrl.newBuilder().addPathSegment("models").build()

    private fun providerError(
        provider: ProviderConfig,
        statusCode: Int,
        body: String,
        fallback: String
    ): String {
        val detail = runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback.ifBlank { "Request failed" }
        return "${provider.displayName} returned HTTP $statusCode: ${detail.take(240)}"
    }

    private fun parseModels(adapter: ProviderAdapter, json: String): List<ConfiguredModel> {
        val content = json.ifBlank { "{}" }
        val array = if (content.trimStart().startsWith("[")) {
            JSONArray(content)
        } else {
            val root = JSONObject(content)
            when (adapter) {
                ProviderAdapter.GEMINI -> root.optJSONArray("models")
                else -> root.optJSONArray("data") ?: root.optJSONArray("models")
            } ?: JSONArray()
        }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val rawId = item.optString("id").ifBlank { item.optString("name") }
            val id = rawId.removePrefix("models/").trim()
            if (id.isBlank()) return@mapNotNull null
            if (adapter == ProviderAdapter.GEMINI) {
                val methods = item.optJSONArray("supportedGenerationMethods")
                if (methods != null && (0 until methods.length()).none { methods.optString(it) == "generateContent" }) {
                    return@mapNotNull null
                }
            }
            val displayName = item.optString("display_name").ifBlank {
                item.optString("displayName").ifBlank { item.optString("name").ifBlank { id } }
            }
            val contextWindow = ModelContextDetector.detectContextWindow(id, item)
            val maxOutput = ModelContextDetector.detectMaxOutputTokens(id, item, contextWindow)
            val capabilities = item.optJSONArray("capabilities")?.let { array ->
                (0 until array.length()).map { array.optString(it).lowercase() }
            }.orEmpty()
            ConfiguredModel(
                id = id,
                displayName = displayName,
                contextWindowTokens = contextWindow,
                maxOutputTokens = maxOutput,
                enabled = false,
                supportsTools = capabilities.isEmpty() || capabilities.any { "tool" in it || "function" in it },
                supportsImages = capabilities.any { "image" in it || "vision" in it || "multimodal" in it }
            )
        }.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    private fun firstPositiveInt(item: JSONObject, vararg keys: String): Int? {
        keys.forEach { key -> item.optInt(key).takeIf { it > 0 }?.let { return it } }
        return null
    }

    private fun isPrivateHost(host: String): Boolean {
        val normalized = host.lowercase()
        if (normalized == "localhost" || normalized == "::1" || normalized.endsWith(".local")) return true
        if (':' in normalized && (
                normalized.startsWith("fc") || normalized.startsWith("fd") ||
                    normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
                    normalized.startsWith("fea") || normalized.startsWith("feb")
            )
        ) return true
        val parts = normalized.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            parts[0] == 127 ||
            parts[0] == 169 && parts[1] == 254 ||
            parts[0] == 192 && parts[1] == 168 ||
            parts[0] == 172 && parts[1] in 16..31
    }
}
