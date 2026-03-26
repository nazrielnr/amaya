package com.amaya.intelligence.ui.screens.agent.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.ui.screens.settings.shared.SettingsSectionCard

@Composable
fun AgentList(
    agentConfigs: List<AgentConfig>,
    onAgentClick: (AgentConfig) -> Unit,
    onToggleEnabled: (AgentConfig, Boolean) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val enabledAgents = agentConfigs.filter { it.enabled }
    val disabledAgents = agentConfigs.filter { !it.enabled }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topPadding,
            bottom = 100.dp
        )
    ) {
        if (agentConfigs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No agents yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Tap + to add your first agent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }

        if (enabledAgents.isNotEmpty()) {
            item {
                SettingsSectionCard(title = "Enabled") {
                    enabledAgents.forEachIndexed { index, config ->
                        AgentCard(
                            config = config,
                            onClick = { onAgentClick(config) },
                            onToggleEnabled = { enabled -> onToggleEnabled(config, enabled) }
                        )
                        if (index < enabledAgents.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }

        if (disabledAgents.isNotEmpty()) {
            item {
                SettingsSectionCard(title = "Disabled") {
                    disabledAgents.forEachIndexed { index, config ->
                        AgentCard(
                            config = config,
                            onClick = { onAgentClick(config) },
                            onToggleEnabled = { enabled -> onToggleEnabled(config, enabled) }
                        )
                        if (index < disabledAgents.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}
