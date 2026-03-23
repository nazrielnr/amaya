package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R

object AgentIcon {
    fun get(name: String, modelId: String): Int {
        val tokens = buildSet {
            addAll(tokenize(name))
            addAll(tokenize(modelId))
        }
        return when {
            tokens.any { it in GPT_ALIASES } -> R.drawable.ic_chatgpt_light
            tokens.any { it in GEMINI_ALIASES } -> R.drawable.ic_gemini
            tokens.any { it in CLAUDE_ALIASES } -> R.drawable.ic_claude
            else -> 0
        }
    }

    private fun tokenize(value: String): Set<String> {
        return value.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .mapNotNull { token -> token.takeIf { it.isNotBlank() } }
            .toSet()
    }

    private val GPT_ALIASES = setOf("gpt", "openai", "chatgpt", "o1", "o3", "o4")
    private val GEMINI_ALIASES = setOf("gemini", "google")
    private val CLAUDE_ALIASES = setOf("claude", "anthropic")
}
