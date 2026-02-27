package com.amaya.intelligence.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single agent / API profile the user has configured.
 * name    – display label (e.g. "OpenRouter Free", "My GPT-4o")
 * baseUrl – API base URL  (e.g. "https://openrouter.ai/api/v1")
 * modelId – model id       (e.g. "openai/gpt-4o-mini")
 * API key is stored encrypted separately, keyed by [id].
 */
data class AgentConfig(
    val id:           String = java.util.UUID.randomUUID().toString(),
    val name:         String = "",
    val providerType: String = "CUSTOM",
    val baseUrl:      String = "",
    val modelId:      String = "",
    val enabled:      Boolean = true
)

private val Context.dataStore by preferencesDataStore(name = "ai_settings")

@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        private val KEY_ACTIVE_MODEL    = stringPreferencesKey("active_model")
        private val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val KEY_THEME           = stringPreferencesKey("theme")
        private val KEY_AGENT_CONFIGS   = stringPreferencesKey("agent_configs")
        private val KEY_ACTIVE_AGENT_ID = stringPreferencesKey("active_agent_id")
        private val KEY_MCP_CONFIG_JSON = stringPreferencesKey("mcp_config_json")

        private const val ENC_OPENAI_KEY        = "openai_api_key"
        private const val ENC_ANTHROPIC_KEY     = "anthropic_api_key"
        private const val ENC_GEMINI_KEY        = "gemini_api_key"
        private const val ENC_AGENT_KEY_PREFIX  = "agent_key_"

        const val MCP_FIXED_PATH = "/storage/emulated/0/Amaya/mcp.json"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "amaya_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getSettings(): AiSettings = runBlocking { settingsFlow.first() }

    val settingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        val configs = parseAgentConfigs(prefs[KEY_AGENT_CONFIGS] ?: "[]")
        AiSettings(
            openaiApiKey    = encryptedPrefs.getString(ENC_OPENAI_KEY, "") ?: "",
            anthropicApiKey = encryptedPrefs.getString(ENC_ANTHROPIC_KEY, "") ?: "",
            geminiApiKey    = encryptedPrefs.getString(ENC_GEMINI_KEY, "") ?: "",
            openaiBaseUrl   = prefs[KEY_OPENAI_BASE_URL] ?: "",
            activeProvider  = prefs[KEY_ACTIVE_PROVIDER] ?: ProviderType.OPENAI.name,
            activeModel     = prefs[KEY_ACTIVE_MODEL] ?: "",
            theme           = prefs[KEY_THEME] ?: "system",
            agentConfigs    = configs,
            activeAgentId   = prefs[KEY_ACTIVE_AGENT_ID] ?: "",
            mcpConfigJson   = prefs[KEY_MCP_CONFIG_JSON] ?: ""
        )
    }

    /** Retrieve encrypted API key for a specific agent config ID */
    fun getAgentApiKey(agentId: String): String =
        encryptedPrefs.getString("$ENC_AGENT_KEY_PREFIX$agentId", "") ?: ""

    // ── Write ────────────────────────────────────────────────────────

    fun setApiKey(provider: ProviderType, apiKey: String) {
        val key = when (provider) {
            ProviderType.OPENAI    -> ENC_OPENAI_KEY
            ProviderType.ANTHROPIC -> ENC_ANTHROPIC_KEY
            ProviderType.GEMINI    -> ENC_GEMINI_KEY
        }
        encryptedPrefs.edit().putString(key, apiKey).apply()
    }

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

    suspend fun setOpenAiSettings(apiKey: String, baseUrl: String = "") {
        setApiKey(ProviderType.OPENAI, apiKey)
        context.dataStore.edit { prefs -> prefs[KEY_OPENAI_BASE_URL] = baseUrl }
    }

    suspend fun setAnthropicApiKey(apiKey: String) = setApiKey(ProviderType.ANTHROPIC, apiKey)
    suspend fun setGeminiApiKey(apiKey: String)    = setApiKey(ProviderType.GEMINI, apiKey)

    suspend fun setActiveProvider(provider: ProviderType, model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROVIDER] = provider.name
            prefs[KEY_ACTIVE_MODEL]    = model
        }
    }

    suspend fun setActiveModel(model: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ACTIVE_MODEL] = model }
    }

    suspend fun setBaseUrl(baseUrl: String) {
        context.dataStore.edit { prefs -> prefs[KEY_OPENAI_BASE_URL] = baseUrl }
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
                    enabled      = obj.optBoolean("enabled", true)
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
                })
            }
        }.toString()
}

data class AiSettings(
    val openaiApiKey:    String          = "",
    val anthropicApiKey: String          = "",
    val geminiApiKey:    String          = "",
    val openaiBaseUrl:   String          = "",
    val activeProvider:  String          = ProviderType.OPENAI.name,
    val activeModel:     String          = "",
    val theme:           String          = "system",
    val agentConfigs:    List<AgentConfig> = emptyList(),
    val activeAgentId:   String          = "",
    val mcpConfigJson:   String          = ""
)

enum class ProviderType { ANTHROPIC, OPENAI, GEMINI }
