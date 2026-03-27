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
        var iconType = AgentMapper.getIconType(agent.modelId)
        
        // Fallback for remote agents: if modelId is unrecognized, try matching the agent name.
        if (iconType == null && isRemote) {
            iconType = AgentMapper.getIconType(agent.name)
        }
        
        val finalIconType = iconType ?: "default"
        
        return AgentSelectorItem(
            id = agent.id,
            name = agent.name.ifBlank { "Unnamed Agent" },
            modelId = agent.modelId,
            tagTitle = tagTitle,
            quotaStr = quotaStr,
            quotaLabel = quotaLabel,
            resetTime = resetTime,
            isRemote = isRemote,
            iconType = finalIconType
        )
    }
}
