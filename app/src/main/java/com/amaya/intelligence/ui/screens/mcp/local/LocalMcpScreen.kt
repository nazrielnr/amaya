package com.amaya.intelligence.ui.screens.mcp.local

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.mcp.shared.McpEditSheet
import com.amaya.intelligence.ui.screens.mcp.shared.McpServerList
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMcpScreen(
    onNavigateBack: () -> Unit,
    aiSettingsManager: AiSettingsManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )
    val gradients = LocalAmayaGradients.current

    val mcpConfig by remember(settings.mcpConfigJson) {
        derivedStateOf { McpConfig.fromJson(settings.mcpConfigJson) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServerConfig?>(null) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }
                if (!json.isNullOrBlank()) {
                    val imported = McpConfig.fromJson(json)
                    if (imported.servers.isEmpty()) {
                        snackbarHostState.showSnackbar("No valid MCP servers found in file")
                        return@launch
                    }
                    val existing = mcpConfig.servers.toMutableList()
                    var added = 0; var updated = 0
                    imported.servers.forEach { importedServer ->
                        val idx = existing.indexOfFirst { it.name == importedServer.name }
                        if (idx >= 0) { existing[idx] = importedServer; updated++ }
                        else { existing.add(importedServer); added++ }
                    }
                    aiSettingsManager.setMcpConfigJson(McpConfig(existing).toJson())
                    val msg = buildString {
                        if (added > 0) append("$added added")
                        if (added > 0 && updated > 0) append(", ")
                        if (updated > 0) append("$updated updated")
                        append(" ✓")
                    }
                    snackbarHostState.showSnackbar("Imported: $msg")
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            McpServerList(
                servers = mcpConfig.servers,
                iconPalettes = gradients.iconPalettes,
                onServerClick = { server ->
                    editingServer = server
                    showAddSheet = true
                },
                onToggleEnabled = { server, enabled ->
                    scope.launch {
                        val updated = mcpConfig.servers.map {
                            if (it.name == server.name) it.copy(enabled = enabled) else it
                        }
                        aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                    }
                },
                onDelete = { server ->
                    scope.launch {
                        val updated = mcpConfig.servers.filter { it.name != server.name }
                        aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                        snackbarHostState.showSnackbar("${server.name} removed")
                    }
                },
                topPadding = topPadding
            )

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
                        "MCP Servers", 
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
                            .clickable { showAddSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            "Add Server",
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

    if (showAddSheet) {
        McpEditSheet(
            existing = editingServer,
            existingNames = mcpConfig.servers.map { it.name }.filter { it != editingServer?.name },
            onDismiss = { showAddSheet = false; editingServer = null },
            onSave = { server ->
                showAddSheet = false
                scope.launch {
                    val updated = if (editingServer != null) {
                        mcpConfig.servers.map { if (it.name == editingServer!!.name) server else it }
                    } else {
                        mcpConfig.servers + server
                    }
                    aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                    snackbarHostState.showSnackbar("${server.name} saved ✓")
                }
                editingServer = null
            }
        )
    }
}
