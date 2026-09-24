package com.amaya.intelligence.ui.screens.models


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.data.remote.api.*

import com.amaya.intelligence.domain.models.ModelOption
import com.amaya.intelligence.ui.components.shared.ModelLeadingIcon
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import com.amaya.intelligence.ui.viewmodels.models.ManageModelsViewModel

private enum class ModelsSheet { PROVIDERS, SETUP, SELECT_MODEL, SEMANTIC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageModelsScreen(
    codexAuthManager: CodexAuthManager,
    onNavigateBack: () -> Unit,
    onNavigateToProvider: (String) -> Unit,
    viewModel: ManageModelsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val operation by viewModel.operation.collectAsState()
    val codexState by codexAuthManager.authState.collectAsState()
    val context = LocalContext.current
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()

    var sheet by remember { mutableStateOf<ModelsSheet?>(null) }
    var setupProvider by remember { mutableStateOf<ProviderConfig?>(null) }


    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0.dp)) { paddingValues ->
        Box(Modifier.padding(paddingValues).fillMaxSize().background(colors.groupedBackground)) {
            ConnectionsOverview(
                settings = settings,
                colors = colors,
                onConnection = { onNavigateToProvider(it.id) },
                onSelectModel = { sheet = ModelsSheet.SELECT_MODEL },
                onAddProvider = { sheet = ModelsSheet.PROVIDERS },
                onSemanticMemory = { sheet = ModelsSheet.SEMANTIC }
            )

            com.amaya.intelligence.ui.screens.amaya.AmayaTopScrim(
                Modifier.align(Alignment.TopCenter)
            )

            TopAppBar(
                title = {
                    Text(
                        "Manage Models",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
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
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp),
                windowInsets = WindowInsets(0.dp)
            )

            ExtendedFloatingActionButton(
                onClick = { sheet = ModelsSheet.PROVIDERS },
                icon = { Icon(Icons.Default.Add, "Add provider") },
                text = { Text("Provider") },
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

    when (sheet) {
        ModelsSheet.PROVIDERS -> ProviderPickerSheet(
            onDismiss = { sheet = null },
            onSelect = { provider ->
                viewModel.clearOperation()
                setupProvider = provider
                sheet = ModelsSheet.SETUP
            }
        )
        ModelsSheet.SETUP -> setupProvider?.let { provider ->
            ProviderSetupSheet(
                provider = provider,
                operation = operation,
                codexAuthenticated = codexAuthManager.isAuthenticated(),
                codexAccount = codexAuthManager.getAccountEmail(),
                codexState = codexState,
                onSignIn = { codexAuthManager.startLocalServerLogin(context) },
                onStartManualCallbackSignIn = { codexAuthManager.startManualCallbackLogin(context) },
                onSubmitManualCallbackUrl = codexAuthManager::submitManualCallbackUrl,
                onCancelSignIn = codexAuthManager::cancel,
                onConnect = { name, baseUrl, apiKey ->
                    viewModel.connect(provider, name, baseUrl, apiKey) { saved, _ ->
                        onNavigateToProvider(saved.id)
                        sheet = null
                    }
                },
                onSaveWithoutModelList = { name, baseUrl, apiKey ->
                    viewModel.saveWithoutModelList(provider, name, baseUrl, apiKey) { saved ->
                        onNavigateToProvider(saved.id)
                        sheet = null
                    }
                },
                onSaveSubscription = {
                    viewModel.saveSubscriptionConnection { saved ->
                        onNavigateToProvider(saved.id)
                        sheet = null
                    }
                },
                onBack = { sheet = ModelsSheet.PROVIDERS },
                onDismiss = { sheet = null }
            )
        }
        ModelsSheet.SELECT_MODEL -> SelectModelSheet(
            settings = settings,
            onSelect = { option ->
                viewModel.selectModel(option.connectionId, option.modelId) { sheet = null }
            },
            onDismiss = { sheet = null }
        )
        ModelsSheet.SEMANTIC -> SemanticMemorySheet(
            settings = settings,
            operation = operation,
            hasCredential = viewModel::hasCredential,
            onDismiss = { sheet = null },
            onSave = { enabled, connection, modelId ->
                viewModel.saveSemanticMemory(enabled, connection, modelId) { sheet = null }
            }
        )
        null -> Unit
    }
}

@Composable
private fun ConnectionsOverview(
    settings: AiSettings,
    colors: com.amaya.intelligence.ui.screens.amaya.IosAmayaColors,
    onConnection: (ProviderConnection) -> Unit,
    onSelectModel: () -> Unit,
    onAddProvider: () -> Unit,
    onSemanticMemory: () -> Unit
) {
    val active = settings.activeSelection?.let { selection ->
        settings.connections.firstOrNull { it.id == selection.connectionId }
            ?.let { connection -> connection to connection.visibleModels.firstOrNull { it.id == selection.modelId && it.enabled } }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
                Spacer(
                    Modifier
                        .statusBarsPadding()
                        .height(AmayaGroupedSettingsTokens.screenContentTopSpacer)
                )
        }
        if (settings.connections.isEmpty()) {
            item {
                com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
                    title = "No Models Configured",
                    subtitle = "Add an API or subscription provider to start using AI models.",
                    icon = Icons.Default.SmartToy,
                    buttonText = "Add Provider",
                    onButtonClick = onAddProvider,
                    titleColor = colors.primaryText,
                    subtitleColor = colors.secondaryText,
                    iconTint = colors.secondaryText.copy(alpha = 0.45f),
                    modifier = Modifier.fillParentMaxHeight(0.75f)
                )
            }
        } else {
            item {
                com.amaya.intelligence.ui.screens.amaya.AmayaSection("Active Model") {
                    ModelSettingsRow(
                        icon = Icons.Default.Psychology,
                        title = active?.second?.displayName ?: "Select Model",
                        subtitle = active?.first?.name ?: "Choose a model for chat",
                        colors = colors,
                        onClick = onSelectModel,
                        modelId = active?.second?.id,
                        providerId = active?.first?.providerId
                    )
                }
            }
            item {
                com.amaya.intelligence.ui.screens.amaya.AmayaSection("Providers") {
                    settings.connections.forEachIndexed { index, connection ->
                        ModelSettingsRow(
                            icon = if (AmayaProviderRegistry.find(connection.providerId)?.isSubscription == true) Icons.Default.AccountCircle else Icons.Default.Api,
                            title = connection.name,
                            subtitle = "${AmayaProviderRegistry.displayName(connection.providerId)} · ${connection.visibleModels.size} ${if (connection.visibleModels.size == 1) "model" else "models"}",
                            colors = colors,
                            onClick = { onConnection(connection) }
                        )
                        if (index < settings.connections.lastIndex) ModelDivider(colors)
                    }
                }
            }
            item {
                com.amaya.intelligence.ui.screens.amaya.AmayaSection("Semantic Memory") {
                    val embedding = settings.memoryEmbedding
                    val refConnection = embedding.connectionId?.let { id -> settings.connections.firstOrNull { it.id == id } }
                    ModelSettingsRow(
                        icon = Icons.Default.Grain,
                        title = if (embedding.enabled) "Enabled" else "Off",
                        subtitle = when {
                            embedding.enabled && refConnection != null && embedding.model.isNotBlank() ->
                                "${embedding.model} · ${refConnection.name}"
                            embedding.enabled && refConnection == null ->
                                "Configured provider was removed — re-select a model"
                            settings.connections.isEmpty() ->
                                "Add a provider connection to configure"
                            else ->
                                "Re-rank memory with an embedding model"
                        },
                        colors = colors,
                        onClick = onSemanticMemory
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(onDismiss: () -> Unit, onSelect: (ProviderConfig) -> Unit) {
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Select Provider",
        scrollable = false
    ) {
        LazyColumn(contentPadding = com.amaya.intelligence.ui.components.shared.StandardModalSheetDefaults.ContentPadding, modifier = Modifier.fillMaxWidth().heightIn(max = com.amaya.intelligence.ui.components.shared.StandardModalSheetDefaults.MaxHeight)) {
            ProviderCategory.entries.forEach { category ->
                val categoryProviders = AmayaProviderRegistry.providers.filter { it.category == category }
                if (categoryProviders.isNotEmpty()) {
                    item {
                        Text(providerCategoryLabel(category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(categoryProviders) { provider ->
                        Surface(onClick = { dismiss { onSelect(provider) } }, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (provider.isSubscription) Icons.Default.AccountCircle else Icons.Default.Api, null)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(if (provider.isSubscription) "Sign in with your account" else if (provider.isCustom) "Custom endpoint" else "API key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSetupSheet(
    provider: ProviderConfig,
    operation: ManageModelsViewModel.OperationState,
    codexAuthenticated: Boolean,
    codexAccount: String?,
    codexState: CodexAuthState,
    onSignIn: () -> Unit,
    onStartManualCallbackSignIn: () -> Unit,
    onSubmitManualCallbackUrl: (String) -> Unit,
    onCancelSignIn: () -> Unit,
    onConnect: (String, String, String) -> Unit,
    onSaveWithoutModelList: (String, String, String) -> Unit,
    onSaveSubscription: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(provider.id) { mutableStateOf(provider.displayName) }
    var baseUrl by remember(provider.id) { mutableStateOf("") }
    var apiKey by remember(provider.id) { mutableStateOf("") }
    var showKey by remember(provider.id) { mutableStateOf(false) }
    var callbackUrl by remember(provider.id) { mutableStateOf("") }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = provider.displayName,
        actions = {
            com.amaya.intelligence.ui.components.shared.AmayaTopBarButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = { dismiss(onBack) },
                contentDescription = "Back"
            )
        }
    ) {
        if (provider.isSubscription) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (codexAuthenticated) Icons.Default.CheckCircle else Icons.Default.AccountCircle, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (codexAuthenticated) "Connected" else "Not Connected", fontWeight = FontWeight.SemiBold)
                        Text(codexAccount ?: subscriptionStateLabel(codexState), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            operation.error?.let { InlineError(it) }
            when {
                codexAuthenticated -> Button(onClick = onSaveSubscription, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Continue") }
                codexState is CodexAuthState.WaitingForManualCallback -> {
                    Text(
                        "After OpenAI redirects to localhost, copy the complete URL from the browser address bar and paste it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = callbackUrl,
                        onValueChange = { callbackUrl = it },
                        label = { Text("Localhost callback URL") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = { onSubmitManualCallbackUrl(callbackUrl) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = callbackUrl.isNotBlank()
                    ) { Text("Finish Sign In") }
                    OutlinedButton(onClick = onCancelSignIn, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("Cancel Sign In")
                    }
                }
                codexState is CodexAuthState.Starting || codexState is CodexAuthState.WaitingForBrowser || codexState is CodexAuthState.ExchangingToken -> {
                    Button(onClick = onCancelSignIn, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Cancel Sign In") }
                }
                else -> {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Sign In With OpenAI") }
                    OutlinedButton(onClick = onStartManualCallbackSignIn, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("Use Pasted Callback URL")
                    }
                }
            }
        } else {
            OutlinedTextField(name, { name = it }, label = { Text("Connection Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (provider.isCustom) {
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text("https://example.com/v1") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else {
                Text("Requests use ${provider.defaultBaseUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                apiKey,
                { apiKey = it },
                label = { Text(if (provider.credentialRequired) "API Key" else "API Key (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showKey) "Hide API key" else "Show API key")
                    }
                }
            )
            operation.error?.let {
                InlineError(it)
                if (provider.isCustom) {
                    Text(
                        "If this endpoint does not expose a model list, save it now, then add model IDs manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { onSaveWithoutModelList(name, baseUrl, apiKey) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = !operation.loading && baseUrl.isNotBlank()
                    ) { Text("Save Without Model List") }
                }
            }
            Button(
                onClick = { onConnect(name, baseUrl, apiKey) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !operation.loading && (!provider.credentialRequired || apiKey.isNotBlank()) && (!provider.isCustom || baseUrl.isNotBlank())
            ) {
                if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectModelSheet(settings: AiSettings, onSelect: (ModelOption) -> Unit, onDismiss: () -> Unit) {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val allOptions = remember(settings.connections) {
        settings.connections.flatMap { connection ->
            connection.visibleModels.mapNotNull { model ->
                com.amaya.intelligence.impl.common.mappers.ModelUiMapper.mapConnectionModel(connection, model)
            }
        }
    }
    val options = remember(allOptions, query) {
        val value = query.trim().lowercase()
        if (value.isBlank()) allOptions else allOptions.filter {
            it.name.lowercase().contains(value) ||
                it.modelId.lowercase().contains(value) ||
                it.providerName.lowercase().contains(value)
        }
    }
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Select Model",
        scrollable = false,
        actions = {
            com.amaya.intelligence.ui.components.shared.AmayaTopBarButton(
                icon = Icons.Default.Search,
                onClick = { showSearch = !showSearch },
                contentDescription = "Search"
            )
        }
    ) {
        val closeWithSelection: (ModelOption) -> Unit = { option -> dismiss { onSelect(option) } }
        LazyColumn(contentPadding = com.amaya.intelligence.ui.components.shared.StandardModalSheetDefaults.ContentPadding, modifier = Modifier.fillMaxWidth().heightIn(max = com.amaya.intelligence.ui.components.shared.StandardModalSheetDefaults.MaxHeight)) {
            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear search")
                            }
                        },
                        placeholder = { Text("Search models…") },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            if (allOptions.isEmpty()) {
                item { Text("No models shown in chat", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (options.isEmpty()) {
                item { Text("No models found", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                settings.connections.forEach { connection ->
                    val connectionModels = options.filter { it.connectionId == connection.id }
                    if (connectionModels.isNotEmpty()) {
                        item(key = "group_${connection.id}") {
                            com.amaya.intelligence.ui.screens.amaya.AmayaSection(connection.name) {
                                Column {
                                    connectionModels.forEachIndexed { index, option ->
                                        val isActive = option.id == settings.activeSelection?.key
                                        Surface(onClick = { closeWithSelection(option) }, color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent) {
                                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isActive) MaterialTheme.colorScheme.primary else colors.iconBackground),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    ModelLeadingIcon(
                                                        modelId = option.modelId,
                                                        providerId = option.providerId,
                                                        iconType = option.iconType,
                                                        modifier = Modifier.size(17.dp),
                                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else colors.iconTint
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        option.name,
                                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                                        color = if (isActive) MaterialTheme.colorScheme.primary else colors.primaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (option.name != option.modelId) {
                                                        Text(option.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                        if (index < connectionModels.lastIndex) {
                                            HorizontalDivider(color = colors.separator)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}



private fun providerCategoryLabel(category: ProviderCategory): String = when (category) {
    ProviderCategory.SUBSCRIPTION -> "SUBSCRIPTION"
    ProviderCategory.API -> "API KEY"
    ProviderCategory.CUSTOM -> "CUSTOM ENDPOINT"
}

private fun subscriptionStateLabel(state: CodexAuthState): String = when (state) {
    is CodexAuthState.Starting -> "Opening browser…"
    is CodexAuthState.WaitingForBrowser -> "Waiting for browser…"
    is CodexAuthState.WaitingForManualCallback -> "Paste the browser callback URL"
    is CodexAuthState.ExchangingToken -> "Finishing sign in…"
    is CodexAuthState.Error -> state.message
    else -> "Sign in to continue"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemanticMemorySheet(
    settings: AiSettings,
    operation: ManageModelsViewModel.OperationState,
    hasCredential: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, ProviderConnection?, String) -> Unit
) {
    val colors = com.amaya.intelligence.ui.screens.amaya.iosAmayaColors()
    val current = settings.memoryEmbedding
    val eligible = settings.connections.filter { connection ->
        AmayaProviderRegistry.find(connection.providerId)?.isSubscription != true &&
            connection.baseUrl.isNotBlank()
    }
    var enabled by remember { mutableStateOf(current.enabled) }
    var selectedConnectionId by remember {
        mutableStateOf(
            current.connectionId?.takeIf { id -> eligible.any { it.id == id } }
                ?: eligible.firstOrNull()?.id
        )
    }
    var modelId by remember { mutableStateOf(current.model) }
    var connectionExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val selectedConnection = eligible.firstOrNull { it.id == selectedConnectionId }
    val models = selectedConnection?.visibleModels.orEmpty()

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Semantic Memory",
        scrollable = true
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "When enabled, memory recall re-ranks its top local matches by embedding similarity. " +
                    "The provider connection below supplies the endpoint and API key — no separate credential is stored.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
            com.amaya.intelligence.ui.screens.amaya.AmayaSwitchRow(
                title = "Enable semantic recall",
                subtitle = "Re-rank saved memories with an embedding model",
                checked = enabled,
                onCheckedChange = { enabled = it }
            )
            if (eligible.isEmpty()) {
                Text(
                    "No provider with a base URL yet. Add or edit a provider under Providers first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = connectionExpanded,
                    onExpandedChange = { connectionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedConnection?.let { "${it.name} · ${AmayaProviderRegistry.displayName(it.providerId)}" }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        label = { Text("Embedding provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connectionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = connectionExpanded,
                        onDismissRequest = { connectionExpanded = false }
                    ) {
                        eligible.forEach { connection ->
                            DropdownMenuItem(
                                text = { Text("${connection.name} · ${AmayaProviderRegistry.displayName(connection.providerId)}") },
                                onClick = {
                                    selectedConnectionId = connection.id
                                    modelId = connection.visibleModels.firstOrNull()?.id ?: modelId
                                    connectionExpanded = false
                                }
                            )
                        }
                    }
                }
                if (selectedConnection != null && !hasCredential(selectedConnection.id)) {
                    Text(
                        "This provider has no API key saved — semantic recall stays off until you add one in the provider's settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Embedding model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model ID") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
                if (models.isNotEmpty()) {
                    Text(
                        "Quick pick",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    models.take(12).forEach { model ->
                        FilterChip(
                            selected = model.id == modelId,
                            onClick = { modelId = model.id },
                            enabled = enabled,
                            label = { Text(model.displayName.ifBlank { model.id }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
            operation.error?.let { InlineError(it) }
            Button(
                onClick = { onSave(enabled, selectedConnection, modelId.trim()) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !operation.loading
            ) {
                if (operation.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save")
            }
        }
    }
}
