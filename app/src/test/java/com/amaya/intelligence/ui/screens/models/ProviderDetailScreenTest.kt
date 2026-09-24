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
    fun `newly discovered models default to disabled`() {
        val models = mergeConfiguredModels(
            savedModels = emptyList(),
            refreshedModels = listOf(
                ConfiguredModel(id = "gpt-4o", displayName = "GPT-4o"),
                ConfiguredModel(id = "claude-3-5-sonnet", displayName = "Claude 3.5 Sonnet")
            )
        )

        assertEquals(2, models.size)
        assertEquals(false, models[0].enabled)
        assertEquals(false, models[1].enabled)
    }
}
