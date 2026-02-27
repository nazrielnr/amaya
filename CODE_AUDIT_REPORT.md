# Code Audit Report: Amaya Intelligence Settings & DI

**Date:** Generated from workspace analysis  
**Focus Areas:** Hilt DI, Security (API key handling), Redundant dependencies, Dead code, Improper singleton usage

---

## Critical Issues

### [AiSettings.kt] [75] [SEVERITY: HIGH] [SECURITY + THREADING] — API Keys Exposed in Data Class + runBlocking on Main Thread

**Issue:**
The `AiSettings` data class (line 226-238) includes unencrypted API key fields (`openaiApiKey`, `anthropicApiKey`, `geminiApiKey`), and they are exposed through `settingsFlow` which is collected on the UI thread via `collectAsState()`. Additionally, `getSettings()` uses `runBlocking {}` on line 75, which blocks the Main thread when called from UI code.

**Current Code:**
```kotlin
// Line 74-91: settingsFlow exposes raw API keys
val settingsFlow: Flow<AiSettings> = context.dataStore.data.map { prefs ->
    val configs = parseAgentConfigs(prefs[KEY_AGENT_CONFIGS] ?: "[]")
    AiSettings(
        openaiApiKey    = encryptedPrefs.getString(ENC_OPENAI_KEY, "") ?: "",
        anthropicApiKey = encryptedPrefs.getString(ENC_ANTHROPIC_KEY, "") ?: "",
        geminiApiKey    = encryptedPrefs.getString(ENC_GEMINI_KEY, "") ?: "",
        // ... other fields
    )
}

// Line 75: runBlocking on Main thread
fun getSettings(): AiSettings = runBlocking { settingsFlow.first() }

// Line 226-238: Data class with raw API keys
data class AiSettings(
    val openaiApiKey: String = "",
    val anthropicApiKey: String = "",
    val geminiApiKey: String = "",
    // ...
)
```

**Why It's a Problem:**
1. **Security:** API keys should never be in data classes that might be logged, serialized, or accidentally exposed in error messages.
2. **Threading:** `runBlocking{}` on Main thread (called from AiRepository.getSettings() → AiModule providers → UI) causes jank and potential ANR.
3. **Exposure:** `settingsFlow.collectAsState()` is called in every settings screen (AgentsScreen, SettingsScreen, PersonaActivity), keeping decrypted keys in UI memory.

**Recommended Fix:**
1. Remove API key fields from `AiSettings` data class entirely.
2. Create a separate `AiSettingsPublic` data class with only non-sensitive fields (theme, accentColor, agentConfigs metadata without keys, etc.).
3. Create getter methods for API keys that return keys only when needed (not exposed in a flow).
4. Replace `runBlocking` calls with proper suspend functions or Flow operations.
5. In AI providers, fetch keys lazily via lambdas (already done in AiModule, but the design should be reinforced).

**Example Fix:**
```kotlin
// Remove from AiSettings
data class AiSettings(
    val theme: String = "system",
    val accentColor: String = "Purple",
    val agentConfigs: List<AgentConfig> = emptyList(),
    // NO API KEYS
)

// New method
suspend fun getApiKey(provider: ProviderType): String {
    return withContext(Dispatchers.IO) {
        val key = when (provider) {
            ProviderType.OPENAI -> ENC_OPENAI_KEY
            ProviderType.ANTHROPIC -> ENC_ANTHROPIC_KEY
            ProviderType.GEMINI -> ENC_GEMINI_KEY
        }
        encryptedPrefs.getString(key, "") ?: ""
    }
}

// In AiModule, providers already use lambdas (good!)
fun provideAnthropicProvider(
    httpClient: OkHttpClient,
    moshi: Moshi,
    settingsManager: AiSettingsManager
): AnthropicProvider {
    return AnthropicProvider(
        httpClient = httpClient,
        moshi = moshi,
        settingsProvider = { settingsManager.getSettings() }  // ← Should be a suspend lambda
    )
}
```

---

### [AiSettings.kt] [74-75] [SEVERITY: HIGH] [DESIGN] — Blocking runBlocking() Call in Repository + DI Init

