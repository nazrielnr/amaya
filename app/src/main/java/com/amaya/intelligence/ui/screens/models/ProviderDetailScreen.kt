package com.amaya.intelligence.ui.screens.models

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.data.remote.api.AmayaProviderRegistry
import com.amaya.intelligence.data.remote.api.ConfiguredModel
import com.amaya.intelligence.data.remote.api.ModelContextDetector
import com.amaya.intelligence.data.remote.api.ModelLatencyResult
import com.amaya.intelligence.ui.components.shared.AmayaTopBarButton
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import com.amaya.intelligence.ui.viewmodels.models.ManageModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    connectionId: String,
    onNavigateBack: () -> Unit,
    viewModel: ManageModelsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val operation by viewModel.operation.collectAsState()
    val modelLatencies by viewModel.modelLatencies.collectAsState()
    val testingModelId by viewModel.testingModelId.collectAsState()
    val connection = settings.connections.firstOrNull { it.id == connectionId }
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    val context = LocalContext.current

    // UI State
    var availableModels by remember(connectionId) { mutableStateOf<List<ConfiguredModel>>(emptyList()) }
    var selectedIds by remember(connectionId) { mutableStateOf<Set<String>>(emptySet()) }
    var hasCredential by remember(connectionId) { mutableStateOf(false) }
    var editingModel by remember(connectionId) { mutableStateOf<ConfiguredModel?>(null) }
    var editingProviderName by remember(connectionId) { mutableStateOf(false) }
    var editingApiKey by remember(connectionId) { mutableStateOf(false) }
    var editingBaseUrl by remember(connectionId) { mutableStateOf(false) }
    var addingCustomModel by remember(connectionId) { mutableStateOf(false) }
    var confirmDeleteProvider by remember(connectionId) { mutableStateOf(false) }
    var refreshTurns by remember(connectionId) { mutableIntStateOf(0) }
    var refreshingModels by remember(connectionId) { mutableStateOf(false) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshTurns * 360f,
        animationSpec = tween(durationMillis = 700),
        label = "provider_models_refresh"
    )

    // Init connection data
    LaunchedEffect(connection) {
        if (connection != null) {
            hasCredential = viewModel.hasCredential(connection.id)
            selectedIds = connection.visibleModels.filter { it.enabled }.map { it.id }.toSet()
            availableModels = connection.visibleModels.sortedBy { it.displayName.lowercase() }
        }
    }

    LaunchedEffect(connectionId, connection?.providerId) {
        val currentConnection = connection ?: return@LaunchedEffect
        if (!AmayaProviderRegistry.require(currentConnection.providerId).isSubscription && !refreshingModels) {
            refreshingModels = true
            refreshTurns += 1
            viewModel.refresh(
                connection = currentConnection,
                onSuccess = { refreshed ->
                    val merged = mergeConfiguredModels(currentConnection.visibleModels, refreshed)
                    availableModels = merged
                    viewModel.cacheModels(
                        connection = currentConnection,
                        models = merged,
                        onSuccess = { refreshingModels = false },
                        onFailure = { refreshingModels = false }
                    )
                },
                onFailure = { refreshingModels = false }
            )
        }
    }

    var query by remember { mutableStateOf("") }

    val filteredModels = remember(availableModels, query) {
        val value = query.trim().lowercase()
        if (value.isBlank()) availableModels else availableModels.filter {
            it.id.lowercase().contains(value) || it.displayName.lowercase().contains(value)
        }
    }

    if (connection == null) {
        if (settings.connections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryText)
            }
        } else {
            LaunchedEffect(Unit) { onNavigateBack() }
        }
        return
    }

    val isSubscription = AmayaProviderRegistry.find(connection.providerId)?.isSubscription == true
    val isCustomProvider = AmayaProviderRegistry.require(connection.providerId).isCustom

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize().background(colors.groupedBackground)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AmayaGroupedSettingsTokens.contentHorizontalPadding,
                    end = AmayaGroupedSettingsTokens.contentHorizontalPadding,
                    bottom = AmayaGroupedSettingsTokens.floatingActionButtonContentClearance
                ),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AmayaGroupedSettingsTokens.sectionSpacing)
            ) {
                item {
                    Spacer(
                        Modifier
                            .statusBarsPadding()
                            .height(AmayaGroupedSettingsTokens.screenContentTopSpacer)
                    )
                }

                item {
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection("Configuration") {
                        ModelSettingsRow(
                            icon = Icons.Default.Edit,
                            title = "Provider Name",
                            subtitle = connection.name,
                            colors = colors,
                            onClick = { editingProviderName = true }
                        )
                        if (!isSubscription) {
                            ModelDivider(colors)
                            if (isCustomProvider) {
                                ModelSettingsRow(
                                    Icons.Default.Link,
                                    "Base URL",
                                    connection.baseUrl,
                                    colors,
                                    onClick = { editingBaseUrl = true }
                                )
                                ModelDivider(colors)
                            }
                            ModelSettingsRow(
                                Icons.Default.Key,
                                "Change API Key",
                                if (hasCredential) "API key saved" else "Add API key",
                                colors,
                                onClick = { editingApiKey = true }
                            )
                        }
                    }
                }

                operation.error?.let { error ->
                    item { InlineError(error) }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.secondaryText) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear search", tint = colors.secondaryText)
                            }
                        },
                        placeholder = { Text("Search models…", color = colors.secondaryText) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.groupSurface,
                            unfocusedContainerColor = colors.groupSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = colors.primaryText,
                            unfocusedTextColor = colors.primaryText
                        )
                    )
                }

                item {
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection("Models") {
                        if (availableModels.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = AmayaGroupedSettingsTokens.rowHorizontalPadding,
                                        vertical = 6.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedIds.size} of ${availableModels.size} active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondaryText
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (selectedIds.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                viewModel.testActiveModelsLatency(connection)
                                            },
                                            enabled = testingModelId == null,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            if (testingModelId != null) {
                                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                Spacer(Modifier.width(4.dp))
                                            } else {
                                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text("Test Latency", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = {
                                                selectedIds = emptySet()
                                                viewModel.saveVisibleModels(connection.id, emptyList()) {}
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Disable All", fontSize = 12.sp)
                                        }
                                    }
                                    if (selectedIds.size < availableModels.size) {
                                        TextButton(
                                            onClick = {
                                                val allIds = availableModels.map { it.id }.toSet()
                                                selectedIds = allIds
                                                viewModel.saveVisibleModels(connection.id, availableModels) {}
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Enable All", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            ModelDivider(colors)
                        }
                        if (filteredModels.isEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AmayaGroupedSettingsTokens.emptyStateContentPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                if (operation.loading) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                } else {
                                    Text("No models found", color = colors.secondaryText)
                                }
                            }
                        } else {
                            filteredModels.forEachIndexed { index, model ->
                                ModernModelToggleRow(
                                    model = model,
                                    providerId = connection.providerId,
                                    checked = model.id in selectedIds,
                                    colors = colors,
                                    latencyResult = modelLatencies[model.id],
                                    isTestingLatency = testingModelId == model.id,
                                    onToggle = {
                                        val updatedIds = if (model.id in selectedIds) {
                                            selectedIds - model.id
                                        } else {
                                            selectedIds + model.id
                                        }
                                        selectedIds = updatedIds
                                        viewModel.saveVisibleModels(
                                            connection.id,
                                            availableModels.filter { it.id in updatedIds }
                                        ) {}
                                    },
                                    onConfigure = { editingModel = model },
                                    onTestLatency = {
                                        viewModel.testModelLatency(connection, model.id)
                                    }
                                )
                                if (index < filteredModels.lastIndex) ModelDivider(colors)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(AmayaGroupedSettingsTokens.inlineTextSpacing))
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection("Danger Zone") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    confirmDeleteProvider = true
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Delete Provider", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            com.amaya.intelligence.ui.screens.amaya.AmayaTopScrim(
                Modifier.align(Alignment.TopCenter)
            )
            TopAppBar(
                title = {
                    Text(
                        connection.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
                        modifier = Modifier.padding(start = AmayaGroupedSettingsTokens.topBarTitleStartPadding)
                    )
                },
                navigationIcon = { SettingsBackButton(onNavigateBack) },
                actions = {
                    if (!isSubscription) AmayaTopBarButton(
                        icon = Icons.Default.Refresh,
                        onClick = {
                            if (!refreshingModels) {
                                refreshingModels = true
                                refreshTurns += 1
                                Toast.makeText(context, "Refreshing models…", Toast.LENGTH_SHORT).show()
                                viewModel.refresh(
                                    connection = connection,
                                    onSuccess = { refreshed ->
                                        val merged = mergeConfiguredModels(connection.visibleModels, refreshed)
                                        availableModels = merged
                                        viewModel.cacheModels(
                                            connection = connection,
                                            models = merged,
                                            onSuccess = { refreshingModels = false },
                                            onFailure = { refreshingModels = false }
                                        )
                                    },
                                    onFailure = { refreshingModels = false }
                                )
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        iconModifier = Modifier.rotate(refreshRotation),
                        contentDescription = "Refresh models"
                    )
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = AmayaGroupedSettingsTokens.topBarHorizontalPadding),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp)
            )
            ExtendedFloatingActionButton(
                onClick = { addingCustomModel = true },
                icon = { Icon(Icons.Default.Add, "Add custom model") },
                text = { Text("Custom Model") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                    .amayaFloatingActionButtonBottomPadding()
            )
        }
    }

    editingModel?.let { model ->
        ModelConfigurationSheet(
            model = model,
            operation = operation,
            latencyResult = modelLatencies[model.id],
            isTestingLatency = testingModelId == model.id,
            onTestLatency = {
                viewModel.testModelLatency(connection, model.id)
            },
            onDismiss = { editingModel = null },
            onSave = { updated ->
                viewModel.saveModel(connection.id, updated) {
                    availableModels = availableModels.map { if (it.id == updated.id) updated else it }
                    editingModel = null
                }
            }
        )
    }

    if (editingProviderName) {
        ProviderNameSheet(
            currentName = connection.name,
            operation = operation,
            onDismiss = { editingProviderName = false },
            onSave = { name ->
                viewModel.renameConnection(connection, name) {
                    editingProviderName = false
                }
            }
        )
    }

    if (editingApiKey) {
        ApiKeySheet(
            providerName = connection.name,
            credentialRequired = AmayaProviderRegistry.require(connection.providerId).credentialRequired,
            operation = operation,
            onDismiss = { editingApiKey = false },
            onSave = { apiKey ->
                viewModel.replaceCredential(connection, apiKey) { refreshed ->
                    hasCredential = true
                    val merged = mergeConfiguredModels(connection.visibleModels, refreshed)
                    availableModels = merged
                    viewModel.cacheModels(connection, merged) { editingApiKey = false }
                }
            }
        )
    }

    if (editingBaseUrl) {
        BaseUrlSheet(
            currentBaseUrl = connection.baseUrl,
            operation = operation,
            onDismiss = { editingBaseUrl = false },
            onSave = { baseUrl ->
                viewModel.updateBaseUrl(connection, baseUrl) {
                    editingBaseUrl = false
                }
            }
        )
    }

    if (addingCustomModel) {
        AddCustomModelSheet(
            existingModelIds = availableModels.map { it.id }.toSet(),
            operation = operation,
            onDismiss = { addingCustomModel = false },
            onSave = { model ->
                viewModel.addModel(connection.id, model) {
                    availableModels = (availableModels + model).sortedBy { it.displayName.lowercase() }
                    selectedIds = selectedIds + model.id
                    addingCustomModel = false
                }
            }
        )
    }

    if (confirmDeleteProvider) {
        com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
            onDismissRequest = { confirmDeleteProvider = false },
            title = "Delete Provider"
        ) {
            Text(
                "This removes the provider, its configured models, and any saved credentials.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    viewModel.deleteConnection(connection.id) {
                        onNavigateBack()
                    }
                },
                enabled = !operation.loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Delete Provider")
            }
            OutlinedButton(
                onClick = { confirmDeleteProvider = false },
                enabled = !operation.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun AddCustomModelSheet(
    existingModelIds: Set<String>,
    operation: ManageModelsViewModel.OperationState,
    onDismiss: () -> Unit,
    onSave: (ConfiguredModel) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val normalizedId = modelId.trim()
    val duplicate = normalizedId in existingModelIds
    val detectedContext = remember(normalizedId) {
        if (normalizedId.isNotBlank()) ModelContextDetector.detectContextWindow(normalizedId) else null
    }
    val detectedMaxOutput = remember(normalizedId, detectedContext) {
        if (normalizedId.isNotBlank()) ModelContextDetector.detectMaxOutputTokens(normalizedId, contextWindow = detectedContext) else null
    }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Add Custom Model"
    ) {
        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model ID") },
            supportingText = { Text("Use the ID accepted by this provider.") },
            singleLine = true
        )
        if (detectedContext != null) {
            Text(
                "Detected context window: ${ModelContextDetector.formatTokenCount(detectedContext)} tokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name (optional)") },
            singleLine = true
        )
        if (duplicate) InlineError("A model with this ID already exists.")
        Button(
            onClick = {
                onSave(
                    ConfiguredModel(
                        id = normalizedId,
                        displayName = displayName.trim().ifBlank { normalizedId },
                        contextWindowTokens = detectedContext,
                        maxOutputTokens = detectedMaxOutput,
                        enabled = true
                    )
                )
            },
            enabled = normalizedId.isNotBlank() && !duplicate && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Add Model")
        }
    }
}

