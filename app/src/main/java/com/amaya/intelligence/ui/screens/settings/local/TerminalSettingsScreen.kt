package com.amaya.intelligence.ui.screens.settings.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.TerminalSettings
import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import kotlinx.coroutines.launch

@Composable
fun TerminalSettingsScreen(
    repository: TerminalSettingsRepository,
    onNavigateBack: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.terminal_settings_saved)
    var trusted by remember { mutableStateOf("") }
    var declined by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = repository.getSettings()
        trusted = settings.trustedCommands.joinToString("\n")
        declined = settings.declinedCommands.joinToString("\n")
        loaded = true
    }

    AmayaScaffold(stringResource(R.string.terminal), snackbar, onNavigateBack) {
        AmayaSection(stringResource(R.string.command_policy)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.one_wildcard_pattern_per_line_trusted_commands_run_automatically),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = trusted,
                    onValueChange = { trusted = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.trusted_commands)) },
                    supportingText = { Text(stringResource(R.string.examples_npm_or_npm_run)) },
                    minLines = 6,
                    enabled = loaded,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                OutlinedTextField(
                    value = declined,
                    onValueChange = { declined = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.declined_commands)) },
                    supportingText = { Text(stringResource(R.string.matched_commands_are_rejected_without_review)) },
                    minLines = 4,
                    enabled = loaded,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                Button(
                    onClick = {
                        scope.launch {
                            repository.setSettings(
                                TerminalSettings(
                                    trustedCommands = trusted.lines(),
                                    declinedCommands = declined.lines()
                                )
                            )
                            snackbar.showSnackbar(savedMessage)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
