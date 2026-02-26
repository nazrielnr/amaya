package com.opencode.mobile.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.mobile.data.remote.api.AgentConfig
import com.opencode.mobile.data.remote.api.AiSettingsManager
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
        initial = com.opencode.mobile.data.remote.api.AiSettings()
    )

    // Which config is being edited in the bottom sheet (null = sheet is closed)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Existing Agents ──────────────────────────────────────
            if (settings.agentConfigs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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

            items(settings.agentConfigs, key = { it.id }) { config ->
                val isActive  = config.id == settings.activeAgentId

                AgentSummaryCard(
                    config = config,
                    isActive = isActive,
                    onClick = {
                        editingConfig = config
                        editingIsNew = false
                    },
                    onSetActive = {
                        scope.launch {
                            aiSettingsManager.setActiveAgent(config.id, config.modelId)
                            aiSettingsManager.setOpenAiSettings(
                                aiSettingsManager.getAgentApiKey(config.id),
                                config.baseUrl
                            )
                            snackbarHostState.showSnackbar("Active agent: ${config.name}")
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }

        editingConfig?.let { currentConfig ->
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { false } // Prevent closing by swipe
            )
            val currentApiKey = remember(currentConfig.id) {
                if (editingIsNew) "" else aiSettingsManager.getAgentApiKey(currentConfig.id)
            }
            val isActive = currentConfig.id == settings.activeAgentId

            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch {
                        sheetState.hide()
                        editingConfig = null
                    }
                },
                sheetState = sheetState,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                dragHandle = null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .imePadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp, top = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AgentEditCard(
                        config = currentConfig,
                        apiKey = currentApiKey,
                        isNew = editingIsNew,
                        isActive = isActive,
                        onSave = { updatedConfig, key ->
                            editingConfig = null // Instantly close modal
                            scope.launch {
                                aiSettingsManager.saveAgentConfig(updatedConfig, key)
                                snackbarHostState.showSnackbar("Agent saved ✓")
                            }
                        },
                        onDiscard = {
                            editingConfig = null // Instantly close modal
                        },
                        onDelete = if (editingIsNew) null else {
                            {
                                editingConfig = null // Instantly close modal
                                scope.launch {
                                    aiSettingsManager.deleteAgentConfig(currentConfig.id)
                                    snackbarHostState.showSnackbar("Agent deleted")
                                }
                            }
                        },
                        onSetActive = if (editingIsNew || isActive) null else {
                            {
                                editingConfig = null // Instantly close modal
                                scope.launch {
                                    aiSettingsManager.setActiveAgent(currentConfig.id, currentConfig.modelId)
                                    aiSettingsManager.setOpenAiSettings(
                                        aiSettingsManager.getAgentApiKey(currentConfig.id),
                                        currentConfig.baseUrl
                                    )
                                    snackbarHostState.showSnackbar("Active agent: ${currentConfig.name}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Summary card (collapsed view)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentSummaryCard(
    config: AgentConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    onSetActive: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Surface(
                shape = CircleShape,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isActive) Icons.Default.CheckCircle else Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isActive)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        config.name.ifBlank { "Unnamed Agent" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    config.modelId.ifBlank { "No model set" } + " • " + config.providerType,
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface).copy(alpha = 0.65f)
                )
                Text(
                    config.baseUrl.ifBlank { "No base URL" },
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface).copy(alpha = 0.45f)
                )
            }

            // Set active button
            if (!isActive) {
                IconButton(onClick = onSetActive) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Use this agent",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Edit card (expanded form)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditCard(
    config: AgentConfig,
    apiKey: String,
    isNew: Boolean,
    isActive: Boolean,
    onSave: (AgentConfig, String) -> Unit,
    onDiscard: () -> Unit,
    onDelete: (() -> Unit)?,
    onSetActive: (() -> Unit)?
) {
    var name    by remember(config.id) { mutableStateOf(config.name) }
    var providerType by remember(config.id) { mutableStateOf(config.providerType) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var modelId by remember(config.id) { mutableStateOf(config.modelId) }
    var key     by remember(config.id) { mutableStateOf(apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title row
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. OpenRouter Free") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
            )

            // Provider Type
            var providerExpanded by remember { mutableStateOf(false) }
            val options = listOf("OPENAI", "ANTHROPIC", "GEMINI", "CUSTOM")

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
                    options.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.lowercase().replaceFirstChar { it.uppercaseChar() }) },
                            onClick = {
                                providerType = selectionOption
                                providerExpanded = false
                            },
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
                placeholder = { Text("sk-...") },
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

            // Base URL (Hidden if not CUSTOM)
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
                placeholder = { Text("e.g. openai/gpt-4o-mini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp)) }
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Spacer(Modifier.weight(1f))

                // Discard
                OutlinedButton(onClick = onDiscard) {
                    Text("Cancel")
                }

                // Save
                Button(
                    onClick = {
                        val finalBaseUrl = when (providerType) {
                            "OPENAI" -> "https://api.openai.com/v1"
                            "ANTHROPIC" -> "https://api.anthropic.com/v1"
                            "GEMINI" -> "https://generativelanguage.googleapis.com/v1beta"
                            else -> baseUrl.trim()
                        }
                        onSave(
                            config.copy(
                                name         = name.trim(),
                                providerType = providerType,
                                baseUrl      = finalBaseUrl,
                                modelId      = modelId.trim()
                            ),
                            key.trim()
                        )
                    },
                    enabled = name.isNotBlank() && modelId.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }

            // Set as active (only for existing)
            if (!isNew && !isActive && onSetActive != null) {
                Button(
                    onClick = onSetActive,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use This Agent")
                }
            }
        }
    }

    // Delete confirmation dialog
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
