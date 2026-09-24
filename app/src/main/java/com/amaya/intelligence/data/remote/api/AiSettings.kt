package com.amaya.intelligence.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.amaya.intelligence.util.errorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ConfiguredModel(
    val id: String,
    val displayName: String = id,
    val contextWindowTokens: Int? = null,
    val maxInputTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val enabled: Boolean = true,
    val supportsTools: Boolean = true,
    val supportsImages: Boolean = true
)

fun normalizeConfiguredModel(model: ConfiguredModel): ConfiguredModel {
    val normalized = model.copy(
        id = model.id.trim(),
        displayName = model.displayName.trim()
    )
    require(normalized.id.isNotBlank()) { "Model ID is required" }
    listOf(
        "Context window" to normalized.contextWindowTokens,
        "Max input" to normalized.maxInputTokens,
        "Max output" to normalized.maxOutputTokens
    ).forEach { (name, value) ->
        require(value == null || value > 0) { "$name must be greater than zero" }
    }
    normalized.contextWindowTokens?.let { contextWindow ->
        normalized.maxOutputTokens?.let { maxOutput ->
            require(contextWindow > maxOutput) {
                "Context window must exceed max output"
            }
            normalized.maxInputTokens?.let { maxInput ->
                require(maxInput + maxOutput <= contextWindow) {
                    "Max input plus max output cannot exceed the context window"
                }
            }
        }
        normalized.maxInputTokens?.let { maxInput ->
            require(maxInput <= contextWindow) { "Max input cannot exceed the context window" }
        }
    }
    return normalized.copy(displayName = normalized.displayName.ifBlank { normalized.id })
}

data class ProviderConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val providerId: String = "",
    val baseUrl: String = "",
    val visibleModels: List<ConfiguredModel> = emptyList()
)

data class ActiveModelSelection(
    val connectionId: String,
    val modelId: String
) {
    val key: String get() = "model|$connectionId|$modelId"
}

