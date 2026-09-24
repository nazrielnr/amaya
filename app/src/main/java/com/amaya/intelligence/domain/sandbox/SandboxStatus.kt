package com.amaya.intelligence.domain.sandbox

/**
 * Lifecycle state of the Alpine Linux sandbox environment.
 */
sealed class SandboxStatus {
    /**
     * Alpine rootfs is not yet installed in local app storage.
     */
    data object NotInstalled : SandboxStatus()

    /**
     * Installation is actively in progress.
     * @param stage Description of current operation (e.g., "Downloading Rootfs", "Extracting Files")
     * @param progress Value between 0.0f and 1.0f
     */
    data class Installing(
        val stage: String,
        val progress: Float
    ) : SandboxStatus()

    /**
     * Alpine Linux rootfs is installed and ready for execution.
     */
    data class Ready(
        val architecture: LinuxArchitecture,
        val rootfsPath: String,
        val prootAvailable: Boolean,
        val details: String = ""
    ) : SandboxStatus()

    /**
     * Installation or runtime initialization encountered an error.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : SandboxStatus()
}
