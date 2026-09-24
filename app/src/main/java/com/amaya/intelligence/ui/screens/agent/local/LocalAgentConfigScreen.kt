package com.amaya.intelligence.ui.screens.agent.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.entity.AgentEntity
import com.amaya.intelligence.data.local.entity.AgentGroupEntity
import com.amaya.intelligence.domain.models.AgentCapabilityProfile
import com.amaya.intelligence.ui.screens.amaya.AmayaDivider
import com.amaya.intelligence.ui.screens.amaya.AmayaNavigationRow
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.domain.models.ModelOption

@Composable
fun LocalAgentConfigScreen(
    group: AgentGroupEntity,
    agent: AgentEntity,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSave: (String, String, String, AgentCapabilityProfile, Set<String>) -> Unit,
    availableModels: List<ModelOption> = emptyList(),
    globalModelKey: String? = null,
    onAddReference: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenReminders: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(agent.id, agent.name) { mutableStateOf(agent.name) }
    var role by remember(agent.id, agent.role) { mutableStateOf(agent.role) }
    var instructions by remember(agent.id, agent.instructions) { mutableStateOf(agent.instructions) }
    var profile by remember(agent.id, agent.capabilityProfile) { mutableStateOf(AgentCapabilityProfile.decode(agent.capabilityProfile)) }
    var confirmDelete by remember(agent.id) { mutableStateOf(false) }
    var defaultModelKeys by remember(agent.id, agent.defaultModelKeysJson) { mutableStateOf(parseModelKeys(agent.defaultModelKeysJson)) }

    LaunchedEffect(name, role, instructions, profile, defaultModelKeys) {
        if (name.isNotBlank()) {
            delay(250)
            onSave(name.trim(), role.trim(), instructions.trim(), profile, defaultModelKeys)
        }
    }

    var showModelSheet by remember(agent.id) { mutableStateOf(false) }
    var identitySheet by remember(agent.id) { mutableStateOf<String?>(null) }
    var detailSheet by remember(agent.id) { mutableStateOf<String?>(null) }

    AmayaScaffold(agent.name, snackbarHostState, onNavigateBack) {
        AmayaSection("Default Models") {
            AmayaNavigationRow(
                Icons.Default.Psychology,
                "Models",
                if (defaultModelKeys.isEmpty()) "Use global model · ${globalModelKey.orEmpty()}" else "${defaultModelKeys.size} selected",
                onClick = { showModelSheet = true }
            )
        }
        AmayaSection("Agent") {
            AmayaNavigationRow(Icons.Default.Badge, "Agent name", name, onClick = { identitySheet = "name" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Work, "Role", role.ifBlank { "No role" }, onClick = { identitySheet = "role" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Description, "Instructions", instructions.ifBlank { "No instructions" }, onClick = { identitySheet = "instructions" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Psychology, "Group", "${group.name} · ${group.workspacePath}", onClick = {})
        }
        AmayaSection("Agent Context") {
            AmayaNavigationRow(Icons.Default.AttachFile, "References", "${referenceCount(agent.referencePathsJson)} attached · private to this agent", onAddReference)
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Psychology, "Agent Memory", "Private saved memory for this agent", onOpenMemory)
        }
        AmayaSection("Automation") {
            AmayaNavigationRow(Icons.Default.Alarm, "Reminders & Jobs", "Schedules owned by this agent", onOpenReminders)
        }
        AmayaSection("Tools") {
            capabilityRows.forEachIndexed { index, row ->
                if (index > 0) AmayaDivider()
                AmayaNavigationRow(
                    row.icon,
                    row.title,
                    row.subtitle(profile),
                    onClick = { detailSheet = row.category }
                )
            }
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete Agent", color = MaterialTheme.colorScheme.error)
        }
    }

    when (identitySheet) {
        "name" -> AgentIdentitySheet("Agent name", name, { name = it }, { identitySheet = null })
        "role" -> AgentIdentitySheet("Role", role, { role = it }, { identitySheet = null })
        "instructions" -> AgentIdentitySheet("Instructions", instructions, { instructions = it }, { identitySheet = null }, multiline = true)
        null -> Unit
    }

    detailSheet?.let { category ->
        CapabilityDetailSheet(
            category = category,
            profile = profile,
            onToggleCategory = { enabled ->
                profile = when (category) {
                    "workspace" -> profile.copy(workspace = enabled)
                    "terminal" -> profile.copy(terminal = enabled)
                    "browser" -> profile.copy(browser = enabled)
                    "subagents" -> profile.copy(subagents = enabled)
                    "web_search" -> profile.copy(webSearch = enabled)
                    "skills" -> profile.copy(skills = enabled)
                    "reminders" -> profile.copy(reminders = enabled)
                    "todo" -> profile.copy(todo = enabled)
                    "mcp" -> profile.copy(mcp = enabled)
                    else -> profile
                }
            },
            onToggleTool = { tool ->
                profile = profile.copy(
                    excludedTools = if (tool in profile.excludedTools) profile.excludedTools - tool else profile.excludedTools + tool
                )
            },
            onDismiss = { detailSheet = null }
        )
    }

    if (showModelSheet) ModelSelectionSheet(
        models = availableModels,
        selectedKeys = defaultModelKeys,
        globalKey = globalModelKey,
        onToggle = { key -> defaultModelKeys = if (key in defaultModelKeys) defaultModelKeys - key else defaultModelKeys + key },
        onUseGlobal = { defaultModelKeys = emptySet() },
        onDismiss = { showModelSheet = false }
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${agent.name}?") },
        text = { Text("This agent, its conversations, references, and private memory will be permanently deleted.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AgentIdentitySheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    multiline: Boolean = false
) {
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(onDismissRequest = onDismiss, title = title) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(title) },
            singleLine = !multiline,
            minLines = if (multiline) 5 else 1
        )
        Button(onClick = { dismiss(onDismiss) }, enabled = title != "Agent name" || value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    models: List<ModelOption>,
    selectedKeys: Set<String>,
    globalKey: String?,
    onToggle: (String) -> Unit,
    onUseGlobal: () -> Unit,
    onDismiss: () -> Unit
) {
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(onDismissRequest = onDismiss, title = "Default models") {
        Text("Choose active Manage Models entries for this agent. Empty selection uses the global model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val globalModel = models.firstOrNull { it.id == globalKey }
        androidx.compose.material3.FilterChip(
            selected = selectedKeys.isEmpty(),
            onClick = onUseGlobal,
            label = { Text("Global default · ${globalModel?.name ?: "Not selected"}") }
        )
        models.forEach { model ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = model.id in selectedKeys, onCheckedChange = { onToggle(model.id) })
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${model.providerName} · ${model.modelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun referenceCount(json: String): Int = runCatching { org.json.JSONArray(json).length() }.getOrDefault(0)

private data class CapabilityRow(
    val category: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: (AgentCapabilityProfile) -> String
)

private val capabilityRows = listOf(
    CapabilityRow("workspace", Icons.Default.Folder, "Workspace", { p ->
        if (!p.workspace) "Off" else
            "On · " + com.amaya.intelligence.domain.models.CapabilityToolGroups.workspace
                .count { it !in p.excludedTools }
                .let { "$it of ${com.amaya.intelligence.domain.models.CapabilityToolGroups.workspace.size} tools" }
    }),
    CapabilityRow("terminal", Icons.Default.Build, "Terminal", { p ->
        if (!p.terminal) "Off" else "On · shell inside the workspace"
    }),
    CapabilityRow("browser", Icons.Default.Language, "Browser", { p ->
        if (!p.browser) "Off" else "On · control the local browser"
    }),
    CapabilityRow("web_search", Icons.Default.Tag, "Web Search", { p ->
        if (!p.webSearch) "Off" else "On · search and read public pages"
    }),
    CapabilityRow("skills", Icons.Default.Extension, "Skills", { p ->
        if (!p.skills) "Off" else
            "On · " + com.amaya.intelligence.domain.models.CapabilityToolGroups.skills
                .count { it !in p.excludedTools }
                .let { "$it of ${com.amaya.intelligence.domain.models.CapabilityToolGroups.skills.size} tools" }
    }),
    CapabilityRow("reminders", Icons.Default.Alarm, "Reminders", { p ->
        if (!p.reminders) "Off" else "On · create Android reminders"
    }),
    CapabilityRow("todo", Icons.Default.CheckCircle, "Todo", { p ->
        if (!p.todo) "Off" else "On · maintain the live task list"
    }),
    CapabilityRow("subagents", Icons.Default.Groups, "Delegation", { p ->
        if (!p.subagents) "Off" else
            "On · " + com.amaya.intelligence.domain.models.CapabilityToolGroups.subagents
                .count { it !in p.excludedTools }
                .let { "$it of ${com.amaya.intelligence.domain.models.CapabilityToolGroups.subagents.size} tools" }
    }),
    CapabilityRow("mcp", Icons.Default.SmartToy, "MCP", { p ->
        if (!p.mcp) "Off" else "On · external MCP servers"
    })
)

private fun profileFlagFor(category: String, profile: AgentCapabilityProfile): Boolean = when (category) {
    "workspace" -> profile.workspace
    "terminal" -> profile.terminal
    "browser" -> profile.browser
    "subagents" -> profile.subagents
    "web_search" -> profile.webSearch
    "skills" -> profile.skills
    "reminders" -> profile.reminders
    "todo" -> profile.todo
    "mcp" -> profile.mcp
    else -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapabilityDetailSheet(
    category: String,
    profile: AgentCapabilityProfile,
    onToggleCategory: (Boolean) -> Unit,
    onToggleTool: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = com.amaya.intelligence.domain.models.CapabilityToolGroups
    val tools = when (category) {
        "workspace" -> groups.workspace
        "terminal" -> groups.terminal
        "browser" -> groups.browser
        "subagents" -> groups.subagents
        "web_search" -> groups.webSearch
        "skills" -> groups.skills
        "reminders" -> groups.reminders
        "todo" -> groups.todo
        "mcp" -> groups.mcp
        else -> emptyList()
    }
    val title = capabilityRows.firstOrNull { it.category == category }?.title ?: category
    val categoryEnabled = profileFlagFor(category, profile)
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(onDismissRequest = onDismiss, title = "$title tools") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Category", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (categoryEnabled) "Enabled — individual tools below can be turned off" else "Off — no tool in this category reaches the model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(checked = categoryEnabled, onCheckedChange = onToggleCategory)
        }
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        if (tools.size == 1 && tools.first() == "mcp") {
            Text(
                "All tools from MCP servers configured in Settings → MCP. Disable specific servers there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tools.forEach { tool ->
                val toolEnabled = categoryEnabled && tool !in profile.excludedTools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = toolEnabled,
                        onCheckedChange = { if (categoryEnabled) onToggleTool(tool) },
                        enabled = categoryEnabled
                    )
                    Column(Modifier.weight(1f)) {
                        Text(tool, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            groups.descriptionFor(tool),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun parseModelKeys(json: String): Set<String> = runCatching {
    val array = org.json.JSONArray(json)
    buildSet { repeat(array.length()) { add(array.getString(it)) } }
}.getOrDefault(emptySet())
