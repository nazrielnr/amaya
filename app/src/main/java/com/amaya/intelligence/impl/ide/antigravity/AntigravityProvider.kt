package com.amaya.intelligence.impl.ide.antigravity

import com.amaya.intelligence.domain.ai.IdeProvider
import com.amaya.intelligence.domain.models.IdeCapabilities
import com.amaya.intelligence.domain.models.IdeInfo

/**
 * Antigravity IDE provider implementation.
 * Provides metadata and factory methods for Antigravity connections.
 */
object AntigravityProvider : IdeProvider {
    
    override val ideId: String = "antigravity"
    
    override val info: IdeInfo = IdeInfo(
        id = ideId,
        displayName = "Antigravity",
        description = "Google DeepMind AI IDE",
        capabilities = IdeCapabilities(
            supportsStreaming = true,
            supportsThinking = true,
            supportsWorkspaces = true,
            supportsProjectFiles = true,
            supportsModels = true,
            supportsConversations = true,
            supportsToolExecution = true,
            requiresConnection = true,
            supportsMcp = false
        ),
        defaultPort = 8765,
        defaultIpPrefix = "192.168.1."
    )
    
    override val isEnabled: Boolean = true
}
