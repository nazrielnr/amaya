package com.amaya.intelligence.data.remote.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.test.runTest

class ProviderModelServiceTest {
    private val service = ProviderModelService(OkHttpClient())

    @Test
    fun acceptsHttpsPublicEndpoint() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "https://ai.example.com/v1/"
        )

        assertEquals("https://ai.example.com/v1", result.getOrThrow())
    }

    @Test
    fun acceptsHttpPrivateEndpoint() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "http://192.168.1.5:8080/v1"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun rejectsHttpPublicEndpoint() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "http://ai.example.com/v1"
        )

        assertTrue(result.exceptionOrNull()?.message?.contains("HTTPS") == true)
    }

    @Test
    fun requiresUrlScheme() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "ai.example.com/v1"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsCredentialsInUrl() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "https://user:secret@ai.example.com/v1"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun officialPresetIgnoresOverride() {
        val result = service.validateConnectionUrl(
            "openai",
            "https://attacker.example.com/v1"
        )

        assertEquals("https://api.openai.com/v1", result.getOrThrow())
    }

    @Test
    fun rejectsEndpointInsteadOfApiRoot() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "https://ai.example.com/v1/models"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsInvalidPrivateIpv4() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "http://192.168.999.1/v1"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun testModelLatencySuccessOpenAi() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
                assertEquals("Bearer test-key", request.header("Authorization"))
                val responseJson = """
                    {
                        "choices": [
                            {"message": {"content": "pong"}}
                        ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = ProviderModelService(mockClient)
        val result = service.testModelLatency(
            providerId = "openai",
            baseUrlOverride = "",
            apiKey = "test-key",
            modelId = "gpt-4o"
        )

        assertTrue(result.isSuccess)
        assertEquals("gpt-4o", result.modelId)
        assertEquals("pong", result.sampleResponse)
        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun testModelLatencyAnthropic() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
                assertEquals("test-anthropic-key", request.header("x-api-key"))
                val responseJson = """
                    {
                        "content": [
                            {"type": "text", "text": "hello from claude"}
                        ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = ProviderModelService(mockClient)
        val result = service.testModelLatency(
            providerId = "anthropic",
            baseUrlOverride = "",
            apiKey = "test-anthropic-key",
            modelId = "claude-3-5-sonnet-20241022"
        )

        assertTrue(result.isSuccess)
        assertEquals("hello from claude", result.sampleResponse)
    }

    @Test
    fun testModelLatencyGemini() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertTrue(request.url.toString().contains("models/gemini-1.5-pro:generateContent"))
                assertEquals("gemini-key", request.header("x-goog-api-key"))
                val responseJson = """
                    {
                        "candidates": [
                            {
                                "content": {
                                    "parts": [
                                        {"text": "gemini response"}
                                    ]
                                }
                            }
                        ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = ProviderModelService(mockClient)
        val result = service.testModelLatency(
            providerId = "google_gemini_api",
            baseUrlOverride = "",
            apiKey = "gemini-key",
            modelId = "gemini-1.5-pro"
        )

        assertTrue(result.isSuccess)
        assertEquals("gemini-response", result.sampleResponse?.replace(" ", "-"))
    }

    @Test
    fun testModelLatencyMissingApiKeyFails() = runTest {
        val result = service.testModelLatency(
            providerId = "openai",
            baseUrlOverride = "",
            apiKey = "",
            modelId = "gpt-4o"
        )

        assertFalse(result.isSuccess)
        assertEquals("API key is required", result.errorMessage)
    }

    @Test
    fun testModelLatencyHttpError() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":{"message":"Incorrect API key provided"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = ProviderModelService(mockClient)
        val result = service.testModelLatency(
            providerId = "openai",
            baseUrlOverride = "",
            apiKey = "bad-key",
            modelId = "gpt-4o"
        )

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage?.contains("HTTP 401") == true)
        assertTrue(result.errorMessage?.contains("Incorrect API key") == true)
    }
}
