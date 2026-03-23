package com.amaya.intelligence.ui.components.shared

/**
 * Utility for determining context window size based on model name.
 * Used by SessionInfoSheet to display context window information.
 */
object ContextWindowUtils {
    
    /**
     * Returns the context window size for a given model name.
     * @param modelName The model identifier (e.g., "gpt-4o", "claude-3-5-sonnet")
     * @return Context window size as a formatted string (e.g., "128K", "200K", "1M")
     */
    fun getContextWindow(modelName: String): String = when {
        modelName.contains("gpt-4o", ignoreCase = true) -> "128K"
        modelName.contains("gpt-4", ignoreCase = true) -> "128K"
        modelName.contains("gpt-3.5", ignoreCase = true) -> "16K"
        modelName.contains("claude-3-5", ignoreCase = true) -> "200K"
        modelName.contains("claude", ignoreCase = true) -> "200K"
        modelName.contains("gemini-1.5", ignoreCase = true) -> "1M"
        modelName.contains("gemini", ignoreCase = true) -> "128K"
        modelName.contains("mistral", ignoreCase = true) -> "32K"
        modelName.contains("deepseek", ignoreCase = true) -> "64K"
        modelName.contains("llama", ignoreCase = true) -> "128K"
        else -> "∞"
    }
    
    /**
     * Formats a token count for display (e.g., 1500 -> "1.5k", 2000000 -> "2.0M")
     */
    fun formatTokenCount(count: Int): String = when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