**Issue:**
Line 75 uses `runBlocking { settingsFlow.first() }` which blocks the Main thread. This function is called from:
- `AiRepository.getActiveProvider()` (line 96)
- `AiRepository.chat()` (line 129)
- AI providers in AiModule (line 29, 43, 57): `settingsManager.getSettings()` in lambdas
- Multiple tool executors

**Current Code:**
```kotlin
fun getSettings(): AiSettings = runBlocking { settingsFlow.first() }
```

Called from AiRepository (line 96, 129):
```kotlin
fun getActiveProvider(): AiProvider {
    val settings = settingsManager.getSettings()  // ← runBlocking!
    return when (ProviderType.valueOf(settings.activeProvider)) { ... }
}

fun chat(...): Flow<AgentEvent> = flow {
    val settings = settingsManager.getSettings()  // ← runBlocking!
    // ...
}
```

**Why It's a Problem:**
- `getSettings()` is called from lambdas in AiModule provider functions, which may be evaluated on the Main thread during injection.
- Causes ANR (Application Not Responding) when called from UI thread.
- Makes the code non-cancellable and non-suspendable.

**Recommended Fix:**
```kotlin
// Remove blocking function entirely
// fun getSettings(): AiSettings = runBlocking { settingsFlow.first() }

// For cases where you need the latest settings in a suspend context:
suspend fun getSettingsAsync(): AiSettings {
    return withContext(Dispatchers.IO) {
        settingsFlow.first()
    }
}

// In AiRepository, use suspend properly:
fun chat(...): Flow<AgentEvent> = flow {
    val settings = getSettingsAsync()  // ← Now non-blocking
    // ...
}
```

---

### [AgentsScreen.kt] [180] [SEVERITY: MEDIUM] [SECURITY] — API Key Retrieved on Main Thread in Compose Remember

**Issue:**
Line 178-180 retrieves the API key synchronously inside a Compose `remember` block:
```kotlin
val currentApiKey = remember(currentConfig.id) {
    if (editingIsNew) "" else aiSettingsManager.getAgentApiKey(currentConfig.id)
}
```

This calls `getAgentApiKey()` which accesses `encryptedPrefs` (blocking I/O) on the Main thread during recomposition.

**Why It's a Problem:**
1. Compose `remember` blocks are not suspendable — they must be synchronous.
2. Accessing SharedPreferences on the Main thread can cause jank.
3. If the key is not available immediately, the UI freezes.

**Recommended Fix:**
```kotlin
// Use a side effect to load the key asynchronously
val currentApiKey = remember(currentConfig.id) { mutableStateOf("") }

LaunchedEffect(currentConfig.id) {
    if (!editingIsNew) {
        withContext(Dispatchers.IO) {
            val key = aiSettingsManager.getAgentApiKey(currentConfig.id)
            currentApiKey.value = key
        }
    }
}

// Then use currentApiKey.value in the UI
OutlinedTextField(
    value = currentApiKey.value,
    onValueChange = { currentApiKey.value = it },
    // ...
)
```

---

### [PersonaScreen.kt] [29, 120, 174-177] [SEVERITY: HIGH] [THREADING] — runBlocking() in Composable Initialization

**Issue:**
PersonaRepository uses `runBlocking {}` in synchronous getter functions that are called during Compose initialization:
```kotlin
// PersonaRepository.kt
fun getMode(): PersonaMode = runBlocking {
    val raw = context.personaStore.data.map { it[KEY_MODE] ?: "SIMPLE" }.first()
    try { PersonaMode.valueOf(raw) } catch (_: Exception) { PersonaMode.SIMPLE }
}

fun getSimplePersona(): SimplePersona = runBlocking {
    context.personaStore.data.map { prefs ->
        SimplePersona(/* ... */)
    }.first()
}

// PersonaScreen.kt line 29
var mode by remember { mutableStateOf(personaRepository.getMode()) }
```

Called from Compose, which runs on Main thread.

