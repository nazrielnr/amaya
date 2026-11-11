package com.amaya.intelligence.ui.screens.project.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.entity.ProjectEntity
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.amayaFloatingActionButtonBottomPadding
import com.amaya.intelligence.ui.components.shared.SettingsEmptyState

@Composable
fun LocalProjectListScreen(
    projects: List<ProjectEntity>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onAddProject: () -> Unit,
    onOpenProject: (ProjectEntity) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AmayaScaffold(stringResource(R.string.projects), snackbarHostState, onNavigateBack) {
            if (projects.isEmpty()) {
                SettingsEmptyState(
                    title = stringResource(R.string.no_projects_yet),
                    subtitle = stringResource(R.string.add_a_workspace_then_give_it_a_custom_project_name),
                    icon = Icons.Default.FolderOpen,
                    modifier = Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateScreenTopSpacing)
                )
            } else {
                AmayaSection(stringResource(R.string.projects)) {
                    projects.forEach { project ->
                        Card(
                            onClick = { onOpenProject(project) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tag, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.project_id_label, project.id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(project.rootPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddProject,
            icon = { Icon(Icons.Default.Add, stringResource(R.string.add_project)) },
            text = { Text(stringResource(R.string.project)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                .amayaFloatingActionButtonBottomPadding()
        )
    }
}
