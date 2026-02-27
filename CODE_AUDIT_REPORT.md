# Kotlin Code Audit Report

## Summary
Audit of 4 Kotlin files for dead code, redundant imports, unused variables, and simplification opportunities.

---

## File 1: AiRepository.kt
**Path:** `app/src/main/java/com/amaya/intelligence/data/repository/AiRepository.kt`

### Issues Found

#### 1. **Unused Variable: `textBuffer`** ⚠️ MEDIUM
- **Location:** Line 166
- **Issue:** `var textBuffer = StringBuilder()` is created but its content is never used after appending text deltas.
- **Context:**
  ```kotlin
  var textBuffer = StringBuilder()  // Line 166 - created
  val toolCalls = mutableListOf<ToolCallMessage>()
  var hasToolCalls = false
  
  provider.chat(request).collect { response ->
      when (response) {
          is ChatResponse.TextDelta -> {
              textBuffer.append(response.text)  // Line 173 - appended
              emit(AgentEvent.TextDelta(response.text))  // Line 174 - used here instead
          }
          ...
  }
  
  // Line 209 - used here
  content = textBuffer.toString().takeIf { it.isNotEmpty() },
  ```
- **Recommendation:** The `textBuffer` appears to only be used for the final assistant message content. Consider whether this intermediate accumulation is necessary, or if you should collect text deltas differently.

#### 2. **Unused Import: `kotlinx.coroutines.Dispatchers`** ⚠️ LOW
- **Location:** Line 14
- **Issue:** `import kotlinx.coroutines.Dispatchers` is imported but never used in the file.
- **Code:** `Dispatchers.IO` is not referenced anywhere in `AiRepository.kt`
- **Recommendation:** Remove line 14

#### 3. **Unused Import: `kotlinx.coroutines.launch`** ⚠️ LOW
- **Location:** Line 16
- **Issue:** `import kotlinx.coroutines.launch` is imported but `launch` is only used in the `init` block (line 50) which is fine. However, checking if there are better patterns for repository initialization.
- **Note:** This import IS used, so this is a false alarm. Keep it.

#### 4. **Potential Null Safety Issue in `getActiveProvider()`** ⚠️ MEDIUM
- **Location:** Lines 96-103
- **Issue:** `getActiveProvider()` calls `settingsManager.getSettings()` synchronously and doesn't handle potential exceptions from `ProviderType.valueOf()`.
- **Code:**
  ```kotlin
  fun getActiveProvider(): AiProvider {
      val settings = settingsManager.getSettings()
      return when (ProviderType.valueOf(settings.activeProvider)) {
          // ...
      }
  }
  ```
- **Problem:** `ProviderType.valueOf()` can throw `IllegalArgumentException` if `activeProvider` contains invalid enum value.
- **Recommendation:** Add try-catch or default handling:
  ```kotlin
  fun getActiveProvider(): AiProvider {
      val settings = settingsManager.getSettings()
      return when (ProviderType.values().find { it.name == settings.activeProvider } 
          ?: ProviderType.OPENAI) {
          // ...
      }
  }
  ```

#### 5. **Unused Variable: `iterations`** ⚠️ MEDIUM
- **Location:** Line 147
- **Issue:** `var iterations = 0` is declared and incremented (line 151) but only used in the loop condition and the final check (line 253). The value is not particularly useful after the loop ends since you're just checking if it reached max.
- **Recommendation:** While not strictly "unused," you could simplify by removing the check and just using `continueLoop` to track state.

#### 6. **Redundant String Format in `buildSystemPrompt()`** ⚠️ LOW
- **Location:** Lines 269-270
- **Issue:** Two separate `DateTimeFormatter` objects are created for the same datetime with different patterns.
- **Code:**
  ```kotlin
  val now = java.time.LocalDateTime.now()
  val dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
  val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
  ```
- **Recommendation:** Consolidate:
  ```kotlin
  val now = java.time.LocalDateTime.now()
  val dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
  val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
  ```
  Or better, create a single formatter and format once.