**Why It's a Problem:**
1. Main thread is blocked waiting for DataStore I/O.
2. Causes UI jank, especially on slower devices.
3. If DataStore is slow, the entire screen initialization stalls.

**Recommended Fix:**
```kotlin
// In PersonaRepository, convert to suspend functions
suspend fun getMode(): PersonaMode {
    val raw = context.personaStore.data.map { it[KEY_MODE] ?: "SIMPLE" }.first()
    return try { PersonaMode.valueOf(raw) } catch (_: Exception) { PersonaMode.SIMPLE }
}

suspend fun getSimplePersona(): SimplePersona {
    return context.personaStore.data.map { prefs ->
        SimplePersona(/* ... */)
    }.first()
}

// In PersonaScreen, use LaunchedEffect
var mode by remember { mutableStateOf(PersonaMode.SIMPLE) }

LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
        mode = personaRepository.getMode()
    }
}
```

---

## Medium Severity Issues

### [DatabaseModule.kt] [32-54] [SEVERITY: MEDIUM] [DI DESIGN] — DAO Providers Missing @Singleton Annotation

**Issue:**
DAO provider functions (ProjectDao, FileDao, FileMetadataDao, ConversationDao, CronJobDao) are not annotated with `@Singleton`:

```kotlin
// Line 32-34: No @Singleton on DAO providers
@Provides
fun provideProjectDao(database: AppDatabase): ProjectDao {
    return database.projectDao()
}

@Provides
fun provideFileDao(database: AppDatabase): FileDao {
    return database.fileDao()
}

// ... and so on
```

Since they depend on `AppDatabase` (which is `@Singleton`), they should be singletons too. Without the annotation, Hilt creates a new instance for each injection, which is wasteful.

**Why It's a Problem:**
1. **Memory waste:** Multiple DAO instances created instead of reusing the singleton from the database.
2. **Inconsistency:** Some DAOs may have different states if not properly singletons.
3. **Performance:** Extra object allocation on every injection.

**Recommended Fix:**
```kotlin
@Provides
@Singleton
fun provideProjectDao(database: AppDatabase): ProjectDao {
    return database.projectDao()
}

@Provides
@Singleton
fun provideFileDao(database: AppDatabase): FileDao {
    return database.fileDao()
}

// ... apply to all DAO providers
```

---

### [AiModule.kt] [20-59] [SEVERITY: MEDIUM] [DI DESIGN] — Redundant Provider Functions

**Issue:**
All three AI provider functions follow the identical pattern:
```kotlin
@Provides
@Singleton
fun provideAnthropicProvider(
    httpClient: OkHttpClient,
    moshi: Moshi,
    settingsManager: AiSettingsManager
): AnthropicProvider {
    return AnthropicProvider(
        httpClient = httpClient,
        moshi = moshi,
        settingsProvider = { settingsManager.getSettings() }
    )
}

@Provides
@Singleton
fun provideOpenAiProvider(
    httpClient: OkHttpClient,
    moshi: Moshi,
    settingsManager: AiSettingsManager
): OpenAiProvider {
    return OpenAiProvider(/* same deps */)
}

@Provides
@Singleton
fun provideGeminiProvider(
    httpClient: OkHttpClient,
    moshi: Moshi,
    settingsManager: AiSettingsManager
): GeminiProvider {
    return GeminiProvider(/* same deps */)
}
```

**Why It's a Problem:**
1. **Code duplication:** The three providers follow the same initialization pattern.
2. **Maintenance burden:** Any change to shared deps must be made in three places.
3. **Low value:** The providers are used by AiRepository, which only needs one at a time.

**Recommended Fix:**
Keep the providers as-is (they are correctly singleton and properly injected into AiRepository). This is acceptable DI design. However, if you want to reduce boilerplate, consider using a factory pattern. But this is **optional** — the current code works fine.

---

### [SettingsScreen.kt] [177-180] [SEVERITY: MEDIUM] [EFFICIENCY] — Redundant McpConfig Parsing

