package com.amaya.intelligence.domain.models

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Capability flags for IDE providers.
 * Used by UI to show/hide features based on what the IDE supports.
 */
data class IdeCapabilities(
    val supportsStreaming: Boolean = true,
    val supportsThinking: Boolean = true,
    val supportsWorkspaces: Boolean = true,
    val supportsProjectFiles: Boolean = true,
    val supportsModels: Boolean = true,
    val supportsConversations: Boolean = true,
    val supportsToolExecution: Boolean = true,
    val requiresConnection: Boolean = true,  // false for local
    val supportsMcp: Boolean = false
)

/**
 * Metadata about an IDE provider.
 * Used by UI to display IDE information without knowing implementation details.
 */
data class IdeInfo(
    val id: String,                    // "antigravity", "cursor", "windsurf"
    val displayName: String,           // "Antigravity", "Cursor", "Windsurf"
    val description: String,           // "Google DeepMind AI IDE"
    val icon: ImageVector? = null,     // IDE icon (set by impl layer)
    val capabilities: IdeCapabilities = IdeCapabilities(),
    val defaultPort: Int = 8765,
    val defaultIpPrefix: String = "192.168.1."
)