#### 7. **Dead Code: Empty `personaFragment` Handling** ⚠️ LOW
- **Location:** Lines 287-306 vs 308-339
- **Issue:** The `personaFragment` is retrieved (line 284) but the logic duplicates large prompt strings in both branches of the conditional.
- **Code:** Two nearly identical blocks with only `personaFragment` insertion point differing.
- **Recommendation:** Extract common prompt structure and conditionally insert persona fragment.

---

## File 2: AiSettings.kt
**Path:** `app/src/main/java/com/amaya/intelligence/data/remote/api/AiSettings.kt`

### Issues Found

#### 1. **Blocking Call in Non-Blocking Context: `getSettings()`** ⚠️ HIGH
- **Location:** Line 73
- **Issue:** `getSettings()` uses `runBlocking()` which blocks a thread, potentially on the main thread.
- **Code:**
  ```kotlin
  fun getSettings(): AiSettings = runBlocking { settingsFlow.first() }
  ```
- **Problem:** This function can block the UI thread if called from compose or main thread context.
- **Recommendation:** Either make this function suspend or provide a blocking variant only for non-UI contexts. Better approach: use `getSettingsBlocking()` pattern only in initialization code.

#### 2. **Redundant Null Checks in `settingsFlow`** ⚠️ MEDIUM
- **Location:** Lines 78-80
- **Issue:** Elvis operator followed by redundant null coalescing:
  ```kotlin
  openaiApiKey    = encryptedPrefs.getString(ENC_OPENAI_KEY, "") ?: "",
  anthropicApiKey = encryptedPrefs.getString(ENC_ANTHROPIC_KEY, "") ?: "",
  geminiApiKey    = encryptedPrefs.getString(ENC_GEMINI_KEY, "") ?: "",
  ```
- **Problem:** `getString()` with a default value `""` should never return null, so the `?:` is redundant.
- **Recommendation:** Simplify:
  ```kotlin
  openaiApiKey    = encryptedPrefs.getString(ENC_OPENAI_KEY, "") !!,
  anthropicApiKey = encryptedPrefs.getString(ENC_ANTHROPIC_KEY, "") !!,
  geminiApiKey    = encryptedPrefs.getString(ENC_GEMINI_KEY, "") !!,
  ```
  Or just remove the null coalescing since the default handles it.

#### 3. **Redundant Null Coalescing in `getAgentApiKey()`** ⚠️ MEDIUM
- **Location:** Line 94
- **Issue:** Same as above - `getString()` with default `""` shouldn't return null.
- **Code:**
  ```kotlin
  fun getAgentApiKey(agentId: String): String =
      encryptedPrefs.getString("$ENC_AGENT_KEY_PREFIX$agentId", "") ?: ""
  ```
- **Recommendation:** Simplify to:
  ```kotlin
  fun getAgentApiKey(agentId: String): String =
      encryptedPrefs.getString("$ENC_AGENT_KEY_PREFIX$agentId", "") !!
  ```

#### 4. **Unused Import: `java.io.File`** ⚠️ LOW
- **Location:** Line 18
- **Issue:** `import java.io.File` is used only in `loadMcpConfigFromFixedPath()` and `writeMcpConfigToFixedPath()`. The import is necessary and used, so this is NOT an issue.

#### 5. **Unused Variable: `masterKeyAlias`** ⚠️ LOW
- **Location:** Line 63
- **Issue:** `val masterKeyAlias = MasterKeys.getOrCreate(...)` is assigned but not clearly named for understanding.
- **Context:** It's used immediately in the next line, so this is fine, but it could be inlined.
- **Recommendation:** Inline it:
  ```kotlin
  EncryptedSharedPreferences.create(
      "amaya_secure_prefs",
      MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
      ...
  )
  ```

#### 6. **Unused Argument: `_` in Exception Handler** ⚠️ LOW
- **Location:** Line 207
- **Issue:** `catch (_: Exception)` in `parseAgentConfigs` - the underscore suggests intentional ignoring, which is fine for this JSON parsing error case. This is acceptable as-is.

#### 7. **Inefficient List Operation in `deleteAgentConfig()`** ⚠️ MEDIUM
- **Location:** Lines 122-128
- **Issue:** Two separate operations on the same list:
  ```kotlin
  val list = parseAgentConfigs(...).toMutableList()
  list.removeAll { it.id == agentId }  // Remove agent
  if ((prefs[KEY_ACTIVE_AGENT_ID] ?: "") == agentId) {
      prefs[KEY_ACTIVE_AGENT_ID] = list.firstOrNull()?.id ?: ""
      // ...
  }
  ```
