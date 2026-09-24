package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure wrapper for running shell commands.
 *
 * WHY SHELL IS NEEDED:
 * ====================
 *
 * While native APIs are preferred for basic file operations,
 * shell commands are necessary for:
 *
 * 1. GIT OPERATIONS
 *    - git clone, commit, push, pull, status, diff
 *    - No native Java API for full git functionality
 *
 * 2. COMPLEX TEXT PROCESSING
 *    - grep with complex regex across multiple files
 *    - sed for stream editing
 *    - awk for data processing
 *
 * 3. ANDROID-SPECIFIC TOOLS
 *    - adb commands
 *    - logcat for log viewing
 *    - am/pm for package management
 *
 * 4. BUILD TOOLS
 *    - gradle, make, etc.
 *
 * SECURITY MEASURES:
 * ==================
 * - All commands go through CommandValidator
 * - Timeout enforcement prevents hanging
 * - Output size limiting prevents memory issues
 * - Android's system shell provides standard terminal grammar
 */
@Singleton
class RunShellTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandValidator: CommandValidator,
    private val terminalSettingsRepository: TerminalSettingsRepository,
    private val linuxSandboxManager: LinuxSandboxManager
) : Tool, ContextAwareTool {

    companion object {
        // Default timeout: 30 seconds
        const val DEFAULT_TIMEOUT_MS = 30_000L

        // Maximum timeout: 5 minutes
        const val MAX_TIMEOUT_MS = 300_000L

        // Maximum output size retained for the model.
        const val MAX_OUTPUT_SIZE = 64 * 1024
    }

    override val name = "run_shell"

    override val description = """
        Run a shell command and return the output.
        Commands are validated against a security whitelist.

        Use this for:
        - Git operations (git status, git diff, git commit)
        - Complex text search (grep with regex)
        - Build tools (gradle, make)

        TIMEOUT RULES (IMPORTANT):
        - Default timeout: ${DEFAULT_TIMEOUT_MS / 1000}s. Max: ${MAX_TIMEOUT_MS / 1000}s.
        - If a command might take longer (e.g. gradle build, git clone), set timeout_ms explicitly.
        - Example: timeout_ms=120000 for a 2-minute build.
        - If timeout is exceeded, the command is cancelled and you will get a TIMEOUT error.
          Do NOT retry the same command — either increase timeout_ms or break the task into smaller steps.

        DO NOT use for basic file operations - use the native tools instead:
        - list_files instead of ls
        - read_file instead of cat
        - write_file instead of echo/cat >

        Arguments:
        - command (string, required): The shell command to run. Must not be empty.
        - working_dir: Host-owned; never supplied by the model
        - timeout_ms (int, optional): Timeout in milliseconds (default: ${DEFAULT_TIMEOUT_MS}, max: ${MAX_TIMEOUT_MS})
    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult =
        withContext(Dispatchers.IO) {

            val command = ((arguments["command"] ?: arguments["cmd"]) as? String)?.trim()
            if (command.isNullOrBlank()) {
                return@withContext ToolResult.Error(
                    "Missing or empty 'command' argument. You must provide a non-empty shell command to run. " +
                    "Do not call run_shell with an empty command string.",
                    ErrorType.VALIDATION_ERROR
                )
            }

            when (val validation = commandValidator.validateCommand(command, terminalSettingsRepository.getSettings())) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(
                    "Command blocked by security policy: ${validation.reason}",
                    ErrorType.SECURITY_VIOLATION
                )
                is ValidationResult.RequiresConfirmation -> {
                    if (!context.confirmed) {
                        // First call — bubble up to ToolExecutor for user confirmation dialog
                        return@withContext ToolResult.RequiresConfirmation(
                            validation.reason,
                            "Command: $command"
                        )
                    }
                    // alreadyConfirmed = true → user already approved, proceed with execution
                }
                is ValidationResult.Allowed -> { /* proceed */ }
            }

            val workingDir = arguments["working_dir"] as? String
            val isApkCommand = command.contains("apk add") || command.contains("apk update") || command.contains("apk upgrade")
            val baseTimeoutMs = if (isApkCommand) 180_000L else DEFAULT_TIMEOUT_MS
            val timeoutMs = ((arguments["timeout_ms"] as? Number)?.toLong()
                ?: (arguments["timeout_seconds"] as? Number)?.let { it.toLong() * 1000L }
                ?: (arguments["timeout"] as? Number)?.let { it.toLong() * 1000L })
                ?.coerceIn(1000, MAX_TIMEOUT_MS)
                ?: baseTimeoutMs

            try {
                // FIX 4.6: Removed outer withTimeout() — double timeout was redundant and caused
                // process to not be destroyed immediately on coroutine cancellation.
                // runCommand() uses process.waitFor(timeoutMs) + destroyForcibly() internally,
                // which is the correct mechanism for subprocess timeout handling.
                runCommand(command, workingDir, timeoutMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: TimeoutCancellationException) {
                ToolResult.Error(
                    "Command timed out after ${timeoutMs}ms",
                    ErrorType.TIMEOUT
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    "Command execution failed: ${e.message}",
                    ErrorType.EXECUTION_ERROR
                )
            }
        }

    private suspend fun runCommand(
        command: String,
        workingDir: String?,
        timeoutMs: Long
    ): ToolResult = withContext(Dispatchers.IO) {

        val settings = terminalSettingsRepository.getSettings()
        val isSandboxActive = settings.useLinuxSandbox && linuxSandboxManager.isReady()

        val processBuilder = if (isSandboxActive) {
            val (cmdList, envMap) = linuxSandboxManager.buildExecution(command, workingDir)
            ProcessBuilder(cmdList).apply {
                val env = environment()
                env.putAll(envMap)
                com.amaya.intelligence.domain.sandbox.LinuxSandboxManager.HOST_LEAK_ENV_VARS.forEach {
                    env.remove(it)
                }
            }
        } else {
            ProcessBuilder("/system/bin/sh", "-c", command)
        }

        // Set working directory if specified and not in sandbox (sandbox mounts workingDir directly)
        if (!isSandboxActive && workingDir != null) {
            val dir = java.io.File(workingDir)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext ToolResult.Error(
                    "Working directory does not exist: $workingDir",
                    ErrorType.NOT_FOUND
                )
            }
            processBuilder.directory(dir)
        }

        // Redirect stderr to stdout for combined output
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        // FIX #3: Drain stdout on a separate thread so the pipe buffer never fills up
        // and blocks the process (which would cause deadlock when waiting below).
        // Also ensures process is always destroyed if coroutine is cancelled.
        val output = StringBuilder()
        var truncated = false

        try {
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            if (output.length + line.length + 1 > MAX_OUTPUT_SIZE) {
                                truncated = true
                                output.append("\n... [output truncated, exceeded ${MAX_OUTPUT_SIZE / 1024}KB]")
                                // Drain remaining to unblock process
                                while (reader.readLine() != null) { /* drain */ }
                                break
                            }
                            if (output.isNotEmpty()) output.append('\n')
                            output.append(line)
                            line = reader.readLine()
                        }
                    }
                } catch (_: Exception) { /* stream closed on process destroy */ }
            }
            readerThread.isDaemon = true
            readerThread.start()

            // Wait off the coroutine thread; cancellation always kills the process.
            val completed = suspendCancellableCoroutine { continuation ->
                val waiter = Thread {
                    val done = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                    continuation.resume(done) { _, _, _ -> }
                }
                waiter.isDaemon = true
                waiter.start()
                continuation.invokeOnCancellation { process.destroyForcibly() }
            }
            readerThread.join(2_000) // give reader thread 2s to flush remaining output

            if (!completed) {
                process.destroyForcibly()
                val timeoutSec = timeoutMs / 1000
                val maxSec = MAX_TIMEOUT_MS / 1000
                return@withContext ToolResult.Error(
                    "TIMEOUT: Command was cancelled after ${timeoutSec}s (timeout_ms=${timeoutMs}). " +
                    "The process has been forcefully terminated. " +
                    "To fix: increase timeout_ms (max ${maxSec}s = ${MAX_TIMEOUT_MS}ms), " +
                    "or break the command into smaller steps. " +
                    "Output captured before timeout:\n${output.toString().takeLast(2000)}",
                    ErrorType.TIMEOUT
                )
            }
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            throw cancelled
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }

        val exitCode = process.exitValue()
        val cleanedOutput = output.toString().replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")

        if (exitCode != 0) {
            return@withContext ToolResult.Error(
                "Command exited with code $exitCode:\n${cleanedOutput}",
                ErrorType.EXECUTION_ERROR,
                recoverable = true
            )
        }

        ToolResult.Success(
            output = cleanedOutput,
            metadata = mapOf(
                "exit_code" to exitCode,
                "truncated" to truncated,
                "command" to command
            )
        )
    }
}
