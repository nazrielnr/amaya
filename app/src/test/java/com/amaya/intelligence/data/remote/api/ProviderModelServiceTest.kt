package com.amaya.intelligence.data.remote.api

import com.amaya.intelligence.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
}
