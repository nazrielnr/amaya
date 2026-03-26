package com.amaya.intelligence.impl.ide.windsurf

import com.amaya.intelligence.domain.ai.IdeProvider
import com.amaya.intelligence.domain.models.IdeCapabilities
import com.amaya.intelligence.domain.models.IdeInfo

/**
 * Windsurf IDE provider placeholder.
 */
object WindsurfProvider : IdeProvider {
    
    override val ideId: String = "windsurf"
    
    override val info: IdeInfo = IdeInfo(
        id = ideId,
        displayName = "Windsurf",
        description = "Windsurf IDE",
        capabilities = IdeCapabilities(
            requiresConnection = true
        )
    )
    
    override val isEnabled: Boolean = false // Placeholder but clickable
}
