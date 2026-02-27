package com.amaya.intelligence.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.data.remote.api.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    onNavigateBack: () -> Unit,
    aiSettingsManager: AiSettingsManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )

    val mcpConfig by remember(settings.mcpConfigJson) {
        derivedStateOf { McpConfig.fromJson(settings.mcpConfigJson) }
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServerConfig?>(null) }

    // File importer
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }
                if (!json.isNullOrBlank()) {
                    aiSettingsManager.setMcpConfigJson(json)
                    snackbarHostState.showSnackbar("MCP config imported ✓")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("MCP Servers", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch("application/json") }) {
                        Icon(Icons.Default.FileOpen, "Import JSON")
                    }
                    IconButton(onClick = {
                        editingServer = null
                        showAddSheet = true
                    }) {
                        Icon(Icons.Default.Add, "Add MCP Server")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (mcpConfig.servers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Spacer(Modifier.height(12.dp))
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
                            Spacer(Modifier.height(24.dp))
                            // Format guideline
                            McpFormatGuide()
                        }
                    }
                }
            } else {
                // Active servers
                val activeServers = mcpConfig.servers.filter { it.enabled }
                val disabledServers = mcpConfig.servers.filter { !it.enabled }

                if (activeServers.isNotEmpty()) {
                    item {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(activeServers, key = { "active_${it.name}" }) { server ->
                        McpServerCard(
                            server = server,
                            onToggle = { enabled ->
                                scope.launch {
                                    val updated = mcpConfig.servers.map {
                                        if (it.name == server.name) it.copy(enabled = enabled) else it
                                    }
                                    aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                                }
                            },
                            onEdit = {
                                editingServer = server
                                showAddSheet = true
                            },
                            onDelete = {
                                scope.launch {
                                    val updated = mcpConfig.servers.filter { it.name != server.name }
                                    aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                                    snackbarHostState.showSnackbar("${server.name} removed")
                                }
                            }
                        )
                    }
                }

                if (disabledServers.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Disabled",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(disabledServers, key = { "disabled_${it.name}" }) { server ->
                        McpServerCard(
                            server = server,
                            onToggle = { enabled ->
                                scope.launch {
                                    val updated = mcpConfig.servers.map {
                                        if (it.name == server.name) it.copy(enabled = enabled) else it
                                    }
                                    aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                                }
                            },
                            onEdit = {
                                editingServer = server
                                showAddSheet = true
                            },
                            onDelete = {
                                scope.launch {
                                    val updated = mcpConfig.servers.filter { it.name != server.name }
                                    aiSettingsManager.setMcpConfigJson(McpConfig(updated).toJson())
                                    snackbarHostState.showSnackbar("${server.name} removed")
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    McpFormatGuide()
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAddSheet) {
        AddEditMcpSheet(
            existing = editingServer,
            existingNames = mcpConfig.servers.map { it.name }.filter { it != editingServer?.name },
            onDismiss = { showAddSheet = false },
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
            }
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServerConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (server.enabled)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (server.enabled) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = if (server.enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (server.enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    server.serverUrl.ifBlank { "No URL" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (server.headers.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "${server.headers.size} header${if (server.headers.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Switch(
                checked = server.enabled,
                onCheckedChange = onToggle
            )
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditMcpSheet(
    existing: McpServerConfig?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )
    // Predictive back: animate sheet down, then dismiss
    BackHandler {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var serverUrl by remember { mutableStateOf(existing?.serverUrl ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    // Headers: list of key-value pairs
    var headers by remember {
        mutableStateOf<List<Pair<String, String>>>(
            existing?.headers?.entries?.map { it.key to it.value } ?: emptyList()
        )
    }

    val nameError = when {
        name.isBlank() -> "Name is required"
        existingNames.contains(name.trim()) -> "Name already used"
        else -> null
    }
    val urlError = if (serverUrl.isBlank()) "Server URL is required" else null
    val isValid = nameError == null && urlError == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(0.dp)
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (existing != null) "Edit MCP Server" else "New MCP Server",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim() },
                label = { Text("Name") },
                placeholder = { Text("e.g. context7") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
            )

            // Server URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it.trim() },
                label = { Text("Server URL") },
                placeholder = { Text("https://mcp.example.com/mcp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)) }
            )

            // Headers
            HorizontalDivider()
            Text("Headers (optional)", style = MaterialTheme.typography.labelLarge)
            Text(
                "Add API keys or auth tokens as request headers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            headers.indices.forEach { idx ->
                val (key, value) = headers[idx]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { newKey ->
                            headers = headers.mapIndexed { i, p -> if (i == idx) newKey to p.second else p }
                        },
                        label = { Text("Key") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newVal ->
                            headers = headers.mapIndexed { i, p -> if (i == idx) p.first to newVal else p }
                        },
                        label = { Text("Value") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(onClick = {
                        headers = headers.filterIndexed { i, _ -> i != idx }
                    }) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }

            OutlinedButton(
                onClick = { headers = headers + ("" to "") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Header")
            }

            HorizontalDivider()

            // Enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (enabled) "Server will be used by the AI agent" else "Server will be skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Button(
                onClick = {
                    val validHeaders = headers
                        .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
                        .associate { pair -> pair.first to pair.second }
                    onSave(
                        McpServerConfig(
                            name = name.trim(),
                            serverUrl = serverUrl.trim(),
                            headers = validHeaders,
                            enabled = enabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (existing != null) "Update Server" else "Add Server")
            }
        }
        } // Surface
    }
}

@Composable
private fun McpFormatGuide() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "MCP Server Format for Amaya",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Amaya only supports HTTP-based MCP servers (no npx/stdio). Your server must accept JSON-RPC 2.0 POST requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Required endpoints:", style = MaterialTheme.typography.labelMedium)
            Text(
                "• POST /mcp  →  method: \"tools/list\"\n• POST /mcp  →  method: \"tools/call\"",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Required headers:", style = MaterialTheme.typography.labelMedium)
            Text(
                "• Content-Type: application/json\n• Accept: application/json, text/event-stream",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Tool naming in AI:", style = MaterialTheme.typography.labelMedium)
            Text(
                "mcp__{name}__{toolName}\n\nExample: mcp__context7__resolve-library-id",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Public servers (no key needed):", style = MaterialTheme.typography.labelMedium)
            Text(
                "• Context7: https://mcp.context7.com/mcp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
