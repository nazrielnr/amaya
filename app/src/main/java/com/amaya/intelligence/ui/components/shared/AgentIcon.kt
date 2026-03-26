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
            "openai" -> Spec(R.drawable.ic_openai, true)
            "grok" -> Spec(R.drawable.ic_grok, true)
            "groq" -> Spec(R.drawable.ic_groq, true)
            "kimi" -> Spec(R.drawable.ic_kimi, true)
            "zai" -> Spec(R.drawable.ic_zai, true)
            "deepseek" -> Spec(R.drawable.ic_deepseek, true)
            "meta" -> Spec(R.drawable.ic_meta, true)
            "minimax" -> Spec(R.drawable.ic_minimax, true)
            "mistral" -> Spec(R.drawable.ic_mistral, true)
            "qwen" -> Spec(R.drawable.ic_qwen, true)
            "gemini" -> Spec(R.drawable.ic_gemini, true)
            "claude" -> Spec(R.drawable.ic_claude, true)
            else -> null
        }
    }

    fun isTintable(iconType: String?): Boolean {
        return resolveByType(iconType, false) != null
    }
}
