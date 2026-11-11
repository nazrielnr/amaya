package com.amaya.intelligence.data.remote.api

import com.amaya.intelligence.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient

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

        assertEquals(R.string.provider_public_https_required,
            (result.exceptionOrNull() as ProviderConfigurationException).messageRes)
    }

    @Test
    fun requiresUrlScheme() {
        val result = service.validateConnectionUrl(
            "custom_openai_compatible",
            "ai.example.com/v1"
        )

        assertEquals(R.string.provider_valid_url_required,
            (result.exceptionOrNull() as ProviderConfigurationException).messageRes)
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
    fun readsDeclaredImageInputFromDeepSeekCatalog() = runBlocking {
        val models = discoverModels("""
            {"data":[
                {"id":"deepseek-flash","name":"DeepSeek-V4.1-Flash",
                 "context_window":1048576,"max_output_tokens":393216,
                 "input_modalities":["text","image"],"output_modalities":["text"]},
                {"id":"text-only-model","input_modalities":["text"]}
            ]}
        """.trimIndent()).associateBy { it.id }

        val flash = models.getValue("deepseek-flash")
        assertEquals("DeepSeek-V4.1-Flash", flash.displayName)
        assertEquals(1048576, flash.contextWindowTokens)
        assertEquals(393216, flash.maxOutputTokens)
        assertTrue(flash.supportsImages)
        assertEquals(false, models.getValue("text-only-model").supportsImages)
    }

    @Test
    fun absentModalityMetadataDoesNotDeclareImageInputUnsupported() = runBlocking {
        val models = discoverModels("""
            {"data":[
                {"id":"omitted"},
                {"id":"empty","input_modalities":[]},
                {"id":"tools-tag","capabilities":["tools"]},
                {"id":"vision-tag","capabilities":["vision"]},
                {"id":"normalized","input_modalities":[" TEXT "," IMAGE "]}
            ]}
        """.trimIndent())

        assertEquals(5, models.size)
        assertTrue(models.all { it.supportsImages })
    }

    private suspend fun discoverModels(catalog: String): List<ConfiguredModel> {
        // Return a catalog locally: these tests never contact a model service.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(catalog.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return ProviderModelService(client).testAndListModels(
            "custom_openai_compatible", "https://catalog.example.invalid/v1", ""
        ).getOrThrow()
    }
}
