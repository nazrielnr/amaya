package com.opencode.mobile.domain.security

/**
 * Result of command validation.
 * 
 * The validation system uses a three-tier response:
 * 1. Allowed: Can proceed without user interaction
 * 2. RequiresConfirmation: Potentially dangerous, needs explicit approval
 * 3. Denied: Absolutely blocked, no way to proceed
 */
sealed class ValidationResult {
    
    /**
     * Command is safe and allowed to execute.
     */
    data object Allowed : ValidationResult()
    
    /**
     * Command requires explicit user confirmation.
     * 
     * Used for:
     * - Root commands (su)
     * - Destructive operations (deleting multiple files)
     * - Network operations (git push, curl)
     */
    data class RequiresConfirmation(
        val reason: String,
        val command: String,
        val riskLevel: RiskLevel = RiskLevel.MEDIUM
    ) : ValidationResult()
    
    /**
     * Command is blocked and cannot be executed.
     * 
     * Used for:
     * - Dangerous system commands (rm -rf /, dd)
     * - Access to protected system paths
     * - Blacklisted commands
     */
    data class Denied(
        val reason: String,
        val command: String
    ) : ValidationResult()
}

/**
 * Risk level for operations requiring confirmation.
 */
enum class RiskLevel {
    LOW,    // Minor side effects, easily reversible
    MEDIUM, // Significant changes, may need manual rollback
    HIGH,   // Potentially system-breaking, irreversible
    ROOT    // Requires root access
}

/**
 * Information about a protected path.
 */
data class ProtectedPath(
    val path: String,
    val reason: String,
    val allowRead: Boolean = false,
    val allowWrite: Boolean = false
)