@Composable
private fun BaseUrlSheet(
    currentBaseUrl: String,
    operation: ManageModelsViewModel.OperationState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var baseUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Base URL"
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
            placeholder = { Text("https://example.com/v1") },
            supportingText = { Text("Use the API root, without /models or a query.") },
            singleLine = true
        )
        Button(
            onClick = { onSave(baseUrl.trim()) },
            enabled = baseUrl.isNotBlank() && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Save Base URL")
        }
    }
}

@Composable
private fun ProviderNameSheet(
    currentName: String,
    operation: ManageModelsViewModel.OperationState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Provider Name"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Provider name") },
            singleLine = true
        )
        Button(
            onClick = { onSave(name.trim()) },
            enabled = name.isNotBlank() && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Save Name")
        }
    }
}

@Composable
private fun ApiKeySheet(
    providerName: String,
    credentialRequired: Boolean,
    operation: ManageModelsViewModel.OperationState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Change API Key"
    ) {
        Text(
            "Update the API key for $providerName.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (credentialRequired) "New API key" else "New API key (optional)") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (showKey) "Hide API key" else "Show API key"
                    )
                }
            }
        )
        Button(
            onClick = { onSave(apiKey) },
            enabled = apiKey.isNotBlank() && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Update API Key")
        }
    }
}

