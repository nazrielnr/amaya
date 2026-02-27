package com.amaya.intelligence.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onNavigateBack: () -> Unit,
    aiSettingsManager: AiSettingsManager
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )

    var editingConfig by remember { mutableStateOf<AgentConfig?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Agents", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingConfig = AgentConfig()
                        editingIsNew = true
                    }) {
                        Icon(Icons.Default.Add, "Add Agent")
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
            if (settings.agentConfigs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Spacer(Modifier.height(12.dp))
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

            // Enabled agents section
            val enabledAgents  = settings.agentConfigs.filter { it.enabled }
            val disabledAgents = settings.agentConfigs.filter { !it.enabled }

            if (enabledAgents.isNotEmpty()) {
                item {
                    Text(
                        "Enabled",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                items(enabledAgents, key = { "enabled_${it.id}" }) { config ->
                    AgentCard(
                        config = config,
                        onClick = { editingConfig = config; editingIsNew = false },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                aiSettingsManager.saveAgentConfig(
                                    config.copy(enabled = enabled),
                                    aiSettingsManager.getAgentApiKey(config.id)
                                )
                            }
                        }
                    )
                }
            }

            if (disabledAgents.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Disabled",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                items(disabledAgents, key = { "disabled_${it.id}" }) { config ->
                    AgentCard(
                        config = config,
                        onClick = { editingConfig = config; editingIsNew = false },
                        onToggleEnabled = { enabled ->
                            scope.launch {
                                aiSettingsManager.saveAgentConfig(
                                    config.copy(enabled = enabled),
                                    aiSettingsManager.getAgentApiKey(config.id)
                                )
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // BottomSheet drawer for add/edit — full-height, square top, fills statusbar
    editingConfig?.let { currentConfig ->
        val sheetScope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { false }
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
            containerColor = Color.Transparent,
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
                AgentEditSheet(
                    config = currentConfig,
                    apiKey = currentApiKey,
                    isNew = editingIsNew,
                    onDismiss = { editingConfig = null },
                    onSave = { updatedConfig, key ->
                        editingConfig = null
                        scope.launch {
                            aiSettingsManager.saveAgentConfig(updatedConfig, key)
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
}

// ─── Agent Card (list item) ───────────────────────────────────────────────────

@Composable
private fun AgentCard(
    config: AgentConfig,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (config.enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (config.enabled) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar badge
            Surface(
                shape = CircleShape,
                color = if (config.enabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (config.enabled) MaterialTheme.colorScheme.onSecondaryContainer
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.name.ifBlank { "Unnamed Agent" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (config.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (config.modelId.isNotBlank()) append(config.modelId)
                        else append("No model set")
                        append(" · ")
                        append(config.providerType.lowercase().replaceFirstChar { it.uppercaseChar() })
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Enable/Disable toggle — only if model is set
            if (config.modelId.isNotBlank()) {
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Edit Sheet (inside BottomSheet Surface) ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditSheet(
    config: AgentConfig,
    apiKey: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AgentConfig, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name         by remember(config.id) { mutableStateOf(config.name) }
    var providerType by remember(config.id) { mutableStateOf(config.providerType) }
    var baseUrl      by remember(config.id) { mutableStateOf(config.baseUrl) }
    var modelId      by remember(config.id) { mutableStateOf(config.modelId) }
    var key          by remember(config.id) { mutableStateOf(apiKey) }
    var enabled      by remember(config.id) { mutableStateOf(config.enabled) }
    var showKey      by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isNew) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isNew) "New Agent" else "Edit Agent",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            placeholder = { Text("e.g. My GPT-4o") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
        )

        // Provider type dropdown
        var providerExpanded by remember { mutableStateOf(false) }
        val providerOptions = listOf("OPENAI", "ANTHROPIC", "GEMINI", "CUSTOM")
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = it }
        ) {
            OutlinedTextField(
                value = providerType.lowercase().replaceFirstChar { it.uppercaseChar() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                providerOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.lowercase().replaceFirstChar { it.uppercaseChar() }) },
                        onClick = { providerType = option; providerExpanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // API Key
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("API Key") },
            placeholder = { Text("sk-... (leave blank if not needed)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        // Base URL (only for CUSTOM)
        if (providerType == "CUSTOM") {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://openrouter.ai/api/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Model ID
        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            label = { Text("Model ID") },
            placeholder = { Text("e.g. gpt-4o, claude-sonnet-4-20250514") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp)) }
        )

        // Enabled toggle
        if (modelId.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (enabled) "Agent available to AI" else "Agent will be skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Save + Delete row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onDelete != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val finalBaseUrl = when (providerType) {
                        "OPENAI"    -> "https://api.openai.com/v1"
                        "ANTHROPIC" -> "https://api.anthropic.com/v1"
                        "GEMINI"    -> "https://generativelanguage.googleapis.com/v1beta"
                        else        -> baseUrl.trim()
                    }
                    onSave(
                        config.copy(
                            name         = name.trim(),
                            providerType = providerType,
                            baseUrl      = finalBaseUrl,
                            modelId      = modelId.trim(),
                            enabled      = enabled
                        ),
                        key.trim()
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Agent?") },
            text = { Text("\"${config.name.ifBlank { "This agent" }}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete?.invoke() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