**Issue:**
Lines 177-180 parse the MCP config JSON every time the screen recomposes:
```kotlin
val mcpConfig = remember(settings.mcpConfigJson) {
    com.amaya.intelligence.data.remote.api.McpConfig.fromJson(settings.mcpConfigJson)
}
val activeCount = mcpConfig.servers.count { it.enabled }
val totalCount = mcpConfig.servers.size
```

This is called every time `settings` changes. If `mcpConfigJson` hasn't changed, the parsing is redundant.

**Why It's a Problem:**
1. **Inefficiency:** JSON parsing happens on every `settings` Flow update.
2. **Memory:** Creates a new McpConfig object even if unchanged.
3. **Minor:** The performance impact is negligible, but it's not optimal.

**Recommended Fix:**
The `remember` with `mcpConfigJson` as key already prevents re-parsing when unchanged. **This is already correct.** No change needed. The code is fine.

---

## Low Severity Issues

### [PersonaScreen.kt] [321, 338] [SEVERITY: LOW] [THREADING] — Blocking I/O in Composable Remember

**Issue:**
Lines 321 and 338 use `remember` for file operations that should be asynchronous:

```kotlin
// Line 321: logs are read on Main thread
val logs = remember { personaRepository.listDailyLogs() }

// Line 338: log content is read on Main thread for every item
val logContent = remember(logName) { personaRepository.readFile("memory/$logName") }
```

These call I/O methods synchronously on the Main thread inside `remember`.

**Why It's a Problem:**
1. **Threading:** `readFile()` is a blocking I/O call on Main thread.
2. **Inefficiency:** The I/O is done on every recomposition where the condition doesn't hold.
3. **Jank:** Blocks UI rendering while files are being read.

**Recommended Fix:**
```kotlin
// Use LaunchedEffect to load logs asynchronously
val logs = remember { mutableStateOf<List<String>>(emptyList()) }

LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
        logs.value = personaRepository.listDailyLogs()
    }
}

// For each log entry, load content asynchronously
logs.value.take(5).forEach { logName ->
    val logContent = remember(logName) { mutableStateOf("") }
    
    LaunchedEffect(logName) {
        withContext(Dispatchers.IO) {
            logContent.value = personaRepository.readFile("memory/$logName")
        }
    }
    
    Surface(/* ... */) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(logName, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                logContent.value.take(500),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

### [AgentsScreen.kt] [122-156] [SEVERITY: LOW] [CODE STYLE] — Repetitive Toggle Handler

**Issue:**
Lines 122-130 and 149-156 repeat the same toggle handler code in both enabled and disabled agent lists:

```kotlin
onToggleEnabled = { enabled ->
    scope.launch {
        aiSettingsManager.saveAgentConfig(
            config.copy(enabled = enabled),
            aiSettingsManager.getAgentApiKey(config.id)
        )
    }
}
```

**Recommended Fix:**
Extract into a helper lambda at the screen level:

```kotlin
val onToggleEnabled: (AgentConfig, Boolean) -> Unit = { config, enabled ->
    scope.launch {
        aiSettingsManager.saveAgentConfig(
            config.copy(enabled = enabled),
            aiSettingsManager.getAgentApiKey(config.id)
        )
    }
}

// Use in both items()
items(enabledAgents, key = { "enabled_${it.id}" }) { config ->
    AgentCard(
        config = config,
        onClick = { editingConfig = config; editingIsNew = false },
        onToggleEnabled = { enabled -> onToggleEnabled(config, enabled) }
    )
}

