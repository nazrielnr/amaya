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
        val iconType = AgentMapper.getIconType(agent.modelId) ?: "default"
        
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