@Composable
private fun ModelConfigurationSheet(
    model: ConfiguredModel,
    operation: ManageModelsViewModel.OperationState,
    latencyResult: ModelLatencyResult? = null,
    isTestingLatency: Boolean = false,
    onTestLatency: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (ConfiguredModel) -> Unit
) {
    var displayName by remember(model.id, model.displayName) {
        mutableStateOf(model.displayName.ifBlank { model.id })
    }
    var contextWindow by remember(model.id, model.contextWindowTokens) { mutableStateOf(model.contextWindowTokens?.toString().orEmpty()) }
    var maxInput by remember(model.id, model.maxInputTokens) { mutableStateOf(model.maxInputTokens?.toString().orEmpty()) }
    var maxOutput by remember(model.id, model.maxOutputTokens) { mutableStateOf(model.maxOutputTokens?.toString().orEmpty()) }
    val detectedContext = remember(model.id) { ModelContextDetector.detectContextWindow(model.id) }
    val detectedMaxOutput = remember(model.id, detectedContext) {
        ModelContextDetector.detectMaxOutputTokens(model.id, contextWindow = detectedContext)
    }
    val candidate = runCatching {
        model.copy(
            displayName = displayName.trim().ifBlank { model.id },
            contextWindowTokens = contextWindow.toPositiveTokenCount(),
            maxInputTokens = maxInput.toPositiveTokenCount(),
            maxOutputTokens = maxOutput.toPositiveTokenCount()
        ).also { com.amaya.intelligence.data.remote.api.normalizeConfiguredModel(it) }
    }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Configure Model"
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            singleLine = true
        )
        if (detectedContext != null && contextWindow != detectedContext.toString()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Detected: ${ModelContextDetector.formatTokenCount(detectedContext)} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = {
                        contextWindow = detectedContext.toString()
                        detectedMaxOutput?.let { maxOutput = it.toString() }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Apply auto-detect", fontSize = 12.sp)
                }
            }
        }
        TokenField(
            label = "Context window",
            value = contextWindow
        ) { contextWindow = it }
        TokenField("Max input", maxInput) { maxInput = it }
        TokenField("Max output", maxOutput) { maxOutput = it }

        if (onTestLatency != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Model Latency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = onTestLatency,
                            enabled = !isTestingLatency,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (isTestingLatency) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Testing…", fontSize = 12.sp)
                            } else {
                                Text(if (latencyResult == null) "Test Latency" else "Retest", fontSize = 12.sp)
                            }
                        }
                    }
                    if (latencyResult != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (latencyResult.isSuccess) {
                                            when {
                                                latencyResult.latencyMs < 600 -> Color(0xFF4CAF50)
                                                latencyResult.latencyMs < 1500 -> Color(0xFFFFA000)
                                                else -> Color(0xFFFF7043)
                                            }
                                        } else MaterialTheme.colorScheme.error
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (latencyResult.isSuccess) "Response time: ${latencyResult.latencyMs} ms" else "Failed to respond",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (latencyResult.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!latencyResult.errorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = latencyResult.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (!latencyResult.sampleResponse.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sample response: \"${latencyResult.sampleResponse.trim()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        candidate.exceptionOrNull()?.message?.let { message -> InlineError(message) }
        Button(
            onClick = { candidate.getOrNull()?.let(onSave) },
            enabled = candidate.isSuccess && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Save")
        }
    }
}

@Composable
private fun TokenField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.all(Char::isDigit)) onValueChange(input) },
        label = { Text(label) },
        placeholder = { Text("Auto") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    )
}

