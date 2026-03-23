package com.amaya.intelligence.di

import com.amaya.intelligence.data.remote.api.*
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt module for AI provider dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    
    @Provides
    @Singleton
    fun provideAnthropicProvider(
        httpClient: OkHttpClient,
        moshi: Moshi,
        settingsManager: AiSettingsManager
    ): AnthropicProvider {
        return AnthropicProvider(
            httpClient = httpClient,
            moshi = moshi,
            settingsProvider = { settingsManager.getSettings() },
            // FIX 2.2: Pass settingsManager for per-agent API key lookup
            settingsManager = settingsManager
        )
    }
    
    @Provides
    @Singleton
    fun provideOpenAiProvider(
        httpClient: OkHttpClient,
        moshi: Moshi,
        settingsManager: AiSettingsManager
    ): OpenAiProvider {
        return OpenAiProvider(
            httpClient = httpClient,
            moshi = moshi,
            settingsProvider = { settingsManager.getSettings() },
            // FIX 2.2: Pass settingsManager for per-agent API key and baseUrl lookup
            settingsManager = settingsManager
        )
    }
    
    @Provides
    @Singleton
    fun provideGeminiProvider(
        httpClient: OkHttpClient,
        moshi: Moshi,
        settingsManager: AiSettingsManager
    ): GeminiProvider {
        return GeminiProvider(
            httpClient = httpClient,
            moshi = moshi,
            settingsProvider = { settingsManager.getSettings() },
            // FIX 2.2: Pass settingsManager for per-agent API key lookup
            settingsManager = settingsManager
        )
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        // FIX 5.11: Application-scoped coroutine scope tied to process lifetime.
        // Replaces manual SupervisorJob() in AiRepository which leaked via missing close() call.
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
