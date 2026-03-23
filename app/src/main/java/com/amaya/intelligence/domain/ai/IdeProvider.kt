package com.amaya.intelligence.domain.ai

import com.amaya.intelligence.domain.models.IdeInfo

/**
 * Interface for IDE provider implementations.
 * Follows the Plugin pattern - new IDEs implement this interface.
 */
interface IdeProvider {
    /**
     * Unique identifier for this IDE.
     * Used in SessionMode enum and provider registration.
     */
    val ideId: String
    
    /**
     * IDE metadata for UI display.
     */
    val info: IdeInfo
    
    /**
     * Whether this IDE is currently available/enabled.
     * Can be false for future IDEs not yet implemented.
     */
    val isEnabled: Boolean get() = true
}