- **Recommendation:** Could be more efficient:
  ```kotlin
  val list = parseAgentConfigs(...).toMutableList()
  list.removeAll { it.id == agentId }
  if (prefs[KEY_ACTIVE_AGENT_ID] == agentId) {
      prefs[KEY_ACTIVE_AGENT_ID] = list.firstOrNull()?.id ?: ""
      // ...
  }
  ```
  (Remove the redundant `?: ""` check since KEY_ACTIVE_AGENT_ID is already a string)

#### 8. **Unused Imports: Dispatchers (partially)** ⚠️ LOW
- **Location:** Line 13
- **Issue:** `import kotlinx.coroutines.Dispatchers` is imported but only `Dispatchers.IO` is used in lines 177 and 186.
- **Status:** The import IS used, not an issue.

---

## File 3: SettingsScreen.kt
**Path:** `app/src/main/java/com/amaya/intelligence/ui/settings/SettingsScreen.kt`

### Issues Found

#### 1. **Unused Snackbar: `snackbarHostState`** ⚠️ MEDIUM
- **Location:** Line 37-38, 44
- **Issue:** `snackbarHostState` is created and passed to Scaffold but never actually used:
  ```kotlin
  val snackbarHostState = remember { SnackbarHostState() }
  
  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      // ...
  )
  ```
- **Problem:** No `.showSnackbar()` calls anywhere in the composable.
- **Recommendation:** Remove lines 37-38 and the `snackbarHost` parameter from Scaffold if not needed.

