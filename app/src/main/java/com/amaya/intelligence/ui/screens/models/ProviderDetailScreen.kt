package com.amaya.intelligence.ui.screens.models

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

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
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.configuration)) {
                        ModelSettingsRow(
                            icon = Icons.Default.Edit,
                            title = stringResource(R.string.provider_name),
                            subtitle = connection.name,
                            colors = colors,
                            onClick = { editingProviderName = true }
                        )
                        if (!isSubscription) {
                            ModelDivider(colors)
                            if (isCustomProvider) {
                                ModelSettingsRow(
                                    Icons.Default.Link,
                                    stringResource(R.string.base_url),
                                    connection.baseUrl,
                                    colors,
                                    onClick = { editingBaseUrl = true }
                                )
                                ModelDivider(colors)
                            }
                            ModelSettingsRow(
                                Icons.Default.Key,
                                stringResource(R.string.change_api_key),
                                if (hasCredential) stringResource(R.string.api_key_saved) else stringResource(R.string.add_api_key),
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
                                Icon(Icons.Default.Close, stringResource(R.string.clear_search), tint = colors.secondaryText)
                            }
                        },
                        placeholder = { Text(stringResource(R.string.search_models), color = colors.secondaryText) },
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
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.models)) {
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
                                    Text(stringResource(R.string.no_models_found), color = colors.secondaryText)
                                }
                            }
                        } else {
                            filteredModels.forEachIndexed { index, model ->
                                ModernModelToggleRow(
                                    model = model,
                                    providerId = connection.providerId,
                                    checked = model.id in selectedIds,
                                    colors = colors,
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
                                    onConfigure = { editingModel = model }
                                )
                                if (index < filteredModels.lastIndex) ModelDivider(colors)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(AmayaGroupedSettingsTokens.inlineTextSpacing))
                    com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.danger_zone)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    confirmDeleteProvider = true
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.delete_provider), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                                Toast.makeText(context, context.getString(R.string.refreshing_models), Toast.LENGTH_SHORT).show()
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
                        contentDescription = stringResource(R.string.refresh_models)
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
                icon = { Icon(Icons.Default.Add, stringResource(R.string.add_custom_model)) },
                text = { Text(stringResource(R.string.custom_model)) },
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
            title = stringResource(R.string.delete_provider)
        ) {
            Text(
                stringResource(R.string.this_removes_the_provider_its_configured_models_and_any_saved_cre),
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
                else Text(stringResource(R.string.delete_provider))
            }
            OutlinedButton(
                onClick = { confirmDeleteProvider = false },
                enabled = !operation.loading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.cancel))
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

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_custom_model_sentence)
    ) {
        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.model_id)) },
            supportingText = { Text(stringResource(R.string.use_the_id_accepted_by_this_provider)) },
            singleLine = true
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.display_name_optional)) },
            singleLine = true
        )
        if (duplicate) InlineError(stringResource(R.string.a_model_with_this_id_already_exists))
        Button(
            onClick = {
                onSave(
                    ConfiguredModel(
                        id = normalizedId,
                        displayName = displayName.trim().ifBlank { normalizedId }
                    )
                )
            },
            enabled = normalizedId.isNotBlank() && !duplicate && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.add_model))
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
        title = stringResource(R.string.base_url)
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.base_url)) },
            placeholder = { Text("https://example.com/v1") },
            supportingText = { Text(stringResource(R.string.use_the_api_root_without_models_or_a_query)) },
            singleLine = true
        )
        Button(
            onClick = { onSave(baseUrl.trim()) },
            enabled = baseUrl.isNotBlank() && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.save_base_url))
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
        title = stringResource(R.string.provider_name)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.provider_name_sentence)) },
            singleLine = true
        )
        Button(
            onClick = { onSave(name.trim()) },
            enabled = name.isNotBlank() && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.save_name))
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
        title = stringResource(R.string.change_api_key)
    ) {
        Text(
            stringResource(R.string.update_provider_key, providerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (credentialRequired) stringResource(R.string.new_api_key) else stringResource(R.string.new_api_key_optional)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (showKey) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key)
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
            else Text(stringResource(R.string.update_api_key))
        }
    }
}

@Composable
private fun ModelConfigurationSheet(
    model: ConfiguredModel,
    operation: ManageModelsViewModel.OperationState,
    onDismiss: () -> Unit,
    onSave: (ConfiguredModel) -> Unit
) {
    var displayName by remember(model.id, model.displayName) {
        mutableStateOf(model.displayName.ifBlank { model.id })
    }
    var contextWindow by remember(model.id, model.contextWindowTokens) { mutableStateOf(model.contextWindowTokens?.toString().orEmpty()) }
    var maxInput by remember(model.id, model.maxInputTokens) { mutableStateOf(model.maxInputTokens?.toString().orEmpty()) }
    var maxOutput by remember(model.id, model.maxOutputTokens) { mutableStateOf(model.maxOutputTokens?.toString().orEmpty()) }
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
        title = stringResource(R.string.configure_model)
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.display_name)) },
            singleLine = true
        )
        TokenField(stringResource(R.string.context_window), contextWindow) { contextWindow = it }
        TokenField(stringResource(R.string.max_input), maxInput) { maxInput = it }
        TokenField(stringResource(R.string.max_output), maxOutput) { maxOutput = it }
        candidate.exceptionOrNull()?.message?.let { message -> InlineError(message) }
        Button(
            onClick = { candidate.getOrNull()?.let(onSave) },
            enabled = candidate.isSuccess && !operation.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun TokenField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.all(Char::isDigit)) onValueChange(input) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.auto)) },
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
    onToggle: () -> Unit,
    onConfigure: () -> Unit
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
            if (model.displayName != model.id) {
                Spacer(Modifier.height(AmayaGroupedSettingsTokens.inlineTextSpacing))
                Text(
                    model.id,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = colors.secondaryText,
                    maxLines = 1
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            stringResource(R.string.configure_named_model, model.displayName),
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
    val refreshedById = refreshedModels.associateBy { it.id }
    return (refreshedModels + savedModels.map { saved ->
        // Keep user configuration, but replace stale image metadata from older catalogs.
        refreshedById[saved.id]?.let { refreshed ->
            saved.copy(supportsImages = refreshed.supportsImages)
        } ?: saved
    })
        .associateBy { it.id }
        .values
        .sortedBy { it.displayName.lowercase() }
}