items(disabledAgents, key = { "disabled_${it.id}" }) { config ->
    AgentCard(
        config = config,
        onClick = { editingConfig = config; editingIsNew = false },
        onToggleEnabled = { enabled -> onToggleEnabled(config, enabled) }
    )
}
```

---

### [Activity Files] [25-32] [SEVERITY: LOW] [CODE STYLE] — Repeated Theme Logic

**Issue:**
All Activity files (AgentsActivity, SettingsActivity, PersonaActivity) repeat the same theme resolution code:
```kotlin
val settings by aiSettingsManager.settingsFlow.collectAsState(
    initial = com.amaya.intelligence.data.remote.api.AiSettings()
)
val isDarkTheme = when (settings.theme) {
    "light" -> false
    "dark" -> true
    else -> androidx.compose.foundation.isSystemInDarkTheme()
}
AmayaTheme(darkTheme = isDarkTheme, accentColor = settings.accentColor) {
    // ...
}
```

**Recommended Fix:**
Extract into a utility composable:

```kotlin
// In a ThemeUtils.kt file
@Composable
fun WithAmayaTheme(
    aiSettingsManager: AiSettingsManager,
    content: @Composable () -> Unit
) {
    val settings by aiSettingsManager.settingsFlow.collectAsState(
        initial = com.amaya.intelligence.data.remote.api.AiSettings()
    )
    val isDarkTheme = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    AmayaTheme(darkTheme = isDarkTheme, accentColor = settings.accentColor) {
        content()
    }
}

// In SettingsActivity.kt
setContent {
    WithAmayaTheme(aiSettingsManager) {
        SettingsScreen(
            onNavigateBack = { finish() },
            // ...
        )
    }
}
```

---

### [AiSettings.kt] [60] [SEVERITY: LOW] [CONFIG] — Hardcoded MCP Path Not Device-Compatible

**Issue:**
Line 60 hardcodes the MCP config path:
```kotlin
const val MCP_FIXED_PATH = "/storage/emulated/0/Amaya/mcp.json"
```

This path is Android 11+ only and will fail or cause permission issues on older devices and may not work if storage layout differs.

**Recommended Fix:**
```kotlin
// Use context-aware path resolution
fun getMcpConfigPath(context: Context): String {
    val amayaDir = File(context.getExternalFilesDir(null), "Amaya").apply { mkdirs() }
    return File(amayaDir, "mcp.json").absolutePath
}

// Or for app-specific external files
fun getMcpConfigPathCompat(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        File(context.getExternalFilesDir(null), "mcp.json").absolutePath
    } else {
        File(context.filesDir, "mcp.json").absolutePath
    }
}
```

---

## Summary Table

| File | Line | Severity | Type | Issue |
|------|------|----------|------|-------|
| AiSettings.kt | 75 | HIGH | THREADING | `runBlocking()` blocks Main thread |
| AiSettings.kt | 226-238 | HIGH | SECURITY | API keys exposed in data class |
| AiSettings.kt | 74-75 | HIGH | DESIGN | Blocking call in DI + Repository init |
| PersonaScreen.kt | 29, 174-177 | HIGH | THREADING | `runBlocking()` in Composable init |
| AgentsScreen.kt | 180 | MEDIUM | SECURITY | API key retrieved on Main thread |
| DatabaseModule.kt | 32-54 | MEDIUM | DI DESIGN | Missing `@Singleton` on DAO providers |
| AiModule.kt | 20-59 | MEDIUM | DI DESIGN | Code duplication (acceptable design) |
| PersonaScreen.kt | 321, 338 | LOW | THREADING | Blocking I/O in Composable |
| AgentsScreen.kt | 122-156 | LOW | CODE STYLE | Repetitive toggle handler |
| Activity files | 25-32 | LOW | CODE STYLE | Repeated theme logic |
| AiSettings.kt | 60 | LOW | CONFIG | Hardcoded path not device-compatible |

---

## Recommendations (Priority Order)

1. **[CRITICAL]** Remove API keys from `AiSettings` data class and provide through separate getters/methods.
2. **[CRITICAL]** Replace all `runBlocking()` calls with proper suspend functions or Flow operations.
3. **[HIGH]** Load PersonaRepository data asynchronously via `LaunchedEffect` instead of in `remember`.
4. **[HIGH]** Load API keys asynchronously in AgentsScreen via `LaunchedEffect` instead of `remember`.
5. **[MEDIUM]** Add `@Singleton` annotation to all DAO providers in DatabaseModule.
6. **[LOW]** Extract repeated theme logic into a utility composable to reduce code duplication.
7. **[LOW]** Replace hardcoded MCP path with context-aware path resolution.
8. **[LOW]** Extract repetitive toggle handler in AgentsScreen.

