package com.amaya.intelligence.domain.security

import android.content.Context
import com.amaya.intelligence.data.repository.TerminalSettings
import com.amaya.intelligence.data.repository.commandMatchesWildcard
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
        /** Commands that remain blocked even when a wildcard marks them trusted. */
        private val HARD_BLOCK_PATTERNS = listOf(
            Regex("(?i)(^|[;&|]\\s*)rm\\s+-[^\\n]*r[^\\n]*f[^\\n]*\\s+/(?:\\s|$|\\*)"),
            Regex("(?i)\\b--no-preserve-root\\b"),
            Regex("(?i)(^|[;&|]\\s*)(mkfs(?:\\.[a-z0-9]+)?|factory_reset)\\b"),
            Regex("(?i)>\\s*/(?:dev/(?:sd|hd|nvme)|system/|vendor/|proc/|sys/)"),
            Regex("(?i)\\bdd\\b[^\\n]*(?:of=/dev/|if=/dev/(?:zero|random))"),
            Regex("(?i)\\|\\s*(?:sh|bash|dash|zsh|ash)\\b"),
            Regex("(?i)\\bchmod\\s+(?:777|\\+s)\\b")
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

        // ====================================================================
        // NON-DESTRUCTIVE COMMAND CLASSIFIER (auto-approve)
        // ====================================================================

        /**
         * Read-only git invocations (exact prefix match; the following token must be a flag/arg,
         * never a subcommand that writes). Effectful ones like commit/push/checkout/reset/fetch are
         * deliberately absent: they fall through to user review via [isEffectfulGitCommand].
         */
        private val NON_DESTRUCTIVE_GIT_PREFIXES = listOf(
            "git status", "git diff", "git log", "git show", "git blame", "git describe",
            "git rev-parse", "git ls-files", "git ls-tree",
            "git remote -v", "git remote show", "git remote get-url", "git remote list",
            "git var", "git reflog", "git help", "git --version", "git -h", "git --help",
            "git stash list", "git stash show",
            "git tag -l", "git tag --list", "git tag -n",
            "git branch", "git branch -a", "git branch -r", "git branch --list", "git branch -v", "git branch --show-current",
            "git config -l", "git config --list", "git config --get", "git check-ignore"
        )

        /** Redirection/appends are writes — they require review even when the verb itself is safe. */
        private const val NON_DESTRUCTIVE_WRITE_MARKER = '>'

        /** Command/process substitution and backticks are opaque — they require review. */
        private val NON_DESTRUCTIVE_SUBSTITUTION_MARKERS = listOf("$(", "`", "<(")

        /**
         * Dangerous verbs that can appear anywhere in a command (including inside chains and
         * `find … -exec`). Deletion, overwrite, permission changes, privilege escalation,
         * process control, power, mounts, and network downloads all require review.
         */
        private val NON_DESTRUCTIVE_DANGEROUS_VERBS = Regex(
            "\\b(rm|rmdir|unlink|shred|mv|dd|mkfs|fdisk|parted|format|wipe|erase|chmod|chown|chgrp|sudo|su|kill|pkill|killall|reboot|shutdown|halt|poweroff|mount|umount|tee|truncate|ln|curl|wget|exec|delete|uninstall)\\b"
        )
    }

    // Current app's data directory (safe to access)
    private val appDataDir: String by lazy {
        context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Validate a shell command before execution.
     */
    fun validateCommand(command: String, settings: TerminalSettings = TerminalSettings()): ValidationResult {
        val normalized = command.trim()
        if (normalized.isEmpty()) return ValidationResult.Denied("Empty command", command)
        HARD_BLOCK_PATTERNS.firstOrNull { it.containsMatchIn(normalized) }?.let {
            return ValidationResult.Denied("Command is blocked by the host safety boundary", command)
        }
        if (settings.declinedCommands.any { commandMatchesWildcard(normalized, it) }) {
            return ValidationResult.Denied("Command matches a declined terminal pattern", command)
        }
        if (settings.autoApproveAll) {
            return ValidationResult.Allowed
        }
        if (settings.trustedCommands.any { commandMatchesWildcard(normalized, it) }) {
            return ValidationResult.Allowed
        }
        // Auto-approve commands that are not in Trusted Commands but have no destructive impact
        // (read-only commands, MCP invocations, builds, scripts, …). Only destructive commands
        // fall through to the review dialog.
        if (settings.autoApproveNonDestructive && isNonDestructiveCommand(normalized)) {
            return ValidationResult.Allowed
        }
        return ValidationResult.RequiresConfirmation(
            "Shell command is not in Trusted Commands",
            command,
            RiskLevel.MEDIUM
        )
    }

    /**
     * Validate a file path for read or write access.
     */
    fun validatePath(path: String, isWrite: Boolean): ValidationResult {
        if (path.isBlank()) return ValidationResult.Denied("Path is required", path)
        if (!java.io.File(path).isAbsolute) {
            return ValidationResult.Denied("Path must be host-resolved inside the active workspace.", path)
        }
        if (containsPathTraversal(path)) return ValidationResult.Denied("Path traversal detected", path)
        val normalizedPath = normalizePath(path)

        // FIX 3: Resolve symlinks to prevent symlink-based path traversal bypass.
        // e.g. /sdcard/mylink → /data/data/... would bypass normalized path checks.
        val canonicalPath = try {
            java.io.File(normalizedPath).canonicalPath
        } catch (_: Exception) { normalizedPath }
        if (canonicalPath != normalizedPath) {
            // Re-check canonical path against protected prefixes
            for (protected in PROTECTED_PATHS) {
                if (isWithinPath(canonicalPath, protected.path) &&
                    !isWithinPath(canonicalPath, appDataDir)) {
                    return ValidationResult.Denied(
                        "Symlink traversal into protected path detected: $path → $canonicalPath",
                        path
                    )
                }
            }
        }

        // Check if it's our app's directory (always allowed)
        if (isWithinPath(normalizedPath, appDataDir)) {
            return ValidationResult.Allowed
        }

        // Check protected paths
        for (protected in PROTECTED_PATHS) {
            if (isWithinPath(normalizedPath, protected.path)) {
                // Special case: reading from own data directory
                if (isWithinPath(normalizedPath, appDataDir)) {
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

        return ValidationResult.Allowed
    }

    /**
     * Check if a tool operation is allowed.
     */
    fun validateToolCall(
        toolName: String,
        arguments: Map<String, Any?>,
        terminalSettings: TerminalSettings = TerminalSettings(),
        workspacePath: String? = null
    ): ValidationResult {
        missingToolTarget(toolName, arguments)?.let { return ValidationResult.Denied(it, "") }
        return when (toolName) {
            "run_shell" -> {
                val command = arguments["command"] as? String ?: ""
                val commandResult = validateCommand(command, terminalSettings)
                val workingDir = arguments["working_dir"] as? String
                val pathResult = if (workingDir.isNullOrBlank()) ValidationResult.Allowed
                    else validatePath(workingDir, isWrite = false)
                val combined = combineValidation(commandResult, pathResult)
                // Host-enforced workspace containment: the AI must never leave the active
                // workspace through the host shell. Inside the Linux sandbox, the filesystem is
                // already contained within the Alpine PRoot jail.
                if (!terminalSettings.useLinuxSandbox) {
                    workspacePath?.takeIf { it.isNotBlank() }?.let { root ->
                        shellWorkspaceViolation(command, root)?.let { violation ->
                            return ValidationResult.Denied(violation, command)
                        }
                    }
                }
                combined
            }

            "read_file" -> {
                val paths = (arguments["paths"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }
                if (paths != null) combinePathValidation(paths, isWrite = false)
                else validatePath(arguments["path"] as? String ?: "", isWrite = false)
            }

            "write_file" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = true)
            }

            "delete_file" -> {
                val path = arguments["path"] as? String ?: ""
                val result = validatePath(path, isWrite = true)

                // Deletion requires confirmation unless autoApproveAll is active
                if (result is ValidationResult.Allowed) {
                    if (terminalSettings.autoApproveAll) {
                        ValidationResult.Allowed
                    } else {
                        ValidationResult.RequiresConfirmation(
                            "Confirm file deletion",
                            path,
                            RiskLevel.MEDIUM
                        )
                    }
                } else result
            }

            "list_files" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = false)
            }

            "create_directory" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = true)
            }

            // FIX 2.9: Added missing path validation for edit_file, transfer_file, find_files
            "edit_file" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = true)
            }

            "transfer_file" -> {
                // Validate both source (read) and destination (write)
                val source = arguments["source"] as? String ?: ""
                val destination = arguments["destination"] as? String ?: ""
                val srcResult = validatePath(source, isWrite = false)
                if (srcResult !is ValidationResult.Allowed) return srcResult
                validatePath(destination, isWrite = true)
            }

            "find_files" -> {
                val path = arguments["path"] as? String ?: ""
                validatePath(path, isWrite = false)
            }

            else -> ValidationResult.Allowed
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private fun combinePathValidation(paths: List<String>, isWrite: Boolean): ValidationResult {
        var confirmation: ValidationResult.RequiresConfirmation? = null
        for (path in paths) {
            when (val result = validatePath(path, isWrite)) {
                is ValidationResult.Denied -> return result
                is ValidationResult.RequiresConfirmation -> if (confirmation == null) confirmation = result
                is ValidationResult.Allowed -> Unit
            }
        }
        return confirmation ?: ValidationResult.Allowed
    }

    private fun combineValidation(vararg results: ValidationResult): ValidationResult {
        results.filterIsInstance<ValidationResult.Denied>().firstOrNull()?.let { return it }
        return results.filterIsInstance<ValidationResult.RequiresConfirmation>()
            .maxByOrNull { it.riskLevel.ordinal } ?: ValidationResult.Allowed
    }

    private fun isWithinPath(candidate: String, root: String): Boolean {
        val normalizedRoot = root.trimEnd('/', '\\')
        return candidate == normalizedRoot || candidate.startsWith("$normalizedRoot/") || candidate.startsWith("$normalizedRoot\\")
    }

    private fun isWithin(candidate: java.io.File, root: java.io.File): Boolean =
        candidate.path == root.path || candidate.path.startsWith(root.path.trimEnd(java.io.File.separatorChar) + java.io.File.separator)

    /**
     * Returns a violation message when [command] references absolute paths outside the active
     * workspace or changes directory outside it; null when the command stays inside. Runs for
     * every shell command regardless of trusted patterns so the AI cannot escape the workspace
     * through the shell (the host-injected working directory alone cannot stop `cd`/absolute
     * paths).
     */
    private fun shellWorkspaceViolation(command: String, workspaceRoot: String): String? {
        val root = runCatching { java.io.File(workspaceRoot).canonicalFile }.getOrNull() ?: return null
        // Absolute paths: `cat /sdcard/x`, `rm -rf /data/…`, `git -C /other/repo`, `WORKDIR=/x`, …
        command.split(Regex("\\s+")).forEach { rawToken ->
            val token = rawToken.trim('"', '\'', '(', '[', '=', '<', '>')
            if (token.startsWith('/')) {
                val path = token.trimEnd(',', ';', '&', '|', ')', ']', '"', '\'', '>', '<')
                if (path.startsWith('/')) {
                    val canonical = runCatching { java.io.File(path).canonicalFile }.getOrNull()
                    if (canonical != null && !isWithin(canonical, root)) {
                        return "Shell command references a path outside the active workspace: $path"
                    }
                }
            }
        }
        // Directory changes: `cd` (goes home = outside) and `cd …` that resolves outside.
        command.split(Regex("""(?:;|&&|\|\||\|)""")).forEach { rawSegment ->
            val segment = rawSegment.trim()
            if (segment != "cd" && !segment.startsWith("cd ")) return@forEach
            val target = if (segment == "cd") "" else segment.removePrefix("cd ").trim().substringBefore(' ').trim('"', '\'')
            val resolved = when {
                target.isBlank() -> java.io.File("/")
                java.io.File(target).isAbsolute -> java.io.File(target)
                else -> java.io.File(root, target)
            }
            val canonical = runCatching { resolved.canonicalFile }.getOrNull() ?: return@forEach
            if (!isWithin(canonical, root)) {
                return "Shell command changes directory outside the active workspace: cd ${target.ifBlank { "(home)" }}"
            }
        }
        return null
    }

    // ========================================================================
    // NON-DESTRUCTIVE COMMAND CLASSIFIER (auto-approve)
    // ========================================================================

    /**
     * True when [command] is safe to run without a confirmation prompt.
     *
     * Auto-approve covers commands that are NOT in Trusted Commands but have no destructive
     * impact — read-only commands, MCP invocations, builds, scripts, and so on. Approval is
     * only requested for destructive commands (deletion, overwrite, permission changes,
     * privilege escalation, process control, effectful git subcommands, …). Conservative by
     * design: any ambiguity (redirection, substitution, dangerous verb, hard-block pattern)
     * returns false, which falls back to the normal user-review flow — never to denial.
     */
    fun isNonDestructiveCommand(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false
        if (HARD_BLOCK_PATTERNS.any { it.containsMatchIn(trimmed) }) return false
        if (trimmed.contains(NON_DESTRUCTIVE_WRITE_MARKER)) return false
        if (NON_DESTRUCTIVE_SUBSTITUTION_MARKERS.any { trimmed.contains(it) }) return false
        if (NON_DESTRUCTIVE_DANGEROUS_VERBS.containsMatchIn(trimmed)) return false
        if (isEffectfulGitCommand(trimmed)) return false
        return true
    }

    /**
     * True when any `git` segment of [command] uses a subcommand that writes (commit, push,
     * checkout, reset, fetch, …). Read-only git invocations listed in [NON_DESTRUCTIVE_GIT_PREFIXES]
     * stay safe; everything else (including writes inside chains like `git status && git push`)
     * requires review.
     */
    private fun isEffectfulGitCommand(command: String): Boolean =
        command.split(Regex("""\s*(?:;|&&|\|\||\|)\s*""")).any { rawSegment ->
            val segment = rawSegment.trim()
            if (!segment.startsWith("git")) return@any false
            val normalized = segment.replace(Regex("\\s+"), " ")
            if (normalized == "git") return@any false // bare `git` prints help — safe
            NON_DESTRUCTIVE_GIT_PREFIXES.none { prefix ->
                normalized == prefix || normalized.startsWith("$prefix ")
            }
        }

    private fun normalizePath(path: String): String {
        // Resolve all . and .. segments, remove double slashes
        val parts = path.replace(Regex("""//+"""), "/").split("/")
        val resolved = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                "", "." -> { /* skip */ }
                ".." -> if (resolved.isNotEmpty()) resolved.removeLast()
                else -> resolved.addLast(part)
            }
        }
        val normalized = "/" + resolved.joinToString("/")
        return normalized
    }

    private fun containsPathTraversal(path: String): Boolean {
        // After normalization, path should never contain ".." anymore.
        // But also guard against encoded variants.
        return path.contains("..") ||
            path.contains("%2e%2e", ignoreCase = true) ||
            path.contains("%252e", ignoreCase = true)
    }
}
