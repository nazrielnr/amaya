package com.amaya.intelligence.data.remote.api

import org.json.JSONObject
import java.util.Locale

/**
 * Intelligent detector for model context window and token limits.
 *
 * Infers context window and max output limits through a multi-stage strategy:
 * 1. Inspects remote provider JSON response fields (including nested objects like limits, architecture, top_provider).
 * 2. Matches explicit token limit patterns in the model identifier (e.g. "128k", "200k", "1m", "32k").
 * 3. References a comprehensive catalog of modern LLM families and architectures.
 */
object ModelContextDetector {

    private val EXPLICIT_TOKEN_REGEX = Regex(
        """(?:^|[-_./:])(\d+)(k|m)(?:$|[-_./:])""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Attempts to detect the context window (in tokens) for a model.
     */
    fun detectContextWindow(modelId: String, jsonItem: JSONObject? = null): Int? {
        if (jsonItem != null) {
            val fromJson = extractContextFromJson(jsonItem)
            if (fromJson != null && fromJson > 0) return fromJson
        }

        val cleanId = normalizeModelId(modelId)
        if (cleanId.isBlank()) return null

        val fromPattern = extractContextFromPattern(cleanId)
        if (fromPattern != null && fromPattern > 0) return fromPattern

        return detectContextFromCatalog(cleanId)
    }

    /**
     * Attempts to detect max output completion tokens for a model.
     * Guaranteed to be strictly less than [contextWindow] if [contextWindow] is known.
     */
    fun detectMaxOutputTokens(
        modelId: String,
        jsonItem: JSONObject? = null,
        contextWindow: Int? = detectContextWindow(modelId, jsonItem)
    ): Int? {
        val detected = if (jsonItem != null) {
            extractMaxOutputFromJson(jsonItem) ?: detectMaxOutputFromCatalog(normalizeModelId(modelId))
        } else {
            detectMaxOutputFromCatalog(normalizeModelId(modelId))
        }

        if (detected == null || detected <= 0) return null

        // Enforce that max output cannot equal or exceed context window
        if (contextWindow != null && contextWindow > 0) {
            if (detected >= contextWindow) {
                return (contextWindow / 2).coerceAtLeast(1)
            }
        }

        return detected
    }

    /**
     * Formats token count cleanly for UI presentation (e.g. 128,000 -> "128K", 1,048,576 -> "1M").
     */
    fun formatTokenCount(tokens: Int): String {
        return when (tokens) {
            2_097_152, 2_000_000 -> "2M"
            1_048_576, 1_000_000 -> "1M"
            524_288, 500_000 -> "500K"
            262_144, 256_000 -> "256K"
            200_000 -> "200K"
            131_072, 128_000 -> "128K"
            65_536, 64_000 -> "64K"
            32_768, 32_000 -> "32K"
            16_384, 16_385, 16_000 -> "16K"
            8_192, 8_000 -> "8K"
            4_096, 4_000 -> "4K"
            2_048, 2_000 -> "2K"
            else -> when {
                tokens >= 1_000_000 -> {
                    val m = Math.round(tokens / 1_000_000.0)
                    "${m}M"
                }
                tokens >= 1_000 -> {
                    val k = Math.round(tokens / 1_000.0)
                    "${k}K"
                }
                else -> tokens.toString()
            }
        }
    }

    /**
     * Normalizes raw model IDs by lowercasing, trimming, and stripping common prefixes
     * (such as organization names or "models/").
     */
    fun normalizeModelId(rawId: String): String {
        var id = rawId.trim().lowercase(Locale.ROOT)
        if ('/' in id) {
            id = id.substringAfterLast('/')
        }
        id = id.removePrefix("models/")
        return id
    }

    private fun extractContextFromJson(item: JSONObject): Int? {
        firstPositiveInt(
            item,
            "context_window",
            "contextWindow",
            "context_length",
            "contextLength",
            "max_context_length",
            "max_context_tokens",
            "max_sequence_length",
            "max_position_embeddings",
            "inputTokenLimit",
            "input_token_limit",
            "context_size",
            "contextSize"
        )?.let { return it }

        item.optJSONObject("top_provider")?.let { top ->
            firstPositiveInt(top, "context_length", "context_window")?.let { return it }
        }

        item.optJSONObject("limits")?.let { limits ->
            firstPositiveInt(limits, "context_window", "context_length", "max_tokens", "input")?.let { return it }
        }

        item.optJSONObject("architecture")?.let { arch ->
            firstPositiveInt(arch, "context_length", "max_sequence_length")?.let { return it }
        }

        item.optJSONObject("parameters")?.let { params ->
            firstPositiveInt(params, "context_window", "context_length")?.let { return it }
        }

        item.optJSONObject("model_info")?.let { info ->
            firstPositiveInt(info, "context_length", "max_sequence_length")?.let { return it }
        }

        return null
    }

    private fun extractMaxOutputFromJson(item: JSONObject): Int? {
        firstPositiveInt(
            item,
            "max_output_tokens",
            "maxOutputTokens",
            "outputTokenLimit",
            "output_token_limit",
            "max_completion_tokens"
        )?.let { return it }

        item.optJSONObject("top_provider")?.let { top ->
            firstPositiveInt(top, "max_completion_tokens")?.let { return it }
        }

        item.optJSONObject("limits")?.let { limits ->
            firstPositiveInt(limits, "max_output_tokens", "output", "max_completion_tokens")?.let { return it }
        }

        return null
    }

    private fun extractContextFromPattern(id: String): Int? {
        val match = EXPLICIT_TOKEN_REGEX.find(id) ?: return null
        val count = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        return when (unit) {
            "m" -> count * 1_000_000
            "k" -> when (count) {
                1024 -> 1_048_576
                512 -> 524_288
                256 -> 262_144
                200 -> 200_000
                128 -> 128_000
                64 -> 64_000
                32 -> 32_768
                16 -> 16_384
                8 -> 8_192
                4 -> 4_096
                2 -> 2_048
                else -> count * 1_000
            }
            else -> null
        }
    }

    private fun detectContextFromCatalog(id: String): Int? {
        val baseId = id.substringBefore(':')

        return when {
            // Google Gemini
            baseId.contains("gemini-1.5-pro") || baseId.contains("gemini-2.0-pro") || baseId.contains("gemini-2.5-pro") -> 2_097_152
            baseId.contains("gemini-1.5") || baseId.contains("gemini-2.0") || baseId.contains("gemini-2.5") -> 1_048_576
            baseId.contains("gemini-1.0-pro") || baseId.contains("gemini-pro") -> 32_768
            baseId.startsWith("gemini") -> 1_048_576

            // Anthropic Claude
            baseId.startsWith("claude-3-7") || baseId.startsWith("claude-3.7") -> 200_000
            baseId.startsWith("claude-3-5") || baseId.startsWith("claude-3.5") -> 200_000
            baseId.startsWith("claude-3") -> 200_000
            baseId.contains("claude-2.1") -> 200_000
            baseId.contains("claude-2") || baseId.contains("claude-instant") -> 100_000
            baseId.contains("claude") -> 200_000

            // OpenAI Reasoning & Modern models
            baseId.startsWith("o1") || baseId.startsWith("o3") || baseId.startsWith("o4") -> 200_000
            baseId.startsWith("gpt-4o") || baseId.startsWith("chatgpt-4o") -> 128_000
            baseId.startsWith("gpt-4.5") -> 128_000
            baseId.startsWith("gpt-4-turbo") || baseId.contains("gpt-4-0125") || baseId.contains("gpt-4-1106") -> 128_000
            baseId.contains("gpt-4-32k") -> 32_768
            baseId.startsWith("gpt-4") -> 8_192
            baseId.startsWith("gpt-3.5-turbo-16k") -> 16_384
            baseId.startsWith("gpt-3.5-turbo") -> 16_385
            baseId.startsWith("text-embedding-3") -> 8_191

            // DeepSeek
            baseId.contains("deepseek-chat") || baseId.contains("deepseek-v3") -> 64_000
            baseId.contains("deepseek-reasoner") || baseId.contains("deepseek-r1") -> 64_000
            baseId.contains("deepseek-coder") -> 16_384
            baseId.contains("deepseek") -> 64_000

            // Meta LLaMA
            baseId.contains("llama-3.3") || baseId.contains("llama-3.2") || baseId.contains("llama-3.1") -> 128_000
            baseId.contains("llama-3") -> 8_192
            baseId.contains("llama-2") -> 4_096
            baseId.contains("codellama") -> 16_384

            // Qwen (Alibaba)
            baseId.contains("qwen-2.5") || baseId.contains("qwen2.5") || baseId.contains("qwq") -> 128_000
            baseId.contains("qwen-2") || baseId.contains("qwen2") -> 128_000
            baseId.contains("qwen-1.5") || baseId.contains("qwen1.5") || baseId.contains("qwen") -> 32_768

            // Mistral AI
            baseId.contains("mistral-large") || baseId.contains("pixtral-large") || baseId.contains("codestral") || baseId.contains("mistral-nemo") -> 128_000
            baseId.contains("mistral-small") || baseId.contains("pixtral") -> 128_000
            baseId.contains("mixtral-8x22b") -> 64_000
            baseId.contains("mixtral-8x7b") -> 32_768
            baseId.contains("mistral-7b") -> 32_768

            // xAI Grok
            baseId.contains("grok-2") || baseId.contains("grok-beta") || baseId.contains("grok") -> 131_072

            // Cohere Command
            baseId.contains("command-r") -> 128_000
            baseId.contains("command") -> 4_096

            // Perplexity Sonar
            baseId.contains("sonar") -> 127_072

            // Amazon Nova
            baseId.contains("nova-") -> 300_000

            // Microsoft Phi
            baseId.contains("phi-4") -> 16_384
            baseId.contains("phi-3.5") -> 128_000
            baseId.contains("phi-3") -> if (baseId.contains("128k")) 128_000 else 4_096

            else -> null
        }
    }

    private fun detectMaxOutputFromCatalog(id: String): Int? {
        val baseId = id.substringBefore(':')
        return when {
            baseId.startsWith("o1") || baseId.startsWith("o3") || baseId.startsWith("o4") -> 100_000
            baseId.startsWith("claude-3-7") || baseId.startsWith("claude-3.7") -> 64_000
            baseId.startsWith("gpt-4o") || baseId.startsWith("chatgpt-4o") -> 16_384
            baseId.startsWith("gpt-4.5") -> 16_384
            baseId.startsWith("gpt-4-turbo") || baseId.startsWith("gpt-4") -> 4_096
            baseId.startsWith("gpt-3.5") -> 4_096
            baseId.contains("claude-3-5") || baseId.contains("claude-3.5") -> 8_192
            baseId.contains("claude-3") -> 4_096
            baseId.contains("gemini-1.5") || baseId.contains("gemini-2.0") || baseId.contains("gemini-2.5") -> 8_192
            baseId.contains("gemini-1.0") || baseId.contains("gemini-pro") -> 2_048
            baseId.contains("deepseek-") || baseId.contains("deepseek") -> 8_192
            baseId.contains("llama-3.3") || baseId.contains("llama-3.2") || baseId.contains("llama-3.1") -> 8_192
            baseId.contains("qwen-2.5") || baseId.contains("qwen2.5") || baseId.contains("qwq") -> 8_192
            baseId.contains("mistral-large") || baseId.contains("codestral") -> 8_192
            else -> null
        }
    }

    private fun firstPositiveInt(item: JSONObject, vararg keys: String): Int? {
        keys.forEach { key ->
            val value = item.optInt(key)
            if (value > 0) return value
        }
        return null
    }
}
