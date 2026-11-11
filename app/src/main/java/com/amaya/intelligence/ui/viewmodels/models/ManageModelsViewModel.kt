package com.amaya.intelligence.ui.viewmodels.models

import com.amaya.intelligence.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.remote.api.ActiveModelSelection
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.AmayaProviderRegistry
import com.amaya.intelligence.data.remote.api.CodexAuthManager
import com.amaya.intelligence.data.remote.api.ConfiguredModel
import com.amaya.intelligence.data.remote.api.ProviderConfig
import com.amaya.intelligence.data.remote.api.ProviderConnection
import com.amaya.intelligence.data.remote.api.ProviderModelService
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
                _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.connection_failed))
            }
        }
    }

    fun saveSubscriptionConnection(onSuccess: (ProviderConnection) -> Unit) {
        viewModelScope.launch {
            runCatching {
                require(codexAuthManager.isAuthenticated()) { androidx.core.content.ContextCompat.getString(context, R.string.sign_in_to_openai_first) }
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
                    _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_save_openai_account))
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
                _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_refresh_models))
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
                _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_save_models))
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
                require(provider.isCustom) { androidx.core.content.ContextCompat.getString(context, R.string.manual_model_entry_is_only_available_for_custom_endpoints) }
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
                _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_save_provider))
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
                _operation.value = OperationState(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.credential_rejected))
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
                _operation.update { it.copy(loading = false, error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_rename_provider)) }
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
                _operation.update { it.copy(loading = false, error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_update_base_url)) }
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
                _operation.update { it.copy(loading = false, error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_save_models)) }
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
                _operation.update { it.copy(loading = false, error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_save_model)) }
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
                _operation.update { it.copy(loading = false, error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_add_model)) }
            }
        }
    }

    fun selectModel(connectionId: String, modelId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                settingsManager.setActiveModel(ActiveModelSelection(connectionId, modelId))
            }.onSuccess { onSuccess() }
                .onFailure { failure ->
                    _operation.update { it.copy(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_select_model)) }
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
                    _operation.update { it.copy(error = failure.message ?: androidx.core.content.ContextCompat.getString(context, R.string.could_not_delete_provider)) }
                }
        }
    }

    fun hasCredential(connectionId: String): Boolean =
        settingsManager.hasConnectionApiKey(connectionId)

    fun clearOperation() {
        _operation.value = OperationState()
    }
}