#### 2. **Redundant Import: `androidx.compose.foundation.background`** ⚠️ LOW
- **Location:** Line 2
- **Issue:** `import androidx.compose.foundation.background` is imported but not explicitly used (it's in the wildcard `androidx.compose.foundation.layout.*` on line 7).
- **Analysis:** Actually, `background` is NOT in the `layout` package, it's in `foundation`, so this import IS necessary. Not an issue.

#### 3. **Unused Parameter: `snackbarHostState`** ⚠️ MEDIUM (same as issue #1)
- **Location:** Line 44
- **Status:** Related to issue #1 above.

#### 4. **Hardcoded Theme/Color Lists as Local Variables** ⚠️ LOW
- **Location:** Lines 113-114, 129-135
- **Issue:** Theme and color lists are redefined every time the composable recomposes:
  ```kotlin
  val themes = listOf("system", "light", "dark")
  val labels = listOf("System", "Light", "Dark")
  
  val accentColors = listOf(
      "Purple" to Color(0xFF6750A4),
      // ...
  )
  ```
- **Recommendation:** Move these to file-level constants:
  ```kotlin
  private val THEMES = listOf("system", "light", "dark")
  private val THEME_LABELS = listOf("System", "Light", "Dark")
  private val ACCENT_COLORS = listOf(
      "Purple" to Color(0xFF6750A4),
      // ...
  )
  ```

#### 5. **Unused Import: `androidx.compose.material.icons.filled.*`** ⚠️ LOW
- **Location:** Line 13
- **Issue:** Wildcard import `androidx.compose.material.icons.filled.*` might include unused icons. Check actual usage:
  - `Icons.Default.Folder` ✓ (line 70)
  - `Icons.Default.SmartToy` ✓ (line 80)
  - `Icons.Default.Person` ✓ (line 90)
  - `Icons.Default.Alarm` ✓ (line 100)
  - `Icons.Default.Info` ✓ (line 187)
  - `Icons.Default.ChevronRight` ✓ (line 277)
- **Status:** All used, wildcard import is justified.

#### 6. **Missing Null Safety in `currentWorkspace` Display** ⚠️ LOW
- **Location:** Line 72
- **Issue:** `currentWorkspace ?: "Not selected"` is fine, but it's a display concern rather than a code quality issue.
- **Status:** This is intentional and correct.

#### 7. **Redundant Empty Function Call** ⚠️ MEDIUM
- **Location:** Lines 190, 196
- **Issue:** Two settings cards with empty `onClick` handlers:
  ```kotlin
  SettingsCard(
      icon = Icons.Default.Info,
      title = "Version",
      subtitle = "1.0.0-alpha",
      onClick = {}  // Empty lambda
  )
  ```
- **Recommendation:** Either implement the click handler or remove these cards if they're just placeholders. If intentionally non-clickable, use a different component or add a comment explaining why.

#### 8. **Unused Variable: `displayColor`** ⚠️ LOW
- **Location:** Line 144 (inside forEach)
- **Issue:** `(name, displayColor) ->` destructuring in forEach but `displayColor` is immediately used, so this is fine.
- **Status:** Not an issue - variable IS used on line 158.

---

## File 4: McpSettingsCard.kt
**Path:** `app/src/main/java/com/amaya/intelligence/ui/settings/McpSettingsCard.kt`

### Issues Found

#### 1. **Unused Variable: `json`** ⚠️ MEDIUM
- **Location:** Line 43 (in filePicker lambda)
- **Issue:** The variable `json` is declared but immediately used in a null check:
  ```kotlin
  val json = withContext(Dispatchers.IO) {
      context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.bufferedReader().readText()
      }
  }
  if (!json.isNullOrBlank()) {
  ```
- **Problem:** Potentially confusing nullable return type from `withContext()`.
- **Recommendation:** Simplify the nested logic:
  ```kotlin
  scope.launch {
      val json = withContext(Dispatchers.IO) {
          try {
              context.contentResolver.openInputStream(uri)?.use { stream ->
                  stream.bufferedReader().readText()
              }
          } catch (e: Exception) {
              null
          }
      }
      json?.let {
          jsonState = it
          onImportJson(it)
          onCopyToFixedPath(it)
          showSuccess = true
      }
  }
  ```

#### 2. **Unused Import: `com.amaya.intelligence.data.remote.api.McpServerConfig`** ⚠️ LOW
- **Location:** Line 18
- **Issue:** `import com.amaya.intelligence.data.remote.api.McpServerConfig` is imported but used only in `defaultJson()` function (line 97).
- **Status:** The import IS used, not an issue.

#### 3. **Unused Import: `java.io.File`** ⚠️ LOW
- **Location:** Line 23
- **Issue:** `import java.io.File` is imported but NOT used anywhere in the file.
- **Recommendation:** Remove line 23.

#### 4. **Redundant Success Message** ⚠️ LOW
- **Location:** Lines 87-90
- **Issue:** The success message is hardcoded with the file path, but `AiSettingsManager.MCP_FIXED_PATH` already contains this. Referencing the constant would be better.
- **Code:**
  ```kotlin
  if (showSuccess) {
      Spacer(Modifier.height(8.dp))
      Text("Saved and copied to /storage/emulated/0/Amaya/mcp.json", ...)
  }
  ```
- **Recommendation:** Use the constant:
  ```kotlin
  if (showSuccess) {
      Spacer(Modifier.height(8.dp))
      Text("Saved and copied to ${AiSettingsManager.MCP_FIXED_PATH}", ...)
  }
  ```

#### 5. **Unused Import: `com.amaya.intelligence.data.remote.api.AiSettingsManager`** ⚠️ LOW
- **Location:** Line 16
- **Issue:** `AiSettingsManager` is imported but only used indirectly via the constant reference (if we use the recommendation above). Currently it's not used at all.
- **Status:** Currently NOT used, should be removed or the hardcoded path should use the constant from `AiSettingsManager`.

#### 6. **Unused Parameter: `onSaveJson` vs `onImportJson` vs `onCopyToFixedPath`** ⚠️ MEDIUM
- **Location:** Lines 29-31, 73-74, 50-51
- **Issue:** The function parameters `onSaveJson`, `onImportJson`, and `onCopyToFixedPath` are three separate callbacks but they might be doing the same thing:
  ```kotlin
  fun McpSettingsCard(
      settingsJson: String,
      onSaveJson: (String) -> Unit,
      onImportJson: (String) -> Unit,
      onCopyToFixedPath: (String) -> Unit
  )
  ```
  And in the calling code (SettingsScreen.kt, lines 176-180):
  ```kotlin
  McpSettingsCard(
      settingsJson = settings.mcpConfigJson,
      onSaveJson = { json -> scope.launch { aiSettingsManager.setMcpConfigJson(json) } },
      onImportJson = { json -> scope.launch { aiSettingsManager.setMcpConfigJson(json) } },
      onCopyToFixedPath = { json -> scope.launch { aiSettingsManager.writeMcpConfigToFixedPath(json) } }
  )
  ```
- **Problem:** `onSaveJson` and `onImportJson` do the exact same thing, and `onCopyToFixedPath` is always called together. This could be simplified.
- **Recommendation:** Consolidate callbacks - these three could be reduced to one or two with better naming.

#### 7. **Unused Composable Result** ⚠️ LOW
- **Location:** Line 59
- **Issue:** The title "MCP JSON Configuration" is created but could be part of a better card structure.
- **Status:** Not a code quality issue, just a design observation.

#### 8. **Magic Number: TextField Height** ⚠️ LOW
- **Location:** Line 66
- **Issue:** `heightIn(min = 160.dp)` is hardcoded.
- **Recommendation:** Extract to a constant or consider making it proportional.

---

## Summary Table

| File | Issue | Type | Severity | Line(s) |
|------|-------|------|----------|---------|
| AiRepository.kt | Unused variable `textBuffer` | Unused Variable | MEDIUM | 166 |
| AiRepository.kt | Unused import `Dispatchers` | Unused Import | LOW | 14 |
| AiRepository.kt | Unsafe `ProviderType.valueOf()` | Null Safety | MEDIUM | 98 |
| AiRepository.kt | Iterator tracking `iterations` | Code Smell | MEDIUM | 147-151 |
| AiRepository.kt | Redundant datetime formatters | Code Duplication | LOW | 269-270 |
| AiRepository.kt | Duplicated prompt blocks | Dead Code | LOW | 287-339 |
| AiSettings.kt | Blocking `runBlocking()` on main thread | Anti-Pattern | HIGH | 73 |
| AiSettings.kt | Redundant null coalescing (3 places) | Null Safety | MEDIUM | 78-80, 94 |
| AiSettings.kt | Inefficient list operations | Optimization | MEDIUM | 122-128 |
| SettingsScreen.kt | Unused `snackbarHostState` | Unused Variable | MEDIUM | 37-38, 44 |
| SettingsScreen.kt | Hardcoded lists should be constants | Code Quality | LOW | 113-114, 129-135 |
| SettingsScreen.kt | Empty click handlers | Dead Code | MEDIUM | 190, 196 |
| McpSettingsCard.kt | Unused import `File` | Unused Import | LOW | 23 |
| McpSettingsCard.kt | Hardcoded path string | Code Duplication | LOW | 89 |
| McpSettingsCard.kt | Unused import `AiSettingsManager` | Unused Import | LOW | 16 |
| McpSettingsCard.kt | Redundant callback parameters | Design Issue | MEDIUM | 29-31, 73-74 |
| McpSettingsCard.kt | Nullable variable handling | Code Clarity | MEDIUM | 43 |

---

## Recommendations Priority

### 🔴 HIGH Priority
1. **AiSettings.kt:73** - Remove `runBlocking()` to avoid blocking UI thread
   - Change to suspend function or provide blocking variant only for initialization

### 🟡 MEDIUM Priority (Address Soon)
1. **AiRepository.kt:98** - Fix `ProviderType.valueOf()` to handle invalid values
2. **AiSettings.kt:78-80,94** - Remove redundant null coalescing operators
3. **SettingsScreen.kt:37-38** - Remove unused `snackbarHostState`
4. **McpSettingsCard.kt:29-31** - Consolidate redundant callback parameters
5. **SettingsScreen.kt:190,196** - Remove or implement empty click handlers

### 🟢 LOW Priority (Nice to Have)
1. **AiRepository.kt:14** - Remove unused `Dispatchers` import
2. **SettingsScreen.kt:113-135** - Move hardcoded lists to constants
3. **McpSettingsCard.kt:23** - Remove unused `File` import
4. **McpSettingsCard.kt:89** - Use `AiSettingsManager.MCP_FIXED_PATH` constant