private fun String.toPositiveTokenCount(): Int? = trim().takeIf(String::isNotBlank)?.toIntOrNull()

@Composable
fun ModernModelToggleRow(
    model: ConfiguredModel,
    providerId: String,
    checked: Boolean,
    colors: com.amaya.intelligence.ui.screens.amaya.IosAmayaColors,
    latencyResult: ModelLatencyResult? = null,
    isTestingLatency: Boolean = false,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
    onTestLatency: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConfigure)
            .padding(
                horizontal = AmayaGroupedSettingsTokens.rowHorizontalPadding,
                vertical = AmayaGroupedSettingsTokens.rowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AmayaGroupedSettingsTokens.rowIconSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(colors.iconBackground),
            contentAlignment = Alignment.Center
        ) {
            com.amaya.intelligence.ui.components.shared.ModelLeadingIcon(
                modelId = model.id,
                providerId = providerId,
                modifier = Modifier.size(AmayaGroupedSettingsTokens.rowIconGlyphSize),
                tint = colors.iconTint
            )
        }
        Spacer(Modifier.width(AmayaGroupedSettingsTokens.rowIconTextGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.displayName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 19.sp
                ),
                color = colors.primaryText
            )
            val contextTokens = model.contextWindowTokens
                ?: ModelContextDetector.detectContextWindow(model.id)
            val subtext = buildList {
                if (model.displayName != model.id) add(model.id)
                if (contextTokens != null) add("${ModelContextDetector.formatTokenCount(contextTokens)} context")
            }.joinToString(" • ")

            if (subtext.isNotBlank()) {
                Spacer(Modifier.height(AmayaGroupedSettingsTokens.inlineTextSpacing))
                Text(
                    subtext,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }
            if (latencyResult != null) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (latencyResult.isSuccess) {
                                    when {
                                        latencyResult.latencyMs < 600 -> Color(0xFF4CAF50)
                                        latencyResult.latencyMs < 1500 -> Color(0xFFFFA000)
                                        else -> Color(0xFFFF7043)
                                    }
                                } else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (latencyResult.isSuccess) "${latencyResult.latencyMs} ms" else "Failed to respond",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (latencyResult.isSuccess) colors.secondaryText else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (onTestLatency != null) {
            IconButton(
                onClick = onTestLatency,
                enabled = !isTestingLatency,
                modifier = Modifier.size(32.dp)
            ) {
                if (isTestingLatency) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = "Test latency for ${model.displayName}",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            Icons.Default.ChevronRight,
            "Configure ${model.displayName}",
            tint = colors.secondaryText,
            modifier = Modifier.size(AmayaGroupedSettingsTokens.rowChevronSize)
        )
        Spacer(Modifier.width(AmayaGroupedSettingsTokens.rowHorizontalPadding))
        com.amaya.intelligence.ui.screens.amaya.AmayaSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

internal fun mergeConfiguredModels(
    savedModels: List<ConfiguredModel>,
    refreshedModels: List<ConfiguredModel>
): List<ConfiguredModel> {
    val savedById = savedModels.associateBy { it.id }
    val refreshedById = refreshedModels.associateBy { it.id }
    val allIds = (refreshedModels.map { it.id } + savedModels.map { it.id }).distinct()

    return allIds.mapNotNull { id ->
        val saved = savedById[id]
        val refreshed = refreshedById[id]
        when {
            saved != null && refreshed != null -> {
                saved.copy(
                    contextWindowTokens = saved.contextWindowTokens ?: refreshed.contextWindowTokens,
                    maxOutputTokens = saved.maxOutputTokens ?: refreshed.maxOutputTokens,
                    maxInputTokens = saved.maxInputTokens ?: refreshed.maxInputTokens,
                    supportsTools = refreshed.supportsTools,
                    supportsImages = refreshed.supportsImages
                )
            }
            saved != null -> saved
            refreshed != null -> refreshed.copy(enabled = false)
            else -> null
        }
    }.sortedBy { it.displayName.lowercase() }
}
