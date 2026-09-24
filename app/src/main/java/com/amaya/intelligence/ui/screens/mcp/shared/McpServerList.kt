package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.data.remote.mcp.McpServerTestResult
import com.amaya.intelligence.ui.components.shared.SettingsEmptyState
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.settings.shared.SettingsSectionCard
import com.amaya.intelligence.ui.screens.amaya.iosAmayaColors

@Composable
fun McpServerList(
    servers: List<McpServerConfig>,
    onServerClick: (McpServerConfig) -> Unit,
    onToggleEnabled: (McpServerConfig, Boolean) -> Unit,
    onDelete: (McpServerConfig) -> Unit,
    onTest: ((McpServerConfig) -> Unit)? = null,
    testResults: Map<String, McpServerTestResult> = emptyMap(),
    testingServers: Set<String> = emptySet(),
    topPadding: androidx.compose.ui.unit.Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val colors = iosAmayaColors()
    val activeServers = servers.filter { it.enabled }
    val disabledServers = servers.filter { !it.enabled }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AmayaGroupedSettingsTokens.sectionSpacing),
        contentPadding = PaddingValues(
            start = AmayaGroupedSettingsTokens.contentHorizontalPadding,
            end = AmayaGroupedSettingsTokens.contentHorizontalPadding,
            top = topPadding,
            bottom = AmayaGroupedSettingsTokens.screenContentBottomSpacer
        )
    ) {
        if (servers.isEmpty()) {
            item {
                SettingsEmptyState(
                    title = "No MCP servers",
                    subtitle = "Tap + to add an MCP server",
                    icon = Icons.Default.Extension,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AmayaGroupedSettingsTokens.emptyStateListTopSpacing)
                )
                Spacer(Modifier.height(32.dp))
                McpFormatGuide()
            }
        } else {
            if (activeServers.isNotEmpty()) {
                item {
                    SettingsSectionCard(title = "Active Servers") {
                        activeServers.forEachIndexed { index, server ->
                            McpServerCard(
                                server = server,
                                onToggle = { enabled -> onToggleEnabled(server, enabled) },
                                onEdit = { onServerClick(server) },
                                onDelete = { onDelete(server) },
                                onTest = onTest?.let { { it(server) } },
                                testResult = testResults[server.name],
                                isTesting = testingServers.contains(server.name)
                            )
                            if (index < activeServers.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 58.dp, end = 16.dp),
                                    color = colors.separator,
                                    thickness = 0.7.dp
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
                            McpServerCard(
                                server = server,
                                onToggle = { enabled -> onToggleEnabled(server, enabled) },
                                onEdit = { onServerClick(server) },
                                onDelete = { onDelete(server) },
                                onTest = onTest?.let { { it(server) } },
                                testResult = testResults[server.name],
                                isTesting = testingServers.contains(server.name)
                            )
                            if (index < disabledServers.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 58.dp, end = 16.dp),
                                    color = colors.separator,
                                    thickness = 0.7.dp
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
