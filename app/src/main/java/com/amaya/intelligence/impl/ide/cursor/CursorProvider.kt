package com.amaya.intelligence.impl.ide.cursor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import com.amaya.intelligence.domain.ai.IdeProvider
import com.amaya.intelligence.domain.models.IdeCapabilities
import com.amaya.intelligence.domain.models.IdeInfo

/**
 * Cursor IDE provider placeholder.
 */
object CursorProvider : IdeProvider {
    
    override val ideId: String = "cursor"
    
    override val info: IdeInfo = IdeInfo(
        id = ideId,
        displayName = "Cursor",
        description = "Cursor AI Code Editor",
        icon = Icons.Default.Terminal,
        capabilities = IdeCapabilities(
            requiresConnection = true
        )
    )
    
    override val isEnabled: Boolean = false // Placeholder but clickable
}
