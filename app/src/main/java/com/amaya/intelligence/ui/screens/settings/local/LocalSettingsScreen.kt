package com.amaya.intelligence.ui.screens.settings.local

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.res.UiStrings
import com.amaya.intelligence.ui.screens.settings.shared.SettingsItemCard
import com.amaya.intelligence.ui.screens.settings.shared.SettingsSectionCard
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSettingsScreen(
    onNavigateBack: () -> Unit,
    currentWorkspace: String?,
    onNavigateToWorkspace: () -> Unit,
    aiSettingsManager: AiSettingsManager,
    onNavigateToPersona: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToMcp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )
    val isDark = isSystemInDarkTheme()
    val gradients = LocalAmayaGradients.current

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.statusBarsPadding().height(52.dp))
                
                SettingsSectionCard("Workspace") {
                    SettingsItemCard(
                        icon = Icons.Default.Folder,
                        iconBrush = gradients.iconPalettes[0],
                        title = UiStrings.Settings.CURRENT_WORKSPACE,
                        subtitle = currentWorkspace ?: UiStrings.Settings.NOT_SELECTED,
                        isFirst = true, isLast = true,
                        onClick = onNavigateToWorkspace
                    )
                }

                SettingsSectionCard("Agent Configuration") {
                    SettingsItemCard(
                        icon = Icons.Default.SmartToy,
                        iconBrush = gradients.iconPalettes[1],
                        title = UiStrings.Settings.MANAGE_AGENTS,
                        subtitle = UiStrings.Settings.MANAGE_AGENTS_SUBTITLE,
                        isFirst = true, isLast = true,
                        onClick = onNavigateToAgents
                    )
                }

                SettingsSectionCard("Persona") {
                    SettingsItemCard(
                        icon = Icons.Default.Person,
                        iconBrush = gradients.iconPalettes[2],
                        title = UiStrings.Settings.PERSONALITY_MEMORY,
                        subtitle = UiStrings.Settings.PERSONALITY_MEMORY_SUBTITLE,
                        isFirst = true, isLast = true,
                        onClick = onNavigateToPersona
                    )
                }

                SettingsSectionCard("Automation") {
                    SettingsItemCard(
                        icon = Icons.Default.Alarm,
                        iconBrush = gradients.iconPalettes[3],
                        title = UiStrings.Settings.REMINDERS_JOBS,
                        subtitle = UiStrings.Settings.REMINDERS_JOBS_SUBTITLE,
                        isFirst = true, isLast = true,
                        onClick = onNavigateToReminders
                    )
                }

                SettingsSectionCard(UiStrings.Settings.MCP_SERVERS) {
                    val mcpConfig = remember(settings.mcpConfigJson) {
                        com.amaya.intelligence.data.remote.api.McpConfig.fromJson(settings.mcpConfigJson)
                    }
                    val activeCount = mcpConfig.servers.count { it.enabled }
                    val totalCount = mcpConfig.servers.size
                    val subtitle = when {
                        totalCount == 0 -> UiStrings.Settings.NO_SERVERS_CONFIGURED
                        activeCount == 0 -> "$totalCount server${if (totalCount > 1) "s" else ""}, none active"
                        else -> "$activeCount of $totalCount active"
                    }
                    SettingsItemCard(
                        icon = Icons.Default.Extension,
                        iconBrush = gradients.iconPalettes[4],
                        title = UiStrings.Settings.MCP_SERVERS,
                        subtitle = subtitle,
                        isFirst = true, isLast = true,
                        onClick = onNavigateToMcp
                    )
                }

                SettingsSectionCard("Appearance") {
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
                                .background(gradients.iconPalettes[5]),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                UiStrings.Settings.THEME,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val themes = listOf("system", "light", "dark")
                                val labels = listOf(UiStrings.Settings.SYSTEM, UiStrings.Settings.LIGHT, UiStrings.Settings.DARK)
                                themes.forEachIndexed { index, theme ->
                                    SegmentedButton(
                                        selected = settings.theme == theme,
                                        onClick = { scope.launch { aiSettingsManager.setTheme(theme) } },
                                        shape = SegmentedButtonDefaults.itemShape(index, themes.size)
                                    ) { Text(labels[index], style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                }

                SettingsSectionCard("About") {
                    SettingsItemCard(
                        icon = Icons.Default.Info,
                        iconBrush = gradients.iconPalettes[6],
                        title = UiStrings.Settings.VERSION,
                        subtitle = UiStrings.Settings.VERSION_NUMBER,
                        isFirst = true, isLast = false,
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("Amaya Intelligence v${UiStrings.Settings.VERSION_NUMBER}") }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current
                    SettingsItemCard(
                        icon = Icons.Default.Info,
                        iconBrush = gradients.iconPalettes[7],
                        title = UiStrings.Settings.HELP_FEEDBACK,
                        subtitle = UiStrings.Settings.HELP_FEEDBACK_SUBTITLE,
                        isFirst = false, isLast = false,
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/nazrielnr/amaya/pulls")
                            )
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    val updateViewModel: com.amaya.intelligence.ui.screens.settings.shared.UpdateViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val updateState by updateViewModel.uiState.collectAsState()
                    
                    SettingsItemCard(
                        icon = Icons.Default.SystemUpdate,
                        iconBrush = gradients.iconPalettes[0], // Reuse first palette for update
                        title = UiStrings.Settings.CHECK_FOR_UPDATE,
                        subtitle = when (updateState) {
                            is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.Checking -> UiStrings.Settings.CHECKING_UPDATE
                            is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.UpToDate -> UiStrings.Settings.UP_TO_DATE
                            is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.UpdateAvailable -> "New version available"
                            else -> "Tap to check for new releases"
                        },
                        isFirst = false, isLast = true,
                        onClick = { updateViewModel.checkForUpdate() }
                    )

                    // Show update info sheet if available
                    if (updateState is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.UpdateAvailable) {
                        val info = (updateState as com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.UpdateAvailable).info
                        com.amaya.intelligence.ui.components.shared.UpdateInfoSheet(
                            info = info,
                            onDismiss = { updateViewModel.dismiss() }
                        )
                    }

                    // Show up-to-date snackbar
                    LaunchedEffect(updateState) {
                        if (updateState is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.UpToDate) {
                            snackbarHostState.showSnackbar(UiStrings.Settings.UP_TO_DATE)
                            updateViewModel.dismiss()
                        } else if (updateState is com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.Error) {
                            snackbarHostState.showSnackbar((updateState as com.amaya.intelligence.ui.screens.settings.shared.UpdateUiState.Error).message)
                            updateViewModel.dismiss()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.TopCenter)
                    .background(gradients.topScrim)
            )

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
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp),
                windowInsets = WindowInsets(0.dp)
            )
        }
    }
}
