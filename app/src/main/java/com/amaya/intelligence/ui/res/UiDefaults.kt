package com.amaya.intelligence.ui.res

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Default values for UI components.
 * Centralizes hardcoded values for easy configuration.
 */
object UiDefaults {
    
    object Connection {
        const val DEFAULT_PORT = 8765
        const val DEFAULT_IP_PREFIX = "192.168.1."
        const val DEFAULT_IP_PLACEHOLDER = "192.168.1.5"
        const val DEFAULT_PORT_PLACEHOLDER = "8765"
    }
    
    object Colors {
        // Use theme colors instead of hardcoded values
        @Composable
        fun connectedColor() = MaterialTheme.colorScheme.primary
        
        @Composable
        fun errorColor() = MaterialTheme.colorScheme.error
        
        @Composable
        fun successColor() = MaterialTheme.colorScheme.primary
    }
}
