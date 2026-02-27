package com.amaya.intelligence.di

import com.amaya.intelligence.data.remote.api.*
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

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
            settingsProvider = { settingsManager.getSettings() }
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
            settingsProvider = { settingsManager.getSettings() }
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
            settingsProvider = { settingsManager.getSettings() }
        )
    }
}
