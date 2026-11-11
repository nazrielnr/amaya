package com.amaya.intelligence.ui.screens.agent.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.AgentMemoryRecord
import com.amaya.intelligence.ui.screens.amaya.AmayaDivider
import com.amaya.intelligence.ui.screens.amaya.AmayaNavigationRow
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection

@Composable
fun LocalAgentMemoryScreen(
    records: List<AgentMemoryRecord>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (AgentMemoryRecord) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AgentMemoryRecord?>(null) }
    var content by remember { mutableStateOf("") }
    AmayaScaffold(stringResource(R.string.agent_memory), snackbarHostState, onNavigateBack) {
        AmayaSection(stringResource(R.string.private_to_this_agent)) {
            AmayaNavigationRow(Icons.Default.Add, stringResource(R.string.add_memory), stringResource(R.string.saved_only_for_this_agent), onClick = { adding = true })
            records.forEach { record ->
                AmayaDivider()
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.title)
                        Text(record.content)
                    }
                    IconButton(onClick = { deleting = record }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete_memory), tint = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text(stringResource(R.string.add_agent_memory)) },
        text = { OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.memory)) }, minLines = 3) },
        confirmButton = { TextButton(enabled = content.isNotBlank(), onClick = { onAdd(content.trim()); content = ""; adding = false }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } }
    )
    deleting?.let { record -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.delete_memory_sentence)) },
        text = { Text(record.content) },
        confirmButton = { TextButton(onClick = { onDelete(record); deleting = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
    ) }
}
