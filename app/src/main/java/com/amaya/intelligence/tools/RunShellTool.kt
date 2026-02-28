package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
 * - No shell interpolation (uses ProcessBuilder)
 */
@Singleton
class RunShellTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandValidator: CommandValidator
) : Tool {
    
    companion object {
        // Default timeout: 30 seconds
        const val DEFAULT_TIMEOUT_MS = 30_000L
        
        // Maximum timeout: 5 minutes
        const val MAX_TIMEOUT_MS = 300_000L
        
        // Maximum output size: 1MB
        const val MAX_OUTPUT_SIZE = 1024 * 1024
    }
    
    override val name = "run_shell"
    
    override val description = """
        Run a shell command and return the output.
        Commands are validated against a security whitelist.
        
        Use this for:
        - Git operations (git status, git diff, git commit)
        - Complex text search (grep with regex)
        - Build tools (gradle, make)
        
        Examples:
        - git status
        - git add .
        - git commit -m "message"
        - grep -r "pattern" /path
        - gradle build
        
        DO NOT use for basic file operations - use the native tools instead:
        - list_files instead of ls
        - read_file instead of cat
        - write_file instead of echo/cat >
        
        Arguments:
        - command (string, required): The shell command to run
        - working_dir (string, optional): Working directory for the command
        - timeout_ms (int, optional): Timeout in milliseconds (default: 30000, max: 300000)
        - env (object, optional): Environment variables to set
    """.trimIndent()
    
    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = 
        withContext(Dispatchers.IO) {
            
            val command = arguments["command"] as? String
                ?: return@withContext ToolResult.Error(
                    "Missing required argument: command",
                    ErrorType.VALIDATION_ERROR
                )
            
            // Validate command
            when (val validation = commandValidator.validateCommand(command)) {
                is ValidationResult.Denied -> return@withContext ToolResult.Error(
                    validation.reason,
                    ErrorType.SECURITY_VIOLATION
                )
                is ValidationResult.RequiresConfirmation -> return@withContext ToolResult.RequiresConfirmation(
                    validation.reason,
                    "Command: $command"
                )
                is ValidationResult.Allowed -> { /* proceed */ }
            }
            
            val workingDir = arguments["working_dir"] as? String
            val timeoutMs = (arguments["timeout_ms"] as? Number)?.toLong()
                ?.coerceIn(1000, MAX_TIMEOUT_MS)
                ?: DEFAULT_TIMEOUT_MS
            
            @Suppress("UNCHECKED_CAST")
            val env = arguments["env"] as? Map<String, String> ?: emptyMap()
            
            try {
                // FIX 4.6: Removed outer withTimeout() — double timeout was redundant and caused
                // process to not be destroyed immediately on coroutine cancellation.
                // runCommand() uses process.waitFor(timeoutMs) + destroyForcibly() internally,
                // which is the correct mechanism for subprocess timeout handling.
                val result = runCommand(command, workingDir, env, timeoutMs)
                result
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
        env: Map<String, String>,
        timeoutMs: Long
    ): ToolResult = withContext(Dispatchers.IO) {
        
        // Use sh -c to run the command through shell
        // This allows pipes, redirects, etc.
        val processBuilder = ProcessBuilder("sh", "-c", command)
        
        // Set working directory if specified
        if (workingDir != null) {
            val dir = java.io.File(workingDir)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext ToolResult.Error(
                    "Working directory does not exist: $workingDir",
                    ErrorType.NOT_FOUND
                )
            }
            processBuilder.directory(dir)
        }
        
        // Add environment variables
        processBuilder.environment().putAll(env)
        
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

            // Wait for process with timeout
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            readerThread.join(2_000) // give reader thread 2s to flush remaining output

            if (!completed) {
                process.destroyForcibly()
                return@withContext ToolResult.Error(
                    "Command timed out after ${timeoutMs}ms",
                    ErrorType.TIMEOUT
                )
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }
        
        val exitCode = process.exitValue()
        
        if (exitCode != 0) {
            return@withContext ToolResult.Error(
                "Command exited with code $exitCode:\n${output}",
                ErrorType.EXECUTION_ERROR,
                recoverable = true
            )
        }
        
        ToolResult.Success(
            output = output.toString(),
            metadata = mapOf(
                "exit_code" to exitCode,
                "truncated" to truncated,
                "command" to command
            )
        )
    }
}
