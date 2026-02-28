package com.amaya.intelligence.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// FIX 1.3: Removed unused import androidx.security.crypto.MasterKeys (replaced by MasterKey)

/**
 * A single agent / API profile the user has configured.
 * name    – display label (e.g. "OpenRouter Free", "My GPT-4o")
 * baseUrl – API base URL  (e.g. "https://openrouter.ai/api/v1")
 * modelId – model id       (e.g. "openai/gpt-4o-mini")
 * API key is stored encrypted separately, keyed by [id].
 */
data class AgentConfig(
    val id:           String  = java.util.UUID.randomUUID().toString(),
    val name:         String  = "",
    val providerType: String  = "CUSTOM",
    val baseUrl:      String  = "",
    val modelId:      String  = "",
    val enabled:      Boolean = true,
    val maxTokens:    Int     = 8192
)

private val Context.dataStore by preferencesDataStore(name = "ai_settings")

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // KEY_ACTIVE_PROVIDER: legacy key kept in DataStore for backwards compat (not read by new code)
        private val KEY_ACTIVE_MODEL    = stringPreferencesKey("active_model")
        private val KEY_THEME           = stringPreferencesKey("theme")
        private val KEY_AGENT_CONFIGS   = stringPreferencesKey("agent_configs")
        private val KEY_ACTIVE_AGENT_ID = stringPreferencesKey("active_agent_id")
        private val KEY_MCP_CONFIG_JSON = stringPreferencesKey("mcp_config_json")

        // Per-agent encrypted API key storage: key = "agent_key_" + agentId
        private const val ENC_AGENT_KEY_PREFIX  = "agent_key_"

        const val MCP_FIXED_PATH = "/storage/emulated/0/Amaya/mcp.json"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        // FIX 5.8: Use non-deprecated MasterKey.Builder API (MasterKeys was deprecated in security-crypto 1.1.0-alpha)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "amaya_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // FIX 3.1: Cache the last emitted settings so getSettings() rarely needs runBlocking.
    // IMPORTANT: settingsFlow must be declared BEFORE init{} so it is non-null when the
    // coroutine starts. Kotlin initializes properties top-to-bottom, so declaration order matters.
    val settingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val configs = parseAgentConfigs(prefs[KEY_AGENT_CONFIGS] ?: "[]")
        AiSettings(
            activeModel     = prefs[KEY_ACTIVE_MODEL] ?: "",
            theme           = prefs[KEY_THEME] ?: "system",
            agentConfigs    = configs,
            activeAgentId   = prefs[KEY_ACTIVE_AGENT_ID] ?: "",
            mcpConfigJson   = prefs[KEY_MCP_CONFIG_JSON] ?: ""
        )
    }

    // FIX 3.1: Cache declared AFTER settingsFlow so it is non-null when init{} coroutine starts.
    @Volatile private var cachedSettings: AiSettings? = null

    init {
        // Warm the cache on a background thread — settingsFlow is guaranteed non-null here
        CoroutineScope(Dispatchers.IO).launch {
            settingsFlow.collect { cachedSettings = it }
        }
    }

    fun getSettings(): AiSettings =
        cachedSettings ?: runBlocking { settingsFlow.first() }.also { cachedSettings = it }

    /** Retrieve encrypted API key for a specific agent config ID */
    fun getAgentApiKey(agentId: String): String =
        encryptedPrefs.getString("$ENC_AGENT_KEY_PREFIX$agentId", "") ?: ""

    // ── Write ────────────────────────────────────────────────────────

    /** Add or update an agent config and store its API key encrypted */
    suspend fun saveAgentConfig(config: AgentConfig, apiKey: String) {
        encryptedPrefs.edit().putString("$ENC_AGENT_KEY_PREFIX${config.id}", apiKey).apply()
        context.dataStore.edit { prefs ->
            val list = parseAgentConfigs(prefs[KEY_AGENT_CONFIGS] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.id == config.id }
            if (idx >= 0) list[idx] = config else list.add(config)
            prefs[KEY_AGENT_CONFIGS] = serializeAgentConfigs(list)
        }
    }

    /** Delete an agent config */
    suspend fun deleteAgentConfig(agentId: String) {
        encryptedPrefs.edit().remove("$ENC_AGENT_KEY_PREFIX$agentId").apply()
        context.dataStore.edit { prefs ->
            val list = parseAgentConfigs(prefs[KEY_AGENT_CONFIGS] ?: "[]").toMutableList()
            list.removeAll { it.id == agentId }
            prefs[KEY_AGENT_CONFIGS] = serializeAgentConfigs(list)
            if ((prefs[KEY_ACTIVE_AGENT_ID] ?: "") == agentId) {
                prefs[KEY_ACTIVE_AGENT_ID] = list.firstOrNull()?.id ?: ""
                prefs[KEY_ACTIVE_MODEL]    = list.firstOrNull()?.modelId ?: ""
            }
        }
    }

    /** Select which agent config is active; also updates the active model */
    suspend fun setActiveAgent(agentId: String, modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_AGENT_ID] = agentId
            prefs[KEY_ACTIVE_MODEL]    = modelId
        }
    }

    // FIX 1.1: Removed setAnthropicApiKey() and setGeminiApiKey() — dead wrappers around setApiKey().
    // FIX 1.2: Removed setActiveProvider() and setBaseUrl() — dead code from pre-agent-config era.
    //          Provider is now resolved from AgentConfig.providerType, not from activeProvider DataStore key.

    suspend fun setActiveModel(model: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ACTIVE_MODEL] = model }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = theme }
    }

    suspend fun setMcpConfigJson(json: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MCP_CONFIG_JSON] = json }
        writeMcpConfigToFixedPath(json)
    }

    suspend fun loadMcpConfigFromFixedPath(): String? {
        return withContext(Dispatchers.IO) {
            val file = File(MCP_FIXED_PATH)
            if (!file.exists()) return@withContext null
            return@withContext runCatching { file.readText() }.getOrNull()
        }
    }

    suspend fun writeMcpConfigToFixedPath(json: String) {
        withContext(Dispatchers.IO) {
            val file = File(MCP_FIXED_PATH)
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    // ── JSON helpers ─────────────────────────────────────────────────

    private fun parseAgentConfigs(json: String): List<AgentConfig> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { obj ->
                AgentConfig(
                    id           = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name         = obj.optString("name", ""),
                    providerType = obj.optString("providerType", "CUSTOM"),
                    baseUrl      = obj.optString("baseUrl", ""),
                    modelId      = obj.optString("modelId", ""),
                    enabled      = obj.optBoolean("enabled", true),
                    maxTokens    = obj.optInt("maxTokens", 8192)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun serializeAgentConfigs(configs: List<AgentConfig>): String =
        JSONArray().also { arr ->
            configs.forEach { c ->
                arr.put(JSONObject().apply {
                    put("id",           c.id)
                    put("name",         c.name)
                    put("providerType", c.providerType)
                    put("baseUrl",      c.baseUrl)
                    put("modelId",      c.modelId)
                    put("enabled",      c.enabled)
                    put("maxTokens",    c.maxTokens)
                })
            }
        }.toString()
}

data class AiSettings(
    // activeModel: fallback model ID if no agent config found (rarely used)
    val activeModel:     String          = "",
    val theme:           String          = "system",
    val agentConfigs:    List<AgentConfig> = emptyList(),
    val activeAgentId:   String          = "",
    val mcpConfigJson:   String          = ""
)

enum class ProviderType { ANTHROPIC, OPENAI, GEMINI }
