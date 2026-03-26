package com.amaya.intelligence.impl.common.mappers

object AgentMapper {
    fun getIconType(modelId: String): String? {
        val normalized = modelId.lowercase()
        return when {
            matches(normalized, OPENAI_PATTERN) -> "openai"
            matches(normalized, GROK_PATTERN) -> "grok"
            matches(normalized, GROQ_PATTERN) -> "groq"
            matches(normalized, KIMI_PATTERN) -> "kimi"
            matches(normalized, ZAI_PATTERN) -> "zai"
            matches(normalized, DEEPSEEK_PATTERN) -> "deepseek"
            matches(normalized, META_PATTERN) -> "meta"
            matches(normalized, MINIMAX_PATTERN) -> "minimax"
            matches(normalized, MISTRAL_PATTERN) -> "mistral"
            matches(normalized, QWEN_PATTERN) -> "qwen"
            matches(normalized, GEMINI_PATTERN) -> "gemini"
            matches(normalized, CLAUDE_PATTERN) -> "claude"
            else -> null
        }
    }

    private fun matches(value: String, pattern: Regex): Boolean {
        return pattern.containsMatchIn(value)
    }

    private val OPENAI_PATTERN = Regex("(?i)(^|[^a-z0-9])(openai|gpt|chatgpt|o1|o3|o4)([^a-z0-9]|$)")
    private val GROK_PATTERN = Regex("(?i)(^|[^a-z0-9])(grok|xai)([^a-z0-9]|$)")
    private val GROQ_PATTERN = Regex("(?i)(^|[^a-z0-9])(groq)([^a-z0-9]|$)")
    private val KIMI_PATTERN = Regex("(?i)(^|[^a-z0-9])(kimi|moonshot)([^a-z0-9]|$)")
    private val ZAI_PATTERN = Regex("(?i)(^|[^a-z0-9])(zai|zhipu|glm)([^a-z0-9]|$)")
    private val DEEPSEEK_PATTERN = Regex("(?i)(^|[^a-z0-9])(deepseek)([^a-z0-9]|$)")
    private val META_PATTERN = Regex("(?i)(^|[^a-z0-9])(meta|llama|llama2|llama3|llama4|facebook|facebookai)([^a-z0-9]|$)")
    private val MINIMAX_PATTERN = Regex("(?i)(^|[^a-z0-9])(minimax|abab|mini(?:[_ -])?max)([^a-z0-9]|$)")
    private val MISTRAL_PATTERN = Regex("(?i)(^|[^a-z0-9])(mistral|mixtral)([^a-z0-9]|$)")
    private val QWEN_PATTERN = Regex("(?i)(^|[^a-z0-9])(qwen|tongyi|ali[ -_]?qwen)([^a-z0-9]|$)")
    private val GEMINI_PATTERN = Regex("(?i)(^|[^a-z0-9])(gemini|google)([^a-z0-9]|$)")
    private val CLAUDE_PATTERN = Regex("(?i)(^|[^a-z0-9])(claude|anthropic)([^a-z0-9]|$)")
}