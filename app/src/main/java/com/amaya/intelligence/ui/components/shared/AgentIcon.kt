package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import com.amaya.intelligence.impl.common.mappers.AgentMapper

object AgentIcon {
    data class Spec(
        val resId: Int,
        val tintable: Boolean
    )

    /**
     * Return a canonical icon key for the model id, used by callers to pick resources.
     * Values: openai, grok, groq, kimi, zai, gemini, claude, or null when unknown.
     */
    fun getType(modelId: String): String? {
        return AgentMapper.getIconType(modelId)
    }

    fun resolve(modelId: String, isDarkTheme: Boolean): Spec? {
        return resolveByType(getType(modelId), isDarkTheme)
    }

    fun resolveByType(iconType: String?, isDarkTheme: Boolean): Spec? {
        return when (iconType) {
            "openai" -> Spec(if (isDarkTheme) R.drawable.ic_openai_dark else R.drawable.ic_openai_light, true)
            "grok" -> Spec(R.drawable.ic_grok, true)
            "groq" -> Spec(R.drawable.ic_groq, true)
            "kimi" -> Spec(if (isDarkTheme) R.drawable.ic_kimi_dark else R.drawable.ic_kimi_light, false)
            "zai" -> Spec(R.drawable.ic_zai, true)
            "deepseek" -> Spec(R.drawable.ic_deepseek, false)
            "meta" -> Spec(R.drawable.ic_meta, false)
            "minimax" -> Spec(R.drawable.ic_minimax, false)
            "mistral" -> Spec(R.drawable.ic_mistral, false)
            "qwen" -> Spec(R.drawable.ic_qwen, false)
            "gemini" -> Spec(R.drawable.ic_gemini, false)
            "claude" -> Spec(R.drawable.ic_claude, false)
            else -> null
        }
    }

    fun isTintable(iconType: String?): Boolean {
        return iconType in setOf("openai", "grok", "groq", "zai")
    }
}
