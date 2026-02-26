package com.opencode.mobile.domain.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security guardrails for command and path validation.
 * 
 * This is the "Shield" module of the AI Coding Agent. Every tool call
 * from the AI goes through validation before execution.
 * 
 * SECURITY PRINCIPLES:
 * 1. Whitelist over Blacklist: Only allow known-safe commands
 * 2. Defense in Depth: Multiple layers of validation
 * 3. Fail Secure: When in doubt, block the operation
 * 4. User in the Loop: Dangerous operations require explicit confirmation
 */
@Singleton
class CommandValidator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // ====================================================================
        // COMMAND WHITELIST
        // ====================================================================
        
        /**
         * Commands that are always allowed.
         * These are safe, read-only or low-impact commands.
         */
        private val ALWAYS_ALLOWED = setOf(
            "echo", "printf", "cat", "head", "tail",
            "grep", "awk", "sed", "cut", "sort", "uniq",
            "wc", "tr", "diff", "patch",
            "ls", "find", "which", "whereis", "file",
            "pwd", "basename", "dirname", "realpath",
            "date", "cal", "uptime",
            // Node.js/npm commands - handled by RunShellTool with NodeRunner
            "npm", "node", "npx",
            // Background task management commands
            "task_status", "task_stop"
        )
        
        /**
         * Commands allowed but may require confirmation for certain args.
         */
        private val CONDITIONALLY_ALLOWED = setOf(
            "git", "adb", "logcat", "am", "pm",
            "mkdir", "touch", "cp", "mv",
            "chmod", "chown",
            "curl", "wget",
            "tar", "zip", "unzip", "gzip", "gunzip"
        )
        
        // ====================================================================
        // COMMAND BLACKLIST
        // ====================================================================
        
        /**
         * Commands that are NEVER allowed.
         * These can cause irreversible system damage.
         */
        private val ALWAYS_BLOCKED = setOf(
            "rm", "rmdir",              // Use our safe delete instead
            "dd",                       // Can destroy disk
            "mkfs", "format",           // Filesystem destruction
            "reboot", "shutdown", "poweroff",
            "su", "sudo",               // Blocked here, handled separately
            "mount", "umount",          // Filesystem operations
            "insmod", "rmmod", "modprobe", // Kernel modules
            "iptables", "ip6tables",    // Network manipulation
            "init", "systemctl",        // System services
            "setenforce",               // SELinux
            "factory_reset"             // Factory reset
        )
        
        /**
         * Dangerous argument patterns that block even allowed commands.
         */
        private val DANGEROUS_PATTERNS = listOf(
            Regex("""-rf\s+/"""),                    // rm -rf /
            Regex("""--no-preserve-root"""),         // Bypass root protection
            Regex(""">\s*/dev/(sd|hd|nvme)"""),     // Write to disk device
            Regex("""\|\s*sh\b"""),                  // Pipe to shell
            Regex("""\|\s*bash\b"""),                // Pipe to bash
            Regex("""`[^`]+`"""),                    // Command substitution
            Regex("""\$\([^)]+\)"""),                // Command substitution
            Regex(""";\s*rm\b"""),                   // Chained rm
            Regex("""&&\s*rm\b"""),                  // Chained rm
            Regex("""\|\|\s*rm\b"""),                // Chained rm
            Regex("""chmod\s+777"""),                // Overly permissive
            Regex("""chmod\s+\+s"""),                // Setuid bit
            Regex(""">\s*/etc/"""),                  // Write to /etc
            Regex(""">\s*/system/""")                // Write to /system
        )
        
        // ====================================================================
        // PROTECTED PATHS
        // ====================================================================
        
        /**
         * System paths that should never be modified.
         */
        private val PROTECTED_PATHS = listOf(
            ProtectedPath("/system", "Android system partition", allowRead = true),
            ProtectedPath("/vendor", "Vendor partition", allowRead = true),
            ProtectedPath("/proc", "Kernel process info", allowRead = true),
            ProtectedPath("/sys", "Kernel sysfs", allowRead = true),
            ProtectedPath("/dev", "Device files"),
            ProtectedPath("/root", "Root home directory"),
            ProtectedPath("/sbin", "System binaries"),
            ProtectedPath("/init", "Init scripts"),
            ProtectedPath("/data/data", "Other apps' data"),
            ProtectedPath("/data/app", "Installed APKs"),
            ProtectedPath("/data/system", "System settings")
        )
    }
    
    // Current app's data directory (safe to access)
    private val appDataDir: String by lazy {
        context.filesDir.absolutePath.substringBefore("/files")
    }
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    /**
     * Validate a shell command before execution.
     */
    fun validateCommand(command: String): ValidationResult {
        val trimmed = command.trim()
        
        // Empty command check
        if (trimmed.isEmpty()) {
            return ValidationResult.Denied("Empty command", command)
        }
        
        // Extract the base command (first word)
        val baseCommand = extractBaseCommand(trimmed)
        
        // Check blacklist first
        if (baseCommand in ALWAYS_BLOCKED) {
            return ValidationResult.Denied(
                "Command '$baseCommand' is blocked for safety",
                command
            )
        }
        
        // Check for root command
        if (trimmed.startsWith("su ") || trimmed == "su") {
            return ValidationResult.RequiresConfirmation(
                "This command requires root access",
                command,
                RiskLevel.ROOT
            )
        }
        
        // Check dangerous patterns
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return ValidationResult.Denied(
                    "Command contains dangerous pattern: ${pattern.pattern}",
                    command
                )
            }
        }
        
        // Check whitelist
        if (baseCommand in ALWAYS_ALLOWED) {
            return ValidationResult.Allowed
        }
        
        // Check conditional commands
        if (baseCommand in CONDITIONALLY_ALLOWED) {
            return validateConditionalCommand(baseCommand, trimmed)
        }
        
        // Unknown command - require confirmation
        return ValidationResult.RequiresConfirmation(
            "Unknown command '$baseCommand' requires confirmation",
            command,
            RiskLevel.MEDIUM
        )
    }
    
    /**
     * Validate a file path for read or write access.
     */
    fun validatePath(path: String, isWrite: Boolean): ValidationResult {
        val normalizedPath = normalizePath(path)
        
        // Check if it's our app's directory (always allowed)
        if (normalizedPath.startsWith(appDataDir)) {
            return ValidationResult.Allowed
        }
        
        // Check protected paths
        for (protected in PROTECTED_PATHS) {
            if (normalizedPath.startsWith(protected.path)) {
                // Special case: reading from own data directory
                if (normalizedPath.startsWith("/data/data/${context.packageName}")) {
                    return ValidationResult.Allowed
                }
                
                if (isWrite && !protected.allowWrite) {
                    return ValidationResult.Denied(
                        "Cannot write to protected path: ${protected.reason}",
                        path
                    )
                }
                
                if (!isWrite && !protected.allowRead) {
                    return ValidationResult.Denied(
                        "Cannot read from protected path: ${protected.reason}",
                        path
                    )
                }
                
                // Reading from protected but readable paths requires confirmation
                if (!isWrite && protected.allowRead) {
                    return ValidationResult.RequiresConfirmation(
                        "Accessing system path: ${protected.reason}",
                        path,
                        RiskLevel.LOW
                    )
                }
            }
        }
        
        // Check for path traversal attempts
        if (containsPathTraversal(normalizedPath)) {
            return ValidationResult.Denied(
                "Path traversal detected",
                path
            )
        }
        
        return ValidationResult.Allowed
    }
    
    /**
     * Check if a tool operation is allowed.
     */
    fun validateToolCall(
        toolName: String,
        arguments: Map<String, Any?>
    ): ValidationResult {
        return when (toolName) {
            "run_shell" -> {
                val command = arguments["command"] as? String ?: ""
                validateCommand(command)
            }
            
            "read_file" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = false)
            }
            
            "write_file" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = true)
            }
            
            "delete_file" -> {
                val path = arguments["path"] as? String ?: ""
                val result = validatePath(path, isWrite = true)
                
                // Deletion always requires confirmation
                if (result is ValidationResult.Allowed) {
                    ValidationResult.RequiresConfirmation(
                        "Confirm file deletion",
                        path,
                        RiskLevel.MEDIUM
                    )
                } else result
            }
            
            "list_files", "create_directory" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = false)
            }
            
            else -> ValidationResult.Allowed
        }
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun extractBaseCommand(command: String): String {
        // Handle env vars at the start (e.g., "VAR=value command")
        val withoutEnv = command.replace(Regex("""^\s*\w+=\S+\s+"""), "")
        
        // Get first word
        return withoutEnv.split(Regex("""\s+""")).firstOrNull() ?: ""
    }
    
    private fun validateConditionalCommand(
        baseCommand: String,
        fullCommand: String
    ): ValidationResult {
        return when (baseCommand) {
            "git" -> {
                // Allow most git commands, require confirmation for push/reset
                when {
                    fullCommand.contains("push") -> ValidationResult.RequiresConfirmation(
                        "Git push will modify remote repository",
                        fullCommand,
                        RiskLevel.MEDIUM
                    )
                    fullCommand.contains("reset --hard") -> ValidationResult.RequiresConfirmation(
                        "Git reset --hard will discard uncommitted changes",
                        fullCommand,
                        RiskLevel.HIGH
                    )
                    fullCommand.contains("clean -fd") -> ValidationResult.RequiresConfirmation(
                        "Git clean will delete untracked files",
                        fullCommand,
                        RiskLevel.HIGH
                    )
                    else -> ValidationResult.Allowed
                }
            }
            
            "mv", "cp" -> {
                // Check if destination is protected
                val parts = fullCommand.split(Regex("""\s+"""))
                val destination = parts.lastOrNull() ?: ""
                validatePath(destination, isWrite = true)
            }
            
            "curl", "wget" -> ValidationResult.RequiresConfirmation(
                "Network request to external URL",
                fullCommand,
                RiskLevel.MEDIUM
            )
            
            "chmod", "chown" -> ValidationResult.RequiresConfirmation(
                "Changing file permissions",
                fullCommand,
                RiskLevel.HIGH
            )
            
            else -> ValidationResult.Allowed
        }
    }
    
    private fun normalizePath(path: String): String {
        // Resolve relative paths and remove double slashes
        return path
            .replace(Regex("""//+"""), "/")
            .removeSuffix("/")
    }
    
    private fun containsPathTraversal(path: String): Boolean {
        val segments = path.split("/")
        var depth = 0
        
        for (segment in segments) {
            when (segment) {
                ".." -> {
                    depth--
                    if (depth < 0) return true
                }
                ".", "" -> { /* ignore */ }
                else -> depth++
            }
        }
        
        return false
    }
}
