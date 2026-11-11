package com.amaya.intelligence.ui.screens.models

import com.amaya.intelligence.data.remote.api.ConfiguredModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderDetailScreenTest {
    @Test
    fun `merges discovered models while retaining saved configuration`() {
        val saved = ConfiguredModel(
            id = "model-b",
            displayName = "Saved model",
            maxOutputTokens = 1_024,
            enabled = false
        )

        val models = mergeConfiguredModels(
            savedModels = listOf(saved),
            refreshedModels = listOf(
                ConfiguredModel(id = "model-a", displayName = "A model"),
                ConfiguredModel(id = "model-b", displayName = "Provider model")
            )
        )

        assertEquals(listOf("model-a", "model-b"), models.map { it.id })
        assertEquals(saved, models.last())
    }

    @Test
    fun `refresh repairs stale image capability without losing user configuration`() {
        val saved = ConfiguredModel(
            id = "deepseek-flash", displayName = "我的模型", contextWindowTokens = 32_768,
            maxOutputTokens = 1_024, supportsTools = false, supportsImages = false, enabled = false
        )
        val manual = ConfiguredModel(id = "custom-model", supportsImages = false)
        val models = mergeConfiguredModels(
            savedModels = listOf(saved, manual),
            refreshedModels = listOf(ConfiguredModel(id = saved.id, supportsImages = true))
        ).associateBy { it.id }

        assertEquals(saved.copy(supportsImages = true), models.getValue(saved.id))
        assertEquals(manual, models.getValue(manual.id))
    }

    @Test
    fun `refresh respects an explicitly text-only model`() {
        val saved = ConfiguredModel(id = "text-only", supportsImages = true)
        val refreshed = ConfiguredModel(id = saved.id, supportsImages = false)
        assertEquals(listOf(saved.copy(supportsImages = false)),
            mergeConfiguredModels(listOf(saved), listOf(refreshed)))
    }
}
