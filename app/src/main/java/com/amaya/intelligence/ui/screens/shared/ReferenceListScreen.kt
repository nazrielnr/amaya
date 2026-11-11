package com.amaya.intelligence.ui.screens.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.amaya.intelligence.ui.screens.amaya.AmayaDivider
import com.amaya.intelligence.ui.screens.amaya.AmayaNavigationRow
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import java.io.File

@Composable
fun ReferenceListScreen(
    title: String,
    paths: List<String>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onAdd: () -> Unit,
    onAddManual: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var manual by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        AmayaScaffold(title, snackbarHostState, onNavigateBack) {
            if (paths.isEmpty()) {
                com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
                    title = stringResource(R.string.no_references),
                    subtitle = stringResource(R.string.add_a_text_document_for_this_context),
                    icon = Icons.Default.Description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AmayaGroupedSettingsTokens.emptyStateScreenTopSpacing)
                )
            } else AmayaSection(stringResource(R.string.references)) {
                paths.forEachIndexed { index, path ->
                    if (index > 0) AmayaDivider()
                    AmayaNavigationRow(Icons.Default.Description, File(path).name.substringAfter('_'), stringResource(R.string.tap_to_remove), onClick = { deleting = path })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, stringResource(R.string.add_reference)) },
            text = { Text(stringResource(R.string.action_import)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                .amayaFloatingActionButtonBottomPadding()
        )
        ExtendedFloatingActionButton(
            onClick = { manual = true },
            icon = { Icon(Icons.Default.Add, stringResource(R.string.add_note)) },
            text = { Text(stringResource(R.string.note)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                .amayaFloatingActionButtonBottomPadding()
        )
    }
    if (manual) AlertDialog(
        onDismissRequest = { manual = false },
        title = { Text(stringResource(R.string.add_reference_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.content)) }, minLines = 4)
            }
        },
        confirmButton = { TextButton(enabled = content.isNotBlank(), onClick = { onAddManual(name, content); name = ""; content = ""; manual = false }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { manual = false }) { Text(stringResource(R.string.cancel)) } }
    )
    deleting?.let { path -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.remove_reference)) },
        text = { Text(File(path).name.substringAfter('_')) },
        confirmButton = { TextButton(onClick = { onDelete(path); deleting = null }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
    ) }
}
