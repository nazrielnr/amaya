package com.amaya.intelligence.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-configurable embedding provider for semantic memory recall.
 *
 * The user supplies the endpoint + model + API key in settings, so any cloud embedding
 * service works without a hardcoded dependency:
 * - OpenAI-compatible (`POST {endpoint}/embeddings`, `Authorization: Bearer <key>`) —
 *   covers OpenAI, NVIDIA (https://integrate.api.nvidia.com/v1), Groq, Together, etc.
 * - Google Gemini (`POST {endpoint}/models/{model}:batchEmbedContents`, `x-goog-api-key`) —
 *   free tier models such as `text-embedding-004`.
 */
data class MemoryEmbeddingConfig(
    val enabled: Boolean = false,
    /** "openai_compatible" or "gemini". */
    val format: String = "openai_compatible",
    /** Base URL; for OpenAI-compatible providers this usually ends with /v1. */
    val endpoint: String = "",
    val model: String = "",
    /** Provider connection that supplies the endpoint and credential for semantic recall. */
    val connectionId: String? = null
)

@Singleton
class EmbeddingClient @Inject constructor() {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Embed [texts] in a single batched request. Returns one vector per input, in order.
     * Vectors may come from any model/dimension; callers only compare within one config.
     */
    suspend fun embed(texts: List<String>, config: MemoryEmbeddingConfig, apiKey: String): Result<List<List<Float>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(texts.isNotEmpty()) { "Nothing to embed" }
                require(config.endpoint.isNotBlank()) { "Embedding endpoint is not configured" }
                require(config.model.isNotBlank()) { "Embedding model is not configured" }
                require(apiKey.isNotBlank()) { "Embedding API key is not configured" }
                when (config.format) {
                    "gemini" -> embedGemini(texts, config, apiKey)
                    else -> embedOpenAiCompatible(texts, config, apiKey)
                }
            }
        }

    private fun embedOpenAiCompatible(texts: List<String>, config: MemoryEmbeddingConfig, apiKey: String): List<List<Float>> {
        val base = config.endpoint.trimEnd('/')
        val url = if (base.endsWith("/embeddings")) base else "$base/embeddings"
        val body = JSONObject()
            .put("model", config.model)
            .put("input", JSONArray(texts))
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Embedding API error ${response.code}: ${raw.take(200)}")
            val data = JSONObject(raw).optJSONArray("data") ?: error("Embedding response missing data")
            return buildList {
                for (i in 0 until data.length()) {
                    val vector = data.optJSONObject(i)?.optJSONArray("embedding")
                        ?: error("Embedding $i missing vector")
                    add(buildList { for (j in 0 until vector.length()) add(vector.optDouble(j).toFloat()) })
                }
            }
        }
    }

    private fun embedGemini(texts: List<String>, config: MemoryEmbeddingConfig, apiKey: String): List<List<Float>> {
        val base = config.endpoint.trimEnd('/')
            .ifBlank { DEFAULT_GEMINI_BASE }
        val model = config.model
        val url = "$base/models/$model:batchEmbedContents"
        val requests = JSONArray()
        texts.forEach { text ->
            requests.put(JSONObject()
                .put("model", "models/$model")
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
        }
        val body = JSONObject().put("requests", requests).toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Gemini embedding API error ${response.code}: ${raw.take(200)}")
            val embeddings = JSONObject(raw).optJSONArray("embeddings")
                ?: error("Gemini embedding response missing embeddings")
            return buildList {
                for (i in 0 until embeddings.length()) {
                    val vector = embeddings.optJSONObject(i)?.optJSONArray("values")
                        ?: error("Embedding $i missing values")
                    add(buildList { for (j in 0 until vector.length()) add(vector.optDouble(j).toFloat()) })
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
