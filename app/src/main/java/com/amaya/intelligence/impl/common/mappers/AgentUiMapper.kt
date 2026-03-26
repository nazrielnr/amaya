package com.amaya.intelligence.impl.common.mappers

import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.domain.models.AgentSelectorItem

object AgentUiMapper {
    fun mapToSelectorItem(
        agent: AgentConfig,
        isRemote: Boolean = false,
        tagTitle: String? = null,
        quotaStr: String? = null,
        quotaLabel: String? = null,
        resetTime: String? = null
    ): AgentSelectorItem {
        val n = agent.name.lowercase()
        val m = agent.modelId.lowercase()
        val iconType = when {
            n.contains("gpt") || m.contains("gpt") -> "gpt"
            n.contains("gemini") || m.contains("gemini") -> "gemini"
            n.contains("claude") || m.contains("claude") -> "claude"
            else -> "default"
        }
        
        return AgentSelectorItem(
            id = agent.id,
            name = agent.name.ifBlank { "Unnamed Agent" },
            modelId = agent.modelId,
            tagTitle = tagTitle,
            quotaStr = quotaStr,
            quotaLabel = quotaLabel,
            resetTime = resetTime,
            isRemote = isRemote,
            iconType = iconType
        )
    }
}