private val Context.dataStore by preferencesDataStore(name = "ai_settings")

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_CONNECTIONS = stringPreferencesKey("provider_connections_v2")
        private val KEY_ACTIVE_SELECTION = stringPreferencesKey("active_model_selection_v2")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_MCP_CONFIG_JSON = stringPreferencesKey("mcp_config_json")
        private val KEY_LAST_WORKSPACE = stringPreferencesKey("last_workspace_path")
        private val KEY_THINKING_EFFORT_PREFIX = "thinking_effort|"
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_EMBEDDING_ENABLED = booleanPreferencesKey("memory_embedding_enabled")
        private val KEY_EMBEDDING_FORMAT = stringPreferencesKey("memory_embedding_format")
        private val KEY_EMBEDDING_ENDPOINT = stringPreferencesKey("memory_embedding_endpoint")
        private val KEY_EMBEDDING_MODEL = stringPreferencesKey("memory_embedding_model")
        private val KEY_EMBEDDING_CONNECTION_ID = stringPreferencesKey("memory_embedding_connection_id")

        private const val ENC_EMBEDDING_API_KEY = "embedding_api_key"

        private val LEGACY_AGENT_CONFIGS = stringPreferencesKey("agent_configs")
        private val LEGACY_ACTIVE_AGENT_ID = stringPreferencesKey("active_agent_id")
        private val LEGACY_ACTIVE_MODEL = stringPreferencesKey("active_model")

        private const val ENC_CONNECTION_KEY_PREFIX = "agent_key_"
        private const val SECURE_PREFS_NAME = "amaya_secure_prefs"
        private const val STARTUP_PREFS_NAME = "amaya_startup_prefs"
        private const val STARTUP_THEME_KEY = "theme"

        const val MCP_FIXED_PATH = "/storage/emulated/0/Amaya/mcp.json"
    }

    private val encryptedPrefs: SharedPreferences by lazy { createEncryptedPrefs() }
    private val startupPrefs by lazy {
        context.getSharedPreferences(STARTUP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { failure ->
            // Degraded mode: never crash the app over credential storage. Devices with a broken
            // Android Keystore (failed provisioning, restored backups, some emulators) would
            // otherwise force-close on the first credential read — including the semantic
            // embedding settings screen. Keys fall back to plain preferences with a warning.
            Log.e("AiSettingsManager", "Secure credential storage unavailable; falling back to plain preferences", failure)
            context.getSharedPreferences(SECURE_PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    val settingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val connections = parseConnections(
            prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
        )
        val activeSelection = parseActiveSelection(prefs[KEY_ACTIVE_SELECTION])
            ?: legacyActiveSelection(
                connections = connections,
                connectionId = prefs[LEGACY_ACTIVE_AGENT_ID].orEmpty(),
                modelId = prefs[LEGACY_ACTIVE_MODEL].orEmpty()
            )
        AiSettings(
            theme = prefs[KEY_THEME] ?: "system",
            connections = connections,
            activeSelection = activeSelection?.takeIf { selection ->
                connections.any { connection ->
                    connection.id == selection.connectionId &&
                        connection.visibleModels.any { it.id == selection.modelId && it.enabled }
                }
            },
            mcpConfigJson = prefs[KEY_MCP_CONFIG_JSON].orEmpty(),
            lastWorkspacePath = prefs[KEY_LAST_WORKSPACE]?.ifBlank { null },
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            memoryEmbedding = MemoryEmbeddingConfig(
                enabled = prefs[KEY_EMBEDDING_ENABLED] ?: false,
                format = prefs[KEY_EMBEDDING_FORMAT] ?: "openai_compatible",
                endpoint = prefs[KEY_EMBEDDING_ENDPOINT].orEmpty(),
                model = prefs[KEY_EMBEDDING_MODEL].orEmpty(),
                connectionId = prefs[KEY_EMBEDDING_CONNECTION_ID]
            )
        )
    }

    @Volatile
    private var cachedSettings: AiSettings? = null

    init {
        // Cache warm-up is an optimization: on-device the Context is always present, but local JVM
        // unit tests run without a mocked Android runtime, so Context-backed access here
        // (startupPrefs -> getSharedPreferences) throws "not mocked" on this background coroutine.
        // Guard it so the failure never escapes as an uncaught exception and poisons later
        // runTest-based tests; reads then fall back to the on-demand guarded path in getSettings().
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                settingsFlow.collect {
                    cachedSettings = it
                    startupPrefs.edit().putString(STARTUP_THEME_KEY, it.theme).apply()
                }
            }.onFailure { failure ->
                errorLog("AiSettingsManager", "Settings cache warm-up failed; reads will use the on-demand path", failure)
            }
        }
    }

    fun getSettings(): AiSettings =
        cachedSettings ?: runCatching { runBlocking { settingsFlow.first() } }
            .getOrElse { failure ->
                Log.w("AiSettingsManager", "Failed to read settings; using defaults", failure)
                AiSettings()
            }.also { cachedSettings = it }

    fun getStartupTheme(): String =
        startupPrefs.getString(STARTUP_THEME_KEY, "system") ?: "system"



    fun getConnectionApiKey(connectionId: String): String =
        encryptedPrefs.getString("$ENC_CONNECTION_KEY_PREFIX$connectionId", "").orEmpty()

    /** Embedding API key (Gemini/OpenAI-compatible/NVIDIA, etc.), stored encrypted. */
    fun getMemoryEmbeddingApiKey(): String =
        encryptedPrefs.getString(ENC_EMBEDDING_API_KEY, "").orEmpty()

    /**
     * Resolves the credential for semantic recall: prefers the referenced provider connection's
     * key, falling back to the legacy standalone embedding key for older configurations.
     */
    fun getEmbeddingApiKey(config: MemoryEmbeddingConfig): String {
        val connectionKey = config.connectionId?.let { getConnectionApiKey(it) }.orEmpty()
        return connectionKey.ifBlank { getMemoryEmbeddingApiKey() }
    }

    suspend fun saveMemoryEmbedding(config: MemoryEmbeddingConfig, apiKey: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMBEDDING_ENABLED] = config.enabled
            prefs[KEY_EMBEDDING_FORMAT] = config.format
            prefs[KEY_EMBEDDING_ENDPOINT] = config.endpoint.trim().trimEnd('/')
            prefs[KEY_EMBEDDING_MODEL] = config.model.trim()
            if (config.connectionId.isNullOrBlank()) {
                prefs.remove(KEY_EMBEDDING_CONNECTION_ID)
            } else {
                prefs[KEY_EMBEDDING_CONNECTION_ID] = config.connectionId
            }
        }
        apiKey?.let { key ->
            val editor = encryptedPrefs.edit()
            if (key.isBlank()) editor.remove(ENC_EMBEDDING_API_KEY)
            else editor.putString(ENC_EMBEDDING_API_KEY, key.trim())
            if (!editor.commit()) error("Could not save the embedding credential securely")
        }
    }

    fun hasConnectionApiKey(connectionId: String): Boolean =
        getConnectionApiKey(connectionId).isNotBlank()

    fun getEncryptedPrefsForCodex(): SharedPreferences = encryptedPrefs

    suspend fun saveConnection(connection: ProviderConnection, apiKey: String? = null) {
        require(connection.id.isNotBlank()) { "Connection ID is required" }
        require(AmayaProviderRegistry.find(connection.providerId) != null) { "Unsupported provider: ${connection.providerId}" }
        require(connection.name.isNotBlank()) { "Connection name is required" }
        val normalized = connection.copy(
            name = connection.name.trim(),
            baseUrl = connection.baseUrl.trim().trimEnd('/'),
            visibleModels = connection.visibleModels
                .map(::normalizeConfiguredModel)
                .distinctBy { it.id }
        )
        val credentialKey = "$ENC_CONNECTION_KEY_PREFIX${connection.id}"
        val previousCredential = if (apiKey != null) encryptedPrefs.getString(credentialKey, null) else null
        if (apiKey != null && !encryptedPrefs.edit().putString(credentialKey, apiKey.trim()).commit()) {
            error("Could not save the provider credential securely")
        }
        runCatching {
            context.dataStore.edit { prefs ->
                val list = parseConnections(
                    prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
                ).toMutableList()
                val index = list.indexOfFirst { it.id == normalized.id }
                if (index >= 0) list[index] = normalized else list.add(normalized)
                prefs[KEY_CONNECTIONS] = serializeConnections(list)
                val active = parseActiveSelection(prefs[KEY_ACTIVE_SELECTION])
                    ?: legacyActiveSelection(
                        connections = list,
                        connectionId = prefs[LEGACY_ACTIVE_AGENT_ID].orEmpty(),
                        modelId = prefs[LEGACY_ACTIVE_MODEL].orEmpty()
                    )
                if (active != null) prefs[KEY_ACTIVE_SELECTION] = serializeActiveSelection(active)
                prefs.remove(LEGACY_AGENT_CONFIGS)
                prefs.remove(LEGACY_ACTIVE_AGENT_ID)
                prefs.remove(LEGACY_ACTIVE_MODEL)
            }
        }.onFailure {
            if (apiKey != null) {
                val rollback = encryptedPrefs.edit()
                if (previousCredential == null) rollback.remove(credentialKey)
                else rollback.putString(credentialKey, previousCredential)
                rollback.commit()
            }
        }.getOrThrow()
    }

    suspend fun replaceConnectionApiKey(connectionId: String, apiKey: String) {
        val editor = encryptedPrefs.edit()
        if (apiKey.isBlank()) editor.remove("$ENC_CONNECTION_KEY_PREFIX$connectionId")
        else editor.putString("$ENC_CONNECTION_KEY_PREFIX$connectionId", apiKey.trim())
        if (!editor.commit()) error("Could not replace the provider credential securely")
    }

    suspend fun deleteConnection(connectionId: String) {
        context.dataStore.edit { prefs ->
            val remaining = parseConnections(
                prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
            ).filterNot { it.id == connectionId }
            prefs[KEY_CONNECTIONS] = serializeConnections(remaining)
            val active = parseActiveSelection(prefs[KEY_ACTIVE_SELECTION])
                ?: legacyActiveSelection(
                    connections = remaining,
                    connectionId = prefs[LEGACY_ACTIVE_AGENT_ID].orEmpty(),
                    modelId = prefs[LEGACY_ACTIVE_MODEL].orEmpty()
                )
            if (active?.connectionId == connectionId) {
                prefs.remove(KEY_ACTIVE_SELECTION)
            } else if (active != null) {
                prefs[KEY_ACTIVE_SELECTION] = serializeActiveSelection(active)
            }
            prefs.remove(LEGACY_AGENT_CONFIGS)
            prefs.remove(LEGACY_ACTIVE_AGENT_ID)
            prefs.remove(LEGACY_ACTIVE_MODEL)
        }
        if (!encryptedPrefs.edit().remove("$ENC_CONNECTION_KEY_PREFIX$connectionId").commit()) {
            Log.w("AiSettingsManager", "Connection deleted, but its credential could not be removed")
        }
    }

    suspend fun setActiveModel(selection: ActiveModelSelection?) {
        context.dataStore.edit { prefs ->
            if (selection == null) {
                prefs.remove(KEY_ACTIVE_SELECTION)
            } else {
                val connections = parseConnections(
                    prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
                )
                require(connections.any { connection ->
                    connection.id == selection.connectionId &&
                        connection.visibleModels.any { it.id == selection.modelId && it.enabled }
                }) { "Selected model is not visible for this provider connection" }
                prefs[KEY_ACTIVE_SELECTION] = serializeActiveSelection(selection)
            }
            prefs.remove(LEGACY_ACTIVE_AGENT_ID)
            prefs.remove(LEGACY_ACTIVE_MODEL)
        }
    }

    suspend fun updateConfiguredModel(connectionId: String, model: ConfiguredModel) {
        val normalizedModel = normalizeConfiguredModel(model)
        context.dataStore.edit { prefs ->
            val connections = parseConnections(
                prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
            ).toMutableList()
            val index = connections.indexOfFirst { it.id == connectionId }
            require(index >= 0) { "Provider connection not found" }
            connections[index] = connections[index].copy(
                visibleModels = connections[index].visibleModels.map {
                    if (it.id == normalizedModel.id) normalizedModel else it
                }.let { models ->
                    if (models.any { it.id == normalizedModel.id }) models else models + normalizedModel
                }
            )
            prefs[KEY_CONNECTIONS] = serializeConnections(connections)
            prefs.remove(LEGACY_AGENT_CONFIGS)
        }
    }

    suspend fun addConfiguredModel(connectionId: String, model: ConfiguredModel) {
        val normalizedModel = normalizeConfiguredModel(model)
        context.dataStore.edit { prefs ->
            val connections = parseConnections(
                prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
            ).toMutableList()
            val index = connections.indexOfFirst { it.id == connectionId }
            require(index >= 0) { "Provider connection not found" }
            require(connections[index].visibleModels.none { it.id == normalizedModel.id }) {
                "A model with this ID already exists"
            }
            connections[index] = connections[index].copy(
                visibleModels = connections[index].visibleModels + normalizedModel
            )
            prefs[KEY_CONNECTIONS] = serializeConnections(connections)
            prefs.remove(LEGACY_AGENT_CONFIGS)
        }
    }

    suspend fun setVisibleModels(connectionId: String, models: List<ConfiguredModel>) {
        val normalizedModels = models
            .map(::normalizeConfiguredModel)
            .distinctBy { it.id }
        context.dataStore.edit { prefs ->
            val connections = parseConnections(
                prefs[KEY_CONNECTIONS] ?: prefs[LEGACY_AGENT_CONFIGS].orEmpty()
            ).toMutableList()
            val index = connections.indexOfFirst { it.id == connectionId }
            require(index >= 0) { "Provider connection not found" }
            val active = parseActiveSelection(prefs[KEY_ACTIVE_SELECTION])
                ?: legacyActiveSelection(
                    connections = connections,
                    connectionId = prefs[LEGACY_ACTIVE_AGENT_ID].orEmpty(),
                    modelId = prefs[LEGACY_ACTIVE_MODEL].orEmpty()
                )
            val selectedById = normalizedModels.associateBy { it.id }
            val updatedModels = connections[index].visibleModels.map { existing ->
                selectedById[existing.id]?.copy(enabled = true) ?: existing.copy(enabled = false)
            } + normalizedModels.filter { selected ->
                connections[index].visibleModels.none { it.id == selected.id }
            }.map { selected -> selected.copy(enabled = true) }
            connections[index] = connections[index].copy(visibleModels = updatedModels)
            prefs[KEY_CONNECTIONS] = serializeConnections(connections)
            prefs.remove(LEGACY_AGENT_CONFIGS)
            if (active?.connectionId == connectionId && updatedModels.none { it.id == active.modelId && it.enabled }) {
                prefs.remove(KEY_ACTIVE_SELECTION)
            } else if (active != null) {
                prefs[KEY_ACTIVE_SELECTION] = serializeActiveSelection(active)
            }
            prefs.remove(LEGACY_ACTIVE_AGENT_ID)
            prefs.remove(LEGACY_ACTIVE_MODEL)
        }
    }

    suspend fun setTheme(theme: String) {
        startupPrefs.edit().putString(STARTUP_THEME_KEY, theme).apply()
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setLastWorkspacePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(KEY_LAST_WORKSPACE)
            else prefs[KEY_LAST_WORKSPACE] = path
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMcpConfigJson(json: String) {
        context.dataStore.edit { it[KEY_MCP_CONFIG_JSON] = json }
        writeMcpConfigToFixedPath(json)
    }

    /** Per-model thinking effort persistence. Key: `thinking_effort|<connId>|<modelId>`. */
    suspend fun setThinkingEffort(connectionId: String, modelId: String, effort: ThinkingEffort) {
        val key = stringPreferencesKey("$KEY_THINKING_EFFORT_PREFIX$connectionId|$modelId")
        context.dataStore.edit { prefs ->
            if (effort == ThinkingEffort.MEDIUM) prefs.remove(key)
            else prefs[key] = effort.name
        }
    }

    /** Sync getter; defaults to MEDIUM when unset. */
    fun getThinkingEffort(connectionId: String, modelId: String): ThinkingEffort {
        val key = stringPreferencesKey("$KEY_THINKING_EFFORT_PREFIX$connectionId|$modelId")
        return runCatching {
            kotlinx.coroutines.runBlocking { context.dataStore.data.first() }[key]
                ?.let { runCatching { ThinkingEffort.valueOf(it) }.getOrNull() }
        }.getOrNull() ?: ThinkingEffort.MEDIUM
    }

    suspend fun loadMcpConfigFromFixedPath(): String? = withContext(Dispatchers.IO) {
        File(MCP_FIXED_PATH).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
    }

    suspend fun writeMcpConfigToFixedPath(json: String) = withContext(Dispatchers.IO) {
        File(MCP_FIXED_PATH).apply {
            parentFile?.mkdirs()
            writeText(json)
        }
    }

    private fun parseConnections(json: String): List<ProviderConnection> = runCatching {
        val array = JSONArray(json.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
            val providerId = item.optString("providerId").ifBlank {
                inferLegacyProviderId(
                    providerType = item.optString("providerType"),
                    baseUrl = item.optString("baseUrl"),
                    modelId = item.optString("modelId")
                )
            }
            if (providerId.isBlank() || AmayaProviderRegistry.find(providerId) == null) {
                return@mapNotNull null
            }
            val legacyModelId = item.optString("modelId")
            val modelArray = item.optJSONArray("visibleModels")
            val configuredModels = if (modelArray != null) {
                (0 until modelArray.length()).mapNotNull { modelIndex ->
                    when (val value = modelArray.opt(modelIndex)) {
                        is JSONObject -> value.optString("id").takeIf { it.isNotBlank() }?.let {
                            ConfiguredModel(
                                id = it,
                                displayName = value.optString("displayName", it),
                                contextWindowTokens = value.optInt("contextWindowTokens").takeIf { n -> n > 0 },
                                maxInputTokens = value.optInt("maxInputTokens").takeIf { n -> n > 0 },
                                maxOutputTokens = value.optInt("maxOutputTokens").takeIf { n -> n > 0 },
                                enabled = value.optBoolean("enabled", true),
                                supportsTools = value.optBoolean("supportsTools", true),
                                supportsImages = value.optBoolean("supportsImages", true)
                            )
                        }
                        is String -> value.takeIf { it.isNotBlank() }?.let(::ConfiguredModel)
                        else -> null
                    }
                }
            } else {
                buildList {
                    legacyModelId.takeIf { it.isNotBlank() }?.let { add(ConfiguredModel(it)) }
                    item.optJSONArray("enabledModelIds")?.let { legacy ->
                        repeat(legacy.length()) { modelIndex ->
                            legacy.optString(modelIndex).takeIf { it.isNotBlank() }?.let { add(ConfiguredModel(it)) }
                        }
                    }
                }
            }
            ProviderConnection(
                id = id,
                name = item.optString("name").ifBlank { AmayaProviderRegistry.displayName(providerId) },
                providerId = providerId,
                baseUrl = item.optString("baseUrl"),
                visibleModels = configuredModels.distinctBy { it.id }
            )
        }
    }.getOrElse {
        Log.e("AiSettingsManager", "Could not parse provider connections", it)
        emptyList()
    }

    private fun serializeConnections(connections: List<ProviderConnection>): String =
        JSONArray().also { array ->
            connections.forEach { connection ->
                array.put(JSONObject().apply {
                    put("id", connection.id)
                    put("name", connection.name)
                    put("providerId", connection.providerId)
                    put("baseUrl", connection.baseUrl)
                    put("visibleModels", JSONArray().also { models ->
                        connection.visibleModels.forEach { model ->
                            models.put(JSONObject()
                                .put("id", model.id)
                                .put("displayName", model.displayName)
                                .put("contextWindowTokens", model.contextWindowTokens ?: JSONObject.NULL)
                                .put("maxInputTokens", model.maxInputTokens ?: JSONObject.NULL)
                                .put("maxOutputTokens", model.maxOutputTokens ?: JSONObject.NULL)
                                .put("enabled", model.enabled)
                                .put("supportsTools", model.supportsTools)
                                .put("supportsImages", model.supportsImages))
                        }
                    })
                })
            }
        }.toString()

    private fun serializeActiveSelection(selection: ActiveModelSelection): String = JSONObject()
        .put("connectionId", selection.connectionId)
        .put("modelId", selection.modelId)
        .toString()

    private fun parseActiveSelection(json: String?): ActiveModelSelection? = runCatching {
        if (json.isNullOrBlank()) return@runCatching null
        JSONObject(json).let { item ->
            val connectionId = item.optString("connectionId")
            val modelId = item.optString("modelId")
            if (connectionId.isBlank() || modelId.isBlank()) null
            else ActiveModelSelection(connectionId, modelId)
        }
    }.getOrNull()

    private fun legacyActiveSelection(
        connections: List<ProviderConnection>,
        connectionId: String,
        modelId: String
    ): ActiveModelSelection? {
        if (connectionId.isBlank() || modelId.isBlank()) return null
        return connections.firstOrNull { it.id == connectionId }
            ?.takeIf { connection -> connection.visibleModels.any { it.id == modelId && it.enabled } }
            ?.let { ActiveModelSelection(it.id, modelId) }
    }

    private fun inferLegacyProviderId(providerType: String, baseUrl: String, modelId: String): String {
        val value = "${providerType.lowercase()} ${baseUrl.lowercase()} ${modelId.lowercase()}"
        return when {
            value.contains("anthropic") || value.contains("claude") -> "anthropic"
            value.contains("gemini") || value.contains("google") -> "google_gemini_api"
            value.contains("openrouter") -> "openrouter"
            value.contains("groq") -> "groq"
            value.contains("deepseek") -> "deepseek"
            value.contains("x.ai") || value.contains("grok") -> "xai"
            value.contains("github") -> "github_models"
            value.contains("vercel") -> "vercel_ai_gateway"
            baseUrl.isNotBlank() -> "custom_openai_compatible"
            else -> "openai"
        }
    }
}

data class AiSettings(
    val theme: String = "system",
    val connections: List<ProviderConnection> = emptyList(),
    val activeSelection: ActiveModelSelection? = null,
    val mcpConfigJson: String = "",
    val lastWorkspacePath: String? = null,
    val onboardingCompleted: Boolean = false,
    /** Semantic recall embedding endpoint (user-configured; API key stored encrypted). */
    val memoryEmbedding: MemoryEmbeddingConfig = MemoryEmbeddingConfig()
)
