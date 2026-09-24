package com.amaya.intelligence.domain.security

import com.amaya.intelligence.data.repository.TerminalSettings
import com.amaya.intelligence.data.repository.commandMatchesWildcard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {
    @Test
    fun `wildcard matches the full normalized command`() {
        assertTrue(commandMatchesWildcard("npm run build", "npm *"))
        assertTrue(commandMatchesWildcard("npm run build", "npm run *"))
        assertFalse(commandMatchesWildcard("npm install", "npm run *"))
        assertFalse(commandMatchesWildcard("prefix npm run build", "npm *"))
    }

    @Test
    fun `trusted shell grammar is allowed`() {
        val settings = TerminalSettings(
            trustedCommands = listOf("echo *", "printf *"),
            declinedCommands = emptyList()
        )
        listOf(
            "echo hello > out.txt",
            "printf x | grep x",
            "echo first && echo second",
            "echo $(pwd)"
        ).forEach { command ->
            assertTrue(command, validator().validateCommand(command, settings) is ValidationResult.Allowed)
        }
    }

    @Test
    fun `declined command wins over trusted wildcard`() {
        val settings = TerminalSettings(
            trustedCommands = listOf("npm *"),
            declinedCommands = listOf("npm publish*")
        )
        assertTrue(validator().validateCommand("npm run build", settings) is ValidationResult.Allowed)
        assertTrue(validator().validateCommand("npm publish", settings) is ValidationResult.Denied)
    }

    @Test
    fun `unmatched command requires review`() {
        // autoApproveNonDestructive defaults to true, which would auto-allow a harmless command;
        // the review path is what this test pins down, so it disables auto-approval explicitly.
        val result = validator().validateCommand(
            "custom-linter --check",
            TerminalSettings(trustedCommands = emptyList(), autoApproveNonDestructive = false)
        )
        assertTrue(result is ValidationResult.RequiresConfirmation)
    }

    @Test
    fun `host destructive boundary cannot be trusted`() {
        val trustAll = TerminalSettings(trustedCommands = listOf("*"))
        listOf(
            "rm -rf /",
            "mkfs.ext4 /dev/block/test",
            "dd if=/dev/zero of=/dev/sda",
            "echo x > /system/build.prop"
        ).forEach { command ->
            assertTrue(command, validator().validateCommand(command, trustAll) is ValidationResult.Denied)
        }
    }

    @Test
    fun `autoApproveAll allows commands without confirmation but preserves hard blocks and declined`() {
        val allSettings = TerminalSettings(
            autoApproveAll = true,
            declinedCommands = listOf("git push*")
        )
        // Commands that normally require confirmation should now be Allowed
        assertTrue(validator().validateCommand("rm test.txt", allSettings) is ValidationResult.Allowed)
        assertTrue(validator().validateCommand("git commit -m 'wip'", allSettings) is ValidationResult.Allowed)
        assertTrue(validator().validateCommand("curl https://api.example.com", allSettings) is ValidationResult.Allowed)

        // Declined pattern is still Denied
        assertTrue(validator().validateCommand("git push origin main", allSettings) is ValidationResult.Denied)

        // Hard blocks are still Denied
        assertTrue(validator().validateCommand("rm -rf /", allSettings) is ValidationResult.Denied)
    }

    @Test
    fun `git branch and check-ignore are non-destructive`() {
        val safeSettings = TerminalSettings(autoApproveNonDestructive = true)
        assertTrue(validator().validateCommand("git branch", safeSettings) is ValidationResult.Allowed)
        assertTrue(validator().validateCommand("git branch -a", safeSettings) is ValidationResult.Allowed)
        assertTrue(validator().validateCommand("git status", safeSettings) is ValidationResult.Allowed)
    }

    @Test
    fun `useLinuxSandbox bypasses host workspace boundary for container paths`() {
        val hostSettings = TerminalSettings(useLinuxSandbox = false, autoApproveAll = true)
        val sandboxSettings = TerminalSettings(useLinuxSandbox = true, autoApproveAll = true)
        val tempWorkspace = java.nio.file.Files.createTempDirectory("workspace").toFile().absolutePath

        val hostResult = validator().validateToolCall(
            toolName = "run_shell",
            arguments = mapOf("command" to "cat /etc/apk/repositories"),
            terminalSettings = hostSettings,
            workspacePath = tempWorkspace
        )
        // On host shell, accessing /etc outside workspace is blocked
        assertTrue(hostResult is ValidationResult.Denied)

        val sandboxResult = validator().validateToolCall(
            toolName = "run_shell",
            arguments = mapOf("command" to "cat /etc/apk/repositories"),
            terminalSettings = sandboxSettings,
            workspacePath = tempWorkspace
        )
        // In Linux sandbox, container internal paths are allowed
        assertTrue(sandboxResult is ValidationResult.Allowed)
    }

    private fun validator(): CommandValidator = CommandValidator(
        object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): java.io.File = java.nio.file.Files.createTempDirectory("command-policy").toFile()
        }
    )
}
