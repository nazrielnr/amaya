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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
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
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import com.amaya.intelligence.ui.components.shared.SettingsEmptyState

@Composable
fun LocalAgentListScreen(
    groups: List<AgentGroupEntity>,
    agents: List<AgentEntity>,
    snackbarHostState: SnackbarHostState,
    selectedWorkspace: String?,
    onNavigateBack: () -> Unit,
    onOpenGroup: (AgentGroupEntity) -> Unit,
    onSelectWorkspace: () -> Unit,
    onCreateGroup: (String, String, String) -> Unit
) {
    var creatingGroup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        AmayaScaffold(stringResource(R.string.ai_agents), snackbarHostState, onNavigateBack) {
            if (groups.isEmpty()) {
                SettingsEmptyState(
                    title = stringResource(R.string.no_agent_groups_yet),
                    subtitle = stringResource(R.string.create_a_group_then_add_specialized_agents),
                    icon = Icons.Default.Groups,
                    modifier = Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateScreenTopSpacing)
                )
            } else AmayaSection(stringResource(R.string.agent_groups)) {
                groups.forEach { group ->
                    val count = agents.count { it.groupId == group.id }
                    Card(onClick = { onOpenGroup(group) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tag, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.group_id_label, group.id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(pluralStringResource(R.plurals.agents_in_workspace, count, count, group.workspacePath), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creatingGroup = true },
            icon = { Icon(Icons.Default.Add, stringResource(R.string.add_agent_group)) },
            text = { Text(stringResource(R.string.group)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                .amayaFloatingActionButtonBottomPadding()
        )
    }

    if (creatingGroup) AlertDialog(
        onDismissRequest = { creatingGroup = false },
        title = { Text(stringResource(R.string.new_agent_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.group_name)) }, singleLine = true)
                OutlinedTextField(instructions, { instructions = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.shared_instructions)) }, minLines = 3)
                OutlinedButton(onClick = onSelectWorkspace, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedWorkspace ?: stringResource(R.string.select_workspace))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateGroup(name.trim(), instructions.trim(), selectedWorkspace.orEmpty()); creatingGroup = false },
                enabled = name.isNotBlank() && !selectedWorkspace.isNullOrBlank()
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text(stringResource(R.string.cancel)) } }
    )
}
