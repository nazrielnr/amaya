package com.amaya.intelligence.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    currentWorkspace: String?,
    onNavigateToWorkspace: () -> Unit,
    aiSettingsManager: AiSettingsManager,
    onNavigateToPersona: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToReminders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Workspace
            SettingsSection("Workspace") {
                SettingsCard(
                    icon = Icons.Default.Folder,
                    title = "Current Workspace",
                    subtitle = currentWorkspace ?: "Not selected",
                    onClick = onNavigateToWorkspace
                )
            }

            // Agent Configuration
            SettingsSection("Agent Configuration") {
                SettingsCard(
                    icon = Icons.Default.SmartToy,
                    title = "Manage Agents",
                    subtitle = "Add or edit API keys, base URLs, and models",
                    onClick = onNavigateToAgents
                )
            }

            // Persona
            SettingsSection("Persona") {
                SettingsCard(
                    icon = Icons.Default.Person,
                    title = "Personality & Memory",
                    subtitle = "Style, instructions, and AI memory",
                    onClick = onNavigateToPersona
                )
            }

            // Automation
            SettingsSection("Automation") {
                SettingsCard(
                    icon = Icons.Default.Alarm,
                    title = "Reminders & Cron Jobs",
                    subtitle = "Schedule AI-powered reminders and notifications",
                    onClick = onNavigateToReminders
                )
            }

            // Appearance
            SettingsSection("Appearance") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val themes = listOf("system", "light", "dark")
                        val labels = listOf("System", "Light", "Dark")
                        themes.forEachIndexed { index, theme ->
                            SegmentedButton(
                                selected = settings.theme == theme,
                                onClick = { scope.launch { aiSettingsManager.setTheme(theme) } },
                                shape = SegmentedButtonDefaults.itemShape(index, themes.size)
                            ) {
                                Text(labels[index])
                            }
                        }
                    }
                }
            }

            // MCP
            SettingsSection("MCP Servers") {
                val mcpConfig = remember(settings.mcpConfigJson) {
                    com.amaya.intelligence.data.remote.api.McpConfig.fromJson(settings.mcpConfigJson)
                }
                val activeCount = mcpConfig.servers.count { it.enabled }
                val totalCount = mcpConfig.servers.size
                val subtitle = when {
                    totalCount == 0 -> "No servers configured"
                    activeCount == 0 -> "$totalCount server${if (totalCount > 1) "s" else ""}, none active"
                    else -> "$activeCount of $totalCount active"
                }
                SettingsCard(
                    icon = Icons.Default.Extension,
                    title = "MCP Servers",
                    subtitle = subtitle,
                    onClick = onNavigateToMcp
                )
            }

            // About
            SettingsSection("About") {
                SettingsCard(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0-alpha",
                    onClick = {}
                )
                SettingsCard(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = "Help & Feedback",
                    subtitle = "Report issues or suggest features",
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
