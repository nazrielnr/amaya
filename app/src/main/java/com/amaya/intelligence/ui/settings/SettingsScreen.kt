package com.amaya.intelligence.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import kotlinx.coroutines.launch

// ─── Colorful leading icon palette ───────────────────────────────────────────
// (containerBg, iconTint) — pleasant, not too saturated, readable in both modes
private val IconPaletteLight = listOf(
    Color(0xFFD8EDFF) to Color(0xFF1565C0), // Blue
    Color(0xFFDCF5E4) to Color(0xFF1B5E20), // Green
    Color(0xFFFCE4EC) to Color(0xFFC62828), // Pink
    Color(0xFFFFF3E0) to Color(0xFFE65100), // Orange
    Color(0xFFF3E5F5) to Color(0xFF6A1B9A), // Purple
    Color(0xFFE3F2FD) to Color(0xFF0D47A1), // Light Blue
    Color(0xFFE8F5E9) to Color(0xFF2E7D32), // Teal
    Color(0xFFFFFDE7) to Color(0xFFF57F17), // Yellow
)
private val IconPaletteDark = listOf(
    Color(0xFF1A3A5C) to Color(0xFF90CAF9), // Blue
    Color(0xFF1A3A28) to Color(0xFFA5D6A7), // Green
    Color(0xFF4A1A28) to Color(0xFFF48FB1), // Pink
    Color(0xFF4A2800) to Color(0xFFFFCC80), // Orange
    Color(0xFF2E1A4A) to Color(0xFFCE93D8), // Purple
    Color(0xFF0D2545) to Color(0xFF90CAF9), // Light Blue
    Color(0xFF1A3A20) to Color(0xFF80CBC4), // Teal
    Color(0xFF4A3A00) to Color(0xFFFFE082), // Yellow
)

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
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) IconPaletteDark else IconPaletteLight

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onNavigateBack)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Workspace
            SettingsSection("Workspace") {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    iconBg = palette[0].first,
                    iconTint = palette[0].second,
                    title = "Current Workspace",
                    subtitle = currentWorkspace ?: "Not selected",
                    isFirst = true, isLast = true,
                    onClick = onNavigateToWorkspace
                )
            }

            // Agent Configuration
            SettingsSection("Agent Configuration") {
                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    iconBg = palette[1].first,
                    iconTint = palette[1].second,
                    title = "Manage Agents",
                    subtitle = "Add or edit API keys, base URLs, and models",
                    isFirst = true, isLast = true,
                    onClick = onNavigateToAgents
                )
            }

            // Persona
            SettingsSection("Persona") {
                SettingsItem(
                    icon = Icons.Default.Person,
                    iconBg = palette[2].first,
                    iconTint = palette[2].second,
                    title = "Personality & Memory",
                    subtitle = "Style, instructions, and AI memory",
                    isFirst = true, isLast = true,
                    onClick = onNavigateToPersona
                )
            }

            // Automation
            SettingsSection("Automation") {
                SettingsItem(
                    icon = Icons.Default.Alarm,
                    iconBg = palette[3].first,
                    iconTint = palette[3].second,
                    title = "Reminders & Cron Jobs",
                    subtitle = "Schedule AI-powered reminders and notifications",
                    isFirst = true, isLast = true,
                    onClick = onNavigateToReminders
                )
            }

            // Appearance
            SettingsSection("Appearance") {
                // Theme row inside the section card (no separate Surface needed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette[4].first),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = palette[4].second,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Theme",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val themes = listOf("system", "light", "dark")
                            val labels = listOf("System", "Light", "Dark")
                            themes.forEachIndexed { index, theme ->
                                SegmentedButton(
                                    selected = settings.theme == theme,
                                    onClick = { scope.launch { aiSettingsManager.setTheme(theme) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, themes.size)
                                ) { Text(labels[index], fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }

            // MCP Servers
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
                SettingsItem(
                    icon = Icons.Default.Extension,
                    iconBg = palette[5].first,
                    iconTint = palette[5].second,
                    title = "MCP Servers",
                    subtitle = subtitle,
                    isFirst = true, isLast = true,
                    onClick = onNavigateToMcp
                )
            }

            // About
            SettingsSection("About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    iconBg = palette[6].first,
                    iconTint = palette[6].second,
                    title = "Version",
                    subtitle = "1.0.0-alpha",
                    isFirst = true, isLast = false,
                    onClick = {}
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    iconBg = palette[7].first,
                    iconTint = palette[7].second,
                    title = "Help & Feedback",
                    subtitle = "Report issues or suggest features",
                    isFirst = false, isLast = true,
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── Global back button: grey circle ─────────────────────────────────────────

@Composable
fun SettingsBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Section container ────────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

// ─── Individual list item ─────────────────────────────────────────────────────

@Composable
fun SettingsItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val itemShape = when {
        isFirst && isLast -> RoundedCornerShape(14.dp)
        isFirst           -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        isLast            -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else              -> RoundedCornerShape(0.dp)
    }

    Surface(
        onClick = onClick,
        shape = itemShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colorful icon container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
