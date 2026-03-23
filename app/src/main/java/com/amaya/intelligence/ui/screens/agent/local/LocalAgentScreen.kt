package com.amaya.intelligence.ui.screens.agent.local

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.agent.shared.AgentEditSheet
import com.amaya.intelligence.ui.screens.agent.shared.AgentList
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAgentScreen(
    onNavigateBack: () -> Unit,
    aiSettingsManager: AiSettingsManager
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )
    val gradients = LocalAmayaGradients.current

    var editingConfig by remember { mutableStateOf<AgentConfig?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AgentList(
                agentConfigs = settings.agentConfigs,
                iconPalettes = gradients.iconPalettes,
                onAgentClick = { config ->
                    editingConfig = config
                    editingIsNew = false
                },
                onToggleEnabled = { config, enabled ->
                    scope.launch {
                        aiSettingsManager.saveAgentConfig(
                            config.copy(enabled = enabled),
                            aiSettingsManager.getAgentApiKey(config.id)
                        )
                    }
                },
                topPadding = topPadding
            )

            // Scrims
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.TopCenter)
                    .background(gradients.topScrim)
            )

            // Header Overlay
            TopAppBar(
                title = { 
                    Text(
                        "AI Agents", 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    ) 
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onNavigateBack)
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            .clickable {
                                editingConfig = AgentConfig()
                                editingIsNew = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            "Add Agent",
                            modifier = Modifier.size(20.dp)
                        )
                    }
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

    // BottomSheet drawer for add/edit
    editingConfig?.let { currentConfig ->
        val sheetScope = rememberCoroutineScope()
        val maxSheetHeight = (0.75f * LocalConfiguration.current.screenHeightDp).dp
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
        BackHandler {
            sheetScope.launch {
                sheetState.hide()
                editingConfig = null
            }
        }
        val currentApiKey = remember(currentConfig.id) {
            if (editingIsNew) "" else aiSettingsManager.getAgentApiKey(currentConfig.id)
        }

        ModalBottomSheet(
            onDismissRequest = { editingConfig = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AgentEditSheet(
                config = currentConfig,
                apiKey = currentApiKey,
                isNew = editingIsNew,
                maxSheetHeight = maxSheetHeight,
                onDismiss = {
                    sheetScope.launch {
                        sheetState.hide()
                        editingConfig = null
                    }
                },
                onSave = { updatedConfig, key ->
                    editingConfig = null
                    scope.launch {
                        aiSettingsManager.saveAgentConfig(updatedConfig, key)
                        if (updatedConfig.id == settings.activeAgentId) {
                            aiSettingsManager.setActiveAgent(updatedConfig.id, updatedConfig.modelId)
                        }
                        snackbarHostState.showSnackbar("Agent saved ✓")
                    }
                },
                onDelete = if (editingIsNew) null else {
                    {
                        editingConfig = null
                        scope.launch {
                            aiSettingsManager.deleteAgentConfig(currentConfig.id)
                            snackbarHostState.showSnackbar("Agent deleted")
                        }
                    }
                }
            )
        }
    }
}
