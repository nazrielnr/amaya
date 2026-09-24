package com.amaya.intelligence.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.terminalSettingsStore by preferencesDataStore(name = "terminal_settings")

data class TerminalSettings(
    val trustedCommands: List<String> = DEFAULT_TRUSTED_COMMANDS,
    val declinedCommands: List<String> = emptyList(),
    /**
     * Auto-approve commands that are not in Trusted Commands when they have no destructive
     * impact (read-only commands, MCP invocations, builds, scripts, …) so the user is not
     * prompted repeatedly. Destructive commands (deletion, overwrite, permission changes,
     * effectful git subcommands, …) always require review.
     */
    val autoApproveNonDestructive: Boolean = true,
    /**
     * Fully auto-approve all workspace commands without confirmation prompts.
     * When enabled, the user is not prompted for any shell command or file deletion
     * within the workspace boundaries. Hard system safety boundaries (e.g. rm -rf /)
     * and declined patterns still remain enforced.
     */
    val autoApproveAll: Boolean = false,
    /**
     * Route terminal execution through PRoot + Alpine Linux sandbox container
     * instead of standard /system/bin/sh Toybox shell.
     */
    val useLinuxSandbox: Boolean = false
) {
    companion object {
        val DEFAULT_TRUSTED_COMMANDS = listOf(
            "pwd", "date", "uptime", "which *", "where *", "ls *", "cat *", "head *", "tail *", "grep *", "diff *", "find *"
        )
    }
}

interface TerminalSettingsRepository {
    suspend fun getSettings(): TerminalSettings
    suspend fun setSettings(settings: TerminalSettings)
}

@Singleton
class DataStoreTerminalSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : TerminalSettingsRepository {
    override suspend fun getSettings(): TerminalSettings = context.terminalSettingsStore.data.map { prefs ->
        TerminalSettings(
            trustedCommands = prefs[KEY_TRUSTED]?.sorted() ?: TerminalSettings.DEFAULT_TRUSTED_COMMANDS,
            declinedCommands = prefs[KEY_DECLINED]?.sorted().orEmpty(),
            autoApproveNonDestructive = prefs[KEY_AUTO_APPROVE_NON_DESTRUCTIVE] ?: true,
            autoApproveAll = prefs[KEY_AUTO_APPROVE_ALL] ?: false,
            useLinuxSandbox = prefs[KEY_USE_LINUX_SANDBOX] ?: false
        )
    }.first()

    override suspend fun setSettings(settings: TerminalSettings) {
        context.terminalSettingsStore.edit { prefs ->
            prefs[KEY_TRUSTED] = normalizePatterns(settings.trustedCommands).toSet()
            prefs[KEY_DECLINED] = normalizePatterns(settings.declinedCommands).toSet()
            prefs[KEY_AUTO_APPROVE_NON_DESTRUCTIVE] = settings.autoApproveNonDestructive
            prefs[KEY_AUTO_APPROVE_ALL] = settings.autoApproveAll
            prefs[KEY_USE_LINUX_SANDBOX] = settings.useLinuxSandbox
        }
    }

    private fun normalizePatterns(patterns: List<String>): List<String> = patterns
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter(String::isNotBlank)
        .distinct()

    private companion object {
        val KEY_TRUSTED = stringSetPreferencesKey("trusted_commands")
        val KEY_DECLINED = stringSetPreferencesKey("declined_commands")
        val KEY_AUTO_APPROVE_NON_DESTRUCTIVE = booleanPreferencesKey("auto_approve_non_destructive")
        val KEY_AUTO_APPROVE_ALL = booleanPreferencesKey("auto_approve_all")
        val KEY_USE_LINUX_SANDBOX = booleanPreferencesKey("use_linux_sandbox")
    }
}

internal fun commandMatchesWildcard(command: String, pattern: String): Boolean {
    val normalizedCommand = command.trim().replace(Regex("\\s+"), " ")
    val normalizedPattern = pattern.trim().replace(Regex("\\s+"), " ")
    if (normalizedPattern.isBlank()) return false
    val regex = buildString {
        append('^')
        normalizedPattern.split('*').forEachIndexed { index, part ->
            if (index > 0) append(".*")
            append(Regex.escape(part))
        }
        append('$')
    }
    return Regex(regex).matches(normalizedCommand)
}
