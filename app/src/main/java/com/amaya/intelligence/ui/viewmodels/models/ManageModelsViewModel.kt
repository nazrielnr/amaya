package com.amaya.intelligence.ui.viewmodels.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.remote.api.ActiveModelSelection
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.AmayaProviderRegistry
import com.amaya.intelligence.data.remote.api.CodexAuthManager
import com.amaya.intelligence.data.remote.api.ConfiguredModel
import com.amaya.intelligence.data.remote.api.MemoryEmbeddingConfig
import com.amaya.intelligence.data.remote.api.ProviderAdapter
import com.amaya.intelligence.data.remote.api.ProviderConfig
import com.amaya.intelligence.data.remote.api.ProviderConnection
import com.amaya.intelligence.data.remote.api.ProviderModelService
import com.amaya.intelligence.data.remote.api.ModelLatencyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageModelsViewModel @Inject constructor(
    private val settingsManager: AiSettingsManager,
    private val providerModelService: ProviderModelService,
    private val codexAuthManager: CodexAuthManager
) : ViewModel() {
    val settings = settingsManager.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.amaya.intelligence.data.remote.api.AiSettings()
    )

    data class OperationState(
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _operation = MutableStateFlow(OperationState())
    val operation: StateFlow<OperationState> = _operation.asStateFlow()

    private val _modelLatencies = MutableStateFlow<Map<String, ModelLatencyResult>>(emptyMap())
    val modelLatencies: StateFlow<Map<String, ModelLatencyResult>> = _modelLatencies.asStateFlow()

    private val _testingModelId = MutableStateFlow<String?>(null)
    val testingModelId: StateFlow<String?> = _testingModelId.asStateFlow()

    fun testModelLatency(
        connection: ProviderConnection,
        modelId: String
    ) {
        viewModelScope.launch {
            _testingModelId.value = modelId
            runCatching {
                val apiKey = settingsManager.getConnectionApiKey(connection.id)
                providerModelService.testModelLatency(
                    providerId = connection.providerId,
                    baseUrlOverride = connection.baseUrl,
                    apiKey = apiKey,
                    modelId = modelId
                )
            }.onSuccess { result ->
                _modelLatencies.update { it + (modelId to result) }
                _testingModelId.value = null
            }.onFailure { failure ->
                _modelLatencies.update {
                    it + (modelId to ModelLatencyResult(
                        modelId = modelId,
                        latencyMs = 0,
                        isSuccess = false,
                        errorMessage = failure.message ?: "Failed to test model"
                    ))
                }
                _testingModelId.value = null
            }
        }
    }

    fun testActiveModelsLatency(connection: ProviderConnection) {
        viewModelScope.launch {
            val apiKey = settingsManager.getConnectionApiKey(connection.id)
            val active = connection.visibleModels.filter { it.enabled }
            for (model in active) {
                _testingModelId.value = model.id
                runCatching {
                    providerModelService.testModelLatency(
                        providerId = connection.providerId,
                        baseUrlOverride = connection.baseUrl,
                        apiKey = apiKey,
                        modelId = model.id
                    )
                }.onSuccess { result ->
                    _modelLatencies.update { it + (model.id to result) }
                }.onFailure { failure ->
                    _modelLatencies.update {
                        it + (model.id to ModelLatencyResult(
                            modelId = model.id,
                            latencyMs = 0,
                            isSuccess = false,
                            errorMessage = failure.message ?: "Failed to test model"
                        ))
                    }
                }
            }
            _testingModelId.value = null
        }
    }

    fun connect(
        provider: ProviderConfig,
        name: String,
        baseUrl: String,
        apiKey: String,
        onSuccess: (ProviderConnection, List<ConfiguredModel>) -> Unit
    ) {
        viewModelScope.launch {
            _operation.value = OperationState(loading = true)
            runCatching {
                val models = providerModelService
                    .testAndListModels(provider.id, baseUrl, apiKey)
                    .getOrThrow()
                    .map { it.copy(enabled = false) }
                val normalizedUrl = providerModelService
                    .validateConnectionUrl(provider.id, baseUrl)
                    .getOrThrow()
                val connection = ProviderConnection(
                    name = name.trim().ifBlank { provider.displayName },
                    providerId = provider.id,
                    baseUrl = normalizedUrl,
                    visibleModels = models
                )
                settingsManager.saveConnection(connection, apiKey)
                connection to models
            }.onSuccess { (connection, models) ->
                _operation.value = OperationState()
                onSuccess(connection, models)
            }.onFailure { failure ->
                _operation.value = OperationState(error = failure.message ?: "Connection failed")
            }
        }
    }

    fun saveSubscriptionConnection(onSuccess: (ProviderConnection) -> Unit) {
        viewModelScope.launch {
            runCatching {
                require(codexAuthManager.isAuthenticated()) { "Sign in to OpenAI first" }
                val existing = settings.value.connections.firstOrNull {
                    it.providerId == "openai_codex_bridge"
                }
                val connection = existing ?: ProviderConnection(
                    name = "OpenAI",
                    providerId = "openai_codex_bridge"
                )
                settingsManager.saveConnection(connection)
                connection
            }.onSuccess(onSuccess)
                .onFailure { failure ->
                    _operation.value = OperationState(error = failure.message ?: "Could not save OpenAI account")
                }
        }
    }

    fun refresh(
        connection: ProviderConnection,
        onFailure: () -> Unit = {},
        onSuccess: (List<ConfiguredModel>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val provider = AmayaProviderRegistry.require(connection.providerId)
            if (provider.isSubscription) return@launch
            _operation.value = OperationState(loading = true)
            providerModelService.testAndListModels(
                providerId = provider.id,
                baseUrlOverride = connection.baseUrl,
                apiKey = settingsManager.getConnectionApiKey(connection.id)
            ).onSuccess { models ->
                _operation.value = OperationState()
                onSuccess(models)
            }.onFailure { failure ->
                _operation.value = OperationState(error = failure.message ?: "Could not refresh models")
                onFailure()
            }
        }
    }

    fun cacheModels(
        connection: ProviderConnection,
        models: List<ConfiguredModel>,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _operation.value = OperationState(loading = true)
            runCatching {
                settingsManager.saveConnection(connection.copy(visibleModels = models))
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.value = OperationState(error = failure.message ?: "Could not save models")
                onFailure()
            }
        }
    }

    fun saveWithoutModelList(
        provider: ProviderConfig,
        name: String,
        baseUrl: String,
        apiKey: String,
        onSuccess: (ProviderConnection) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                require(provider.isCustom) { "Manual model entry is only available for custom endpoints" }
                val normalizedUrl = providerModelService
                    .validateConnectionUrl(provider.id, baseUrl)
                    .getOrThrow()
                val connection = ProviderConnection(
                    name = name.trim().ifBlank { provider.displayName },
                    providerId = provider.id,
                    baseUrl = normalizedUrl
                )
                settingsManager.saveConnection(connection, apiKey)
                connection
            }.onSuccess { connection ->
                _operation.value = OperationState()
                onSuccess(connection)
            }.onFailure { failure ->
                _operation.value = OperationState(error = failure.message ?: "Could not save provider")
            }
        }
    }

    fun replaceCredential(
        connection: ProviderConnection,
        apiKey: String,
        onSuccess: (List<ConfiguredModel>) -> Unit
    ) {
        viewModelScope.launch {
            val provider = AmayaProviderRegistry.require(connection.providerId)
            _operation.value = OperationState(loading = true)
            runCatching {
                if (provider.isCustom) {
                    settingsManager.replaceConnectionApiKey(connection.id, apiKey)
                    connection.visibleModels
                } else {
                    val models = providerModelService
                        .testAndListModels(provider.id, connection.baseUrl, apiKey)
                        .getOrThrow()
                    settingsManager.replaceConnectionApiKey(connection.id, apiKey)
                    models
                }
            }.onSuccess { models ->
                _operation.value = OperationState()
                onSuccess(models)
            }.onFailure { failure ->
                _operation.value = OperationState(error = failure.message ?: "Credential rejected")
            }
        }
    }

    fun renameConnection(
        connection: ProviderConnection,
        name: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            runCatching {
                settingsManager.saveConnection(connection.copy(name = name))
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not rename provider") }
            }
        }
    }

    fun updateBaseUrl(
        connection: ProviderConnection,
        baseUrl: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            runCatching {
                val normalizedUrl = providerModelService
                    .validateConnectionUrl(connection.providerId, baseUrl)
                    .getOrThrow()
                settingsManager.saveConnection(connection.copy(baseUrl = normalizedUrl))
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not update base URL") }
            }
        }
    }

    fun saveVisibleModels(
        connectionId: String,
        models: List<ConfiguredModel>,
        onSuccess: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            val active = settings.value.activeSelection
            val needsSelection = active == null ||
                active.connectionId == connectionId && models.none { it.id == active.modelId }
            runCatching {
                settingsManager.setVisibleModels(connectionId, models)
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess(needsSelection)
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not save models") }
            }
        }
    }

    fun saveModel(connectionId: String, model: ConfiguredModel, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            runCatching {
                settingsManager.updateConfiguredModel(connectionId, model)
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not save model") }
            }
        }
    }

    fun addModel(connectionId: String, model: ConfiguredModel, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            runCatching {
                settingsManager.addConfiguredModel(connectionId, model)
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not add model") }
            }
        }
    }

    fun selectModel(connectionId: String, modelId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                settingsManager.setActiveModel(ActiveModelSelection(connectionId, modelId))
            }.onSuccess { onSuccess() }
                .onFailure { failure ->
                    _operation.update { it.copy(error = failure.message ?: "Could not select model") }
                }
        }
    }

    fun deleteConnection(connectionId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val isCodexSubscription = settings.value.connections
                .firstOrNull { it.id == connectionId }
                ?.providerId == "openai_codex_bridge"
            runCatching {
                settingsManager.deleteConnection(connectionId)
                if (isCodexSubscription) codexAuthManager.logout()
            }.onSuccess { onSuccess() }
                .onFailure { failure ->
                    _operation.update { it.copy(error = failure.message ?: "Could not delete provider") }
                }
        }
    }

    /**
     * Saves the semantic memory (embedding recall) configuration, reusing an existing provider
     * connection for the endpoint + credential. `connection` may be null only when disabling.
     */
    fun saveSemanticMemory(
        enabled: Boolean,
        connection: ProviderConnection?,
        modelId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _operation.update { it.copy(loading = true, error = null) }
            runCatching {
                if (enabled) {
                    require(connection != null) { "Choose a provider connection for embeddings" }
                    require(connection.baseUrl.isNotBlank()) { "Provider has no base URL" }
                    require(modelId.isNotBlank()) { "Choose an embedding model" }
                }
                val provider = connection?.let { AmayaProviderRegistry.find(it.providerId) }
                val config = MemoryEmbeddingConfig(
                    enabled = enabled,
                    format = if (provider?.adapter == ProviderAdapter.GEMINI) "gemini" else "openai_compatible",
                    endpoint = connection?.baseUrl.orEmpty().trim().trimEnd('/'),
                    model = modelId.trim(),
                    connectionId = connection?.id
                )
                settingsManager.saveMemoryEmbedding(config)
            }.onSuccess {
                _operation.value = OperationState()
                onSuccess()
            }.onFailure { failure ->
                _operation.update { it.copy(loading = false, error = failure.message ?: "Could not save semantic memory settings") }
            }
        }
    }

    fun hasCredential(connectionId: String): Boolean =
        settingsManager.hasConnectionApiKey(connectionId)

    fun clearOperation() {
        _operation.value = OperationState()
    }
}
