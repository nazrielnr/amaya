package com.amaya.intelligence.data.remote.api

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelContextDetectorTest {

    @Test
    fun detectsContextFromDirectJsonKey() {
        val json = JSONObject("""{"context_window": 65536}""")
        val result = ModelContextDetector.detectContextWindow("unknown-model", json)
        assertEquals(65536, result)
    }

    @Test
    fun detectsContextFromNestedLimitsInJson() {
        val json = JSONObject("""{"limits": {"context_window": 131072, "max_tokens": 4096}}""")
        val result = ModelContextDetector.detectContextWindow("unknown-model", json)
        assertEquals(131072, result)
    }

    @Test
    fun detectsContextFromTopProviderInJson() {
        val json = JSONObject("""{"top_provider": {"context_length": 128000, "max_completion_tokens": 8192}}""")
        val result = ModelContextDetector.detectContextWindow("unknown-model", json)
        assertEquals(128000, result)
    }

    @Test
    fun detectsContextFromExplicitTokenNamePatterns() {
        assertEquals(128000, ModelContextDetector.detectContextWindow("my-custom-model-128k"))
        assertEquals(32768, ModelContextDetector.detectContextWindow("llama-2-7b-32k"))
        assertEquals(1000000, ModelContextDetector.detectContextWindow("custom-1m-instruct"))
        assertEquals(200000, ModelContextDetector.detectContextWindow("finetune-200k"))
        assertEquals(16384, ModelContextDetector.detectContextWindow("openchat-3.5-16k"))
    }

    @Test
    fun detectsContextForStandardModelFamilies() {
        // OpenAI
        assertEquals(128000, ModelContextDetector.detectContextWindow("gpt-4o"))
        assertEquals(128000, ModelContextDetector.detectContextWindow("openai/gpt-4o-mini"))
        assertEquals(200000, ModelContextDetector.detectContextWindow("o1-preview"))
        assertEquals(200000, ModelContextDetector.detectContextWindow("o3-mini"))
        assertEquals(8192, ModelContextDetector.detectContextWindow("gpt-4"))
        assertEquals(16385, ModelContextDetector.detectContextWindow("gpt-3.5-turbo"))

        // Anthropic Claude
        assertEquals(200000, ModelContextDetector.detectContextWindow("claude-3-5-sonnet-20241022"))
        assertEquals(200000, ModelContextDetector.detectContextWindow("anthropic/claude-3-7-sonnet"))
        assertEquals(200000, ModelContextDetector.detectContextWindow("claude-3-haiku"))
        assertEquals(100000, ModelContextDetector.detectContextWindow("claude-2"))

        // Google Gemini
        assertEquals(2097152, ModelContextDetector.detectContextWindow("models/gemini-1.5-pro"))
        assertEquals(1048576, ModelContextDetector.detectContextWindow("gemini-2.0-flash"))
        assertEquals(32768, ModelContextDetector.detectContextWindow("gemini-1.0-pro"))

        // DeepSeek
        assertEquals(64000, ModelContextDetector.detectContextWindow("deepseek-chat"))
        assertEquals(64000, ModelContextDetector.detectContextWindow("deepseek-r1:8b"))

        // Meta LLaMA
        assertEquals(128000, ModelContextDetector.detectContextWindow("meta-llama/llama-3.1-70b-instruct"))
        assertEquals(128000, ModelContextDetector.detectContextWindow("llama-3.3-70b"))
        assertEquals(8192, ModelContextDetector.detectContextWindow("llama-3-8b"))
        assertEquals(4096, ModelContextDetector.detectContextWindow("llama-2-7b"))

        // Qwen
        assertEquals(128000, ModelContextDetector.detectContextWindow("qwen/qwen-2.5-coder-32b"))
        assertEquals(128000, ModelContextDetector.detectContextWindow("qwq-32b"))

        // Mistral
        assertEquals(128000, ModelContextDetector.detectContextWindow("mistral-large-latest"))
        assertEquals(64000, ModelContextDetector.detectContextWindow("mixtral-8x22b"))
    }

    @Test
    fun detectsMaxOutputAndEnforcesBoundedLimits() {
        val gpt4oMaxOutput = ModelContextDetector.detectMaxOutputTokens("gpt-4o")
        assertEquals(16384, gpt4oMaxOutput)

        val o1MaxOutput = ModelContextDetector.detectMaxOutputTokens("o1")
        assertEquals(100000, o1MaxOutput)

        val deepseekMaxOutput = ModelContextDetector.detectMaxOutputTokens("deepseek-r1")
        assertEquals(8192, deepseekMaxOutput)

        // Ensures maxOutput < contextWindow
        val bounded = ModelContextDetector.detectMaxOutputTokens("custom", contextWindow = 4096)
        assertTrue(bounded == null || bounded < 4096)
    }

    @Test
    fun formatsTokenCountForUiCleanly() {
        assertEquals("128K", ModelContextDetector.formatTokenCount(128000))
        assertEquals("200K", ModelContextDetector.formatTokenCount(200000))
        assertEquals("1M", ModelContextDetector.formatTokenCount(1048576))
        assertEquals("2M", ModelContextDetector.formatTokenCount(2097152))
        assertEquals("32K", ModelContextDetector.formatTokenCount(32768))
        assertEquals("500", ModelContextDetector.formatTokenCount(500))
    }
}
