package com.amaya.intelligence.ui.screens.agent.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Description
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
import com.amaya.intelligence.ui.screens.amaya.AmayaSwitchRow
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

    AmayaScaffold(agent.name, snackbarHostState, onNavigateBack) {
        AmayaSection(stringResource(R.string.default_models)) {
            AmayaNavigationRow(
                Icons.Default.Psychology,
                stringResource(R.string.models),
                if (defaultModelKeys.isEmpty()) stringResource(R.string.use_global_model, globalModelKey.orEmpty()) else pluralStringResource(R.plurals.selected_models, defaultModelKeys.size, defaultModelKeys.size),
                onClick = { showModelSheet = true }
            )
        }
        AmayaSection(stringResource(R.string.agent)) {
            AmayaNavigationRow(Icons.Default.Badge, stringResource(R.string.agent_name), name, onClick = { identitySheet = "name" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Work, stringResource(R.string.role), role.ifBlank { stringResource(R.string.no_role) }, onClick = { identitySheet = "role" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Description, stringResource(R.string.instructions), instructions.ifBlank { stringResource(R.string.no_instructions) }, onClick = { identitySheet = "instructions" })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Psychology, stringResource(R.string.group), "${group.name} · ${group.workspacePath}", onClick = {})
        }
        AmayaSection(stringResource(R.string.agent_context)) {
            AmayaNavigationRow(Icons.Default.AttachFile, stringResource(R.string.references), pluralStringResource(R.plurals.private_references, referenceCount(agent.referencePathsJson), referenceCount(agent.referencePathsJson)), onAddReference)
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Psychology, stringResource(R.string.agent_memory), stringResource(R.string.private_saved_memory_for_this_agent), onOpenMemory)
        }
        AmayaSection(stringResource(R.string.automation)) {
            AmayaNavigationRow(Icons.Default.Alarm, stringResource(R.string.reminders_jobs), stringResource(R.string.schedules_owned_by_this_agent), onOpenReminders)
        }
        AmayaSection(stringResource(R.string.tools)) {
            AmayaSwitchRow(stringResource(R.string.workspace), stringResource(R.string.read_and_change_group_workspace_files), profile.workspace, { profile = profile.copy(workspace = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.terminal), stringResource(R.string.run_shell_commands_inside_the_workspace), profile.terminal, { profile = profile.copy(terminal = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.browser), stringResource(R.string.control_the_local_browser), profile.browser, { profile = profile.copy(browser = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.web_search), stringResource(R.string.search_and_read_public_web_pages), profile.webSearch, { profile = profile.copy(webSearch = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.skills), stringResource(R.string.view_and_manage_reusable_skills), profile.skills, { profile = profile.copy(skills = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.reminders), stringResource(R.string.create_android_reminders_and_scheduled_jobs), profile.reminders, { profile = profile.copy(reminders = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.todo), stringResource(R.string.maintain_the_live_task_list), profile.todo, { profile = profile.copy(todo = it) })
            AmayaDivider()
            AmayaSwitchRow(stringResource(R.string.delegation), stringResource(R.string.delegate_read_only_work_to_group_members), profile.subagents, { profile = profile.copy(subagents = it) })
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_agent), color = MaterialTheme.colorScheme.error)
        }
    }

    when (identitySheet) {
        "name" -> AgentIdentitySheet(stringResource(R.string.agent_name), name, { name = it }, { identitySheet = null }, required = true)
        "role" -> AgentIdentitySheet(stringResource(R.string.role), role, { role = it }, { identitySheet = null })
        "instructions" -> AgentIdentitySheet(stringResource(R.string.instructions), instructions, { instructions = it }, { identitySheet = null }, multiline = true)
        null -> Unit
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
        title = { Text(stringResource(R.string.delete_named_item, agent.name)) },
        text = { Text(stringResource(R.string.this_agent_its_conversations_references_and_private_memory_will_b)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun AgentIdentitySheet(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    multiline: Boolean = false,
    required: Boolean = false
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
        Button(onClick = { dismiss(onDismiss) }, enabled = !required || value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
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
    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.default_models_sentence)) {
        Text(stringResource(R.string.choose_active_manage_models_entries_for_this_agent_empty_selectio), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val globalModel = models.firstOrNull { it.id == globalKey }
        androidx.compose.material3.FilterChip(
            selected = selectedKeys.isEmpty(),
            onClick = onUseGlobal,
            label = { Text(stringResource(R.string.global_default_model, globalModel?.name ?: stringResource(R.string.not_selected))) }
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

private fun parseModelKeys(json: String): Set<String> = runCatching {
    val array = org.json.JSONArray(json)
    buildSet { repeat(array.length()) { add(array.getString(it)) } }
}.getOrDefault(emptySet())
