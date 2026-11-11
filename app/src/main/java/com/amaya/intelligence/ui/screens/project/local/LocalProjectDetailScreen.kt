package com.amaya.intelligence.ui.screens.project.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amaya.intelligence.data.local.entity.ProjectEntity
import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet
import com.amaya.intelligence.ui.screens.amaya.AmayaDivider
import com.amaya.intelligence.ui.screens.amaya.AmayaNavigationRow
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection

private enum class ProjectEditSheet { NAME, INSTRUCTIONS }

@Composable
fun LocalProjectDetailScreen(
    project: ProjectEntity,
    memoryCount: Int,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveInstructions: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onAddReference: () -> Unit,
    onDelete: () -> Unit
) {
    var editSheet by remember(project.id) { mutableStateOf<ProjectEditSheet?>(null) }
    var name by remember(project.id, project.name) { mutableStateOf(project.name) }
    var instructions by remember(project.id, project.instructions) { mutableStateOf(project.instructions) }
    var confirmDelete by remember(project.id) { mutableStateOf(false) }

    AmayaScaffold(project.name, snackbarHostState, onNavigateBack) {
        AmayaSection(stringResource(R.string.identity)) {
            AmayaNavigationRow(Icons.Default.Badge, stringResource(R.string.project_name), project.name, onClick = { name = project.name; editSheet = ProjectEditSheet.NAME })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Description, stringResource(R.string.instructions), project.instructions.ifBlank { stringResource(R.string.no_project_instructions) }, onClick = { instructions = project.instructions; editSheet = ProjectEditSheet.INSTRUCTIONS })
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Folder, stringResource(R.string.workspace), project.rootPath, onClick = {})
        }
        AmayaSection(stringResource(R.string.project)) {
            AmayaNavigationRow(Icons.Default.Memory, stringResource(R.string.project_memory), pluralStringResource(R.plurals.saved_memories, memoryCount, memoryCount), onOpenMemory)
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.AttachFile, stringResource(R.string.references), pluralStringResource(R.plurals.attached_references, referenceCount(project.referencePathsJson), referenceCount(project.referencePathsJson)), onAddReference)
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_project), color = MaterialTheme.colorScheme.error)
        }
    }

    when (editSheet) {
        ProjectEditSheet.NAME -> StandardModalBottomSheet(
            onDismissRequest = { editSheet = null },
            title = stringResource(R.string.project_name)
        ) {
            com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.settings)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
                    Button(
                        onClick = { onSaveName(name.trim()); editSheet = null },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.save_name)) }
                }
            }
        }
        ProjectEditSheet.INSTRUCTIONS -> StandardModalBottomSheet(
            onDismissRequest = { editSheet = null },
            title = stringResource(R.string.project_instructions)
        ) {
            com.amaya.intelligence.ui.screens.amaya.AmayaSection(stringResource(R.string.settings)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        instructions,
                        { instructions = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.instructions)) },
                        supportingText = { Text(stringResource(R.string.applied_to_every_conversation_in_this_project)) },
                        minLines = 5
                    )
                    Button(
                        onClick = { onSaveInstructions(instructions.trim()); editSheet = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.save_instructions)) }
                }
            }
        }
        null -> Unit
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_named_item, project.name)) },
        text = { Text(stringResource(R.string.project_conversations_and_imported_references_will_be_deleted_wor)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun referenceCount(json: String): Int = runCatching { org.json.JSONArray(json).length() }.getOrDefault(0)
