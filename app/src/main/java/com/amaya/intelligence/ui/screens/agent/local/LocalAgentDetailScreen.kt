package com.amaya.intelligence.ui.screens.agent.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.entity.AgentEntity
import com.amaya.intelligence.data.local.entity.AgentGroupEntity
import com.amaya.intelligence.ui.screens.amaya.AmayaDivider
import com.amaya.intelligence.ui.screens.amaya.AmayaNavigationRow
import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import com.amaya.intelligence.ui.components.shared.SettingsEmptyState

@Composable
fun LocalAgentDetailScreen(
    group: AgentGroupEntity,
    agents: List<AgentEntity>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSaveGroupName: (String) -> Unit,
    onSaveGroupInstructions: (String) -> Unit,
    onSelectWorkspace: () -> Unit,
    onAddReference: () -> Unit,
    onOpenAgent: (AgentEntity) -> Unit,
    onCreateAgent: (String, String, String) -> Unit,
    onDeleteGroup: () -> Unit
) {
    var name by remember(group.id, group.name) { mutableStateOf(group.name) }
    var instructions by remember(group.id, group.instructions) { mutableStateOf(group.instructions) }
    var creatingAgent by remember { mutableStateOf(false) }
    var editSheet by remember(group.id) { mutableStateOf<String?>(null) }
    var agentName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var agentInstructions by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AmayaScaffold(group.name, snackbarHostState, onNavigateBack) {
            AmayaSection(stringResource(R.string.group)) {
                AmayaNavigationRow(Icons.Default.Badge, stringResource(R.string.group_name), group.name, onClick = { name = group.name; editSheet = "name" })
                AmayaDivider()
                AmayaNavigationRow(Icons.Default.Description, stringResource(R.string.shared_instructions), group.instructions.ifBlank { stringResource(R.string.no_shared_instructions) }, onClick = { instructions = group.instructions; editSheet = "instructions" })
                AmayaDivider()
                AmayaNavigationRow(Icons.Default.Folder, stringResource(R.string.workspace), group.workspacePath, onSelectWorkspace)
                AmayaDivider()
                AmayaNavigationRow(Icons.Default.AttachFile, stringResource(R.string.shared_references), pluralStringResource(R.plurals.attached_references, referenceCount(group.referencePathsJson), referenceCount(group.referencePathsJson)), onAddReference)
            }
            AmayaSection(stringResource(R.string.agents)) {
                if (agents.isEmpty()) {
                    SettingsEmptyState(
                        title = stringResource(R.string.no_agents_in_this_group),
                        subtitle = stringResource(R.string.add_a_new_role_to_get_started),
                        icon = Icons.Default.SmartToy,
                        buttonText = stringResource(R.string.create_agent),
                        onButtonClick = { creatingAgent = true },
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else agents.forEachIndexed { index, agent ->
                    if (index > 0) AmayaDivider()
                    AmayaNavigationRow(Icons.Default.SmartToy, "${agent.name} · ID ${agent.localId}", agent.role.ifBlank { stringResource(R.string.no_role) }, onClick = { onOpenAgent(agent) })
                }
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_group), color = MaterialTheme.colorScheme.error)
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creatingAgent = true },
            icon = { Icon(Icons.Default.Add, stringResource(R.string.add_agent)) },
            text = { Text(stringResource(R.string.agent)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                .amayaFloatingActionButtonBottomPadding()
        )
    }

    when (editSheet) {
        "name" -> StandardModalBottomSheet(onDismissRequest = { editSheet = null }, title = stringResource(R.string.group_name)) {
            com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.settings)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_name)) }, singleLine = true)
                    Button(onClick = { onSaveGroupName(name.trim()); editSheet = null }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_name)) }
                }
            }
        }
        "instructions" -> StandardModalBottomSheet(onDismissRequest = { editSheet = null }, title = stringResource(R.string.shared_instructions)) {
            com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.settings)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.instructions)) }, minLines = 5)
                    Button(onClick = { onSaveGroupInstructions(instructions.trim()); editSheet = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_instructions)) }
                }
            }
        }
        null -> Unit
    }

    if (creatingAgent) StandardModalBottomSheet(onDismissRequest = { creatingAgent = false }, title = stringResource(R.string.new_agent)) {
        com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.agent_details)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(agentName, { agentName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.agent_name)) }, singleLine = true)
                OutlinedTextField(role, { role = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.role)) }, singleLine = true)
                OutlinedTextField(agentInstructions, { agentInstructions = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.instructions)) }, minLines = 4)
                Button(onClick = { onCreateAgent(agentName.trim(), role.trim(), agentInstructions.trim()); creatingAgent = false }, enabled = agentName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_agent_sentence)) }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_named_item, group.name)) },
        text = { Text(stringResource(R.string.all_agents_delegation_tasks_group_conversations_and_imported_refe)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteGroup() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun referenceCount(json: String): Int = runCatching { org.json.JSONArray(json).length() }.getOrDefault(0)
