package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.ui.screens.settings.shared.SettingsSectionCard

@Composable
fun McpServerList(
    servers: List<McpServerConfig>,
    iconPalettes: List<Brush>,
    onServerClick: (McpServerConfig) -> Unit,
    onToggleEnabled: (McpServerConfig, Boolean) -> Unit,
    onDelete: (McpServerConfig) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val activeServers = servers.filter { it.enabled }
    val disabledServers = servers.filter { !it.enabled }

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
        if (servers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No MCP servers",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Tap + to add an MCP server",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(Modifier.height(32.dp))
                        McpFormatGuide()
                    }
                }
            }
        } else {
            if (activeServers.isNotEmpty()) {
                item {
                    SettingsSectionCard(title = "Active Servers") {
                        activeServers.forEachIndexed { index, server ->
                            val paletteIndex = activeServers.indexOf(server) % iconPalettes.size
                            McpServerCard(
                                server = server,
                                iconBrush = iconPalettes[paletteIndex],
                                onToggle = { enabled -> onToggleEnabled(server, enabled) },
                                onEdit = { onServerClick(server) },
                                onDelete = { onDelete(server) }
                            )
                            if (index < activeServers.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }

            if (disabledServers.isNotEmpty()) {
                item {
                    SettingsSectionCard(title = "Disabled Servers") {
                        disabledServers.forEachIndexed { index, server ->
                            val paletteIndex = (disabledServers.indexOf(server) + 3) % iconPalettes.size
                            McpServerCard(
                                server = server,
                                iconBrush = iconPalettes[paletteIndex],
                                onToggle = { enabled -> onToggleEnabled(server, enabled) },
                                onEdit = { onServerClick(server) },
                                onDelete = { onDelete(server) }
                            )
                            if (index < disabledServers.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                McpFormatGuide()
            }
        }
    }
}
