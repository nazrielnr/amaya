package com.amaya.intelligence.ui.screens.mcp.local

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.mcp.shared.McpEditSheet
import com.amaya.intelligence.ui.screens.mcp.shared.McpServerList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.amaya.intelligence.data.remote.mcp.McpClientManager
import com.amaya.intelligence.data.remote.mcp.McpServerTestResult
import com.amaya.intelligence.ui.screens.amaya.iosAmayaColors
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMcpScreen(
    onNavigateBack: () -> Unit,
    aiSettingsManager: AiSettingsManager,
    mcpClientManager: McpClientManager? = null
) {
    val colors = iosAmayaColors()
    val scope = rememberCoroutineScope()
    val maxSheetHeight = (0.98f * LocalConfiguration.current.screenHeightDp).dp
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )

    val mcpConfig by remember(settings.mcpConfigJson) {
        derivedStateOf { McpConfig.fromJson(settings.mcpConfigJson) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var editorJson by remember { mutableStateOf(settings.mcpConfigJson) }
    var testResults by remember { mutableStateOf(mapOf<String, McpServerTestResult>()) }
    var testingServers by remember { mutableStateOf(setOf<String>()) }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

    fun testServer(server: com.amaya.intelligence.data.remote.api.McpServerConfig) {
        if (mcpClientManager == null) return
        testingServers = testingServers + server.name
        scope.launch {
            val result = mcpClientManager.testServer(server)
            testResults = testResults + (server.name to result)
            testingServers = testingServers - server.name
            if (result.isSuccess) {
                snackbarHostState.showSnackbar("✓ ${server.name}: ${result.message} (${result.latencyMs}ms)")
            } else {
                snackbarHostState.showSnackbar("✕ ${server.name}: ${result.message}")
            }
        }
    }

    fun openEditor() {
        scope.launch {
            val latestJson = aiSettingsManager.loadMcpConfigFromFixedPath()
            editorJson = latestJson ?: settings.mcpConfigJson
            showAddSheet = true
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }
                if (!json.isNullOrBlank()) {
                    aiSettingsManager.setMcpConfigJson(json)
                    snackbarHostState.showSnackbar("mcp.json imported")
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(colors.groupedBackground)) {
            McpServerList(
                servers = mcpConfig.servers,
                onServerClick = { openEditor() },
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
                onTest = { server -> testServer(server) },
                testResults = testResults,
                testingServers = testingServers,
                topPadding = topPadding
            )

            com.amaya.intelligence.ui.screens.amaya.AmayaTopScrim(
                Modifier.align(Alignment.TopCenter)
            )

            TopAppBar(
                title = { 
                    Text(
                        "MCP Servers", 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp),
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onNavigateBack)
                },
                actions = {
                    com.amaya.intelligence.ui.components.shared.AmayaTopBarButton(
                        icon = Icons.Default.Add,
                        onClick = { openEditor() },
                        contentDescription = "Edit MCP Config",
                        modifier = Modifier.padding(end = 12.dp)
                    )
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
            initialJson = editorJson,
            onDismiss = { showAddSheet = false },
            onSave = { json ->
                showAddSheet = false
                scope.launch {
                    aiSettingsManager.setMcpConfigJson(json)
                    snackbarHostState.showSnackbar("mcp.json saved ✓")
                }
            },
            onTestConnection = mcpClientManager?.let { mgr -> { server -> mgr.testServer(server) } }
        )
    }
}
