# Agent/Model Selection Code - ChatScreen.kt

## 1. State Variables (Lines 69-76)
```kotlin
var showModelSelector by remember { mutableStateOf(false) }
var showSessionInfo by remember { mutableStateOf(false) }

// Active agent derived from settings
val agentConfigs  = uiState.agentConfigs
val activeAgentId = uiState.activeAgentId
val activeAgent   = agentConfigs.find { it.id == activeAgentId } ?: agentConfigs.firstOrNull()
val selectedModel = uiState.selectedModel.ifBlank { activeAgent?.modelId ?: "" }
```

## 2. TopAppBar Selector Button (Lines 437-463)
Tombol clickable di header yang menampilkan agent name atau model yang dipilih:

```kotlin
Surface(
    onClick = { showModelSelector = true },  // Opens dropdown
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.padding(vertical = 4.dp)
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (activeAgent?.name ?: selectedModel).ifBlank { "Select Agent" }
                .let { if (it.length > 20) it.take(18) + "…" else it },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Select Model",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
```

**Key Points:**
- Menampilkan `activeAgent?.name` jika ada agent yang dipilih
- Fallback ke `selectedModel` jika agent name kosong
- Default text: "Select Agent"
- Max 20 char, truncate dengan "…" jika lebih panjang

## 3. DropdownMenu (Lines 466-515)
Menu dropdown yang muncul saat user klik tombol di atas:

```kotlin
DropdownMenu(
    expanded = showModelSelector,
    onDismissRequest = { showModelSelector = false },
    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
) {
    if (agentConfigs.isEmpty()) {
        DropdownMenuItem(
            text = {
                Text(
                    "No agents configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            },
            onClick = { showModelSelector = false }
        )
    } else {
        agentConfigs.forEach { agent ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            agent.name.ifBlank { "Unnamed Agent" },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            agent.modelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                leadingIcon = {
                    if (agent.id == activeAgentId) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    viewModel.setSelectedAgent(agent)  // <-- Key function call
                    showModelSelector = false
                }
            )
        }
    }
}
```

**Key Points:**
- Empty state: "No agents configured"
- Setiap agent ditampilkan dengan:
  - `agent.name` (main label)
  - `agent.modelId` (subtitle)
  - CheckCircle icon jika `agent.id == activeAgentId`
- **Callback:** `viewModel.setSelectedAgent(agent)` saat user klik

## 4. SessionInfoButton (Lines 524-530)
Button di TopAppBar actions yang menampilkan session info:

```kotlin
SessionInfoButton(
    totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens,
    activeModel = selectedModel,  // <-- Uses selectedModel
    activeReminderCount = activeReminderCount,
    hasTodayMemory = hasTodayMemory,
    onClick = { showSessionInfo = true }
)
```

## 5. Data Flow Summary

```
┌─────────────────────────────────────────────────────────────┐
│ UIState (from ViewModel)                                     │
├─────────────────────────────────────────────────────────────┤
│ • agentConfigs: List<AgentConfig>  (all configured agents)  │
│ • activeAgentId: String            (currently selected ID)   │
│ • selectedModel: String            (model ID override)       │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ Local Derived Values (in ChatScreen)                        │
├─────────────────────────────────────────────────────────────┤
│ activeAgent = agentConfigs.find { it.id == activeAgentId }  │
│ selectedModel = uiState.selectedModel ?: activeAgent?.modelId│
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ UI Display                                                   │
├─────────────────────────────────────────────────────────────┤
│ TopAppBar button text:                                       │
│   → activeAgent?.name ?: selectedModel ?: "Select Agent"    │
│                                                              │
│ Dropdown items:                                              │
│   → forEach(agentConfigs) display name + modelId            │
│   → show CheckCircle if agent.id == activeAgentId           │
│                                                              │
│ On selection:                                                │
│   → Call viewModel.setSelectedAgent(agent)                  │
└─────────────────────────────────────────────────────────────┘
```

## 6. ViewModel Function Called
**Function:** `viewModel.setSelectedAgent(agent)`
- **File:** `ChatViewModel.kt`
- **Purpose:** Update `activeAgentId` dan `selectedModel` di UIState
- **Called when:** User memilih agent dari dropdown menu

