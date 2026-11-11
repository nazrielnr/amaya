package com.amaya.intelligence.ui.screens.amaya

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.MemoryRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryAreaListScreen(
    area: MemoryArea,
    state: AmayaUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (MemoryRecord) -> Unit = {}
) {
    val colors = iosAmayaColors()
    var showAddSheet by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MemoryRecord?>(null) }

    val records = when (area) {
        MemoryArea.USER -> state.userMemoryRecords
        MemoryArea.PROJECT -> state.projectMemoryRecords
    }
    Box(modifier = Modifier.fillMaxSize().background(colors.groupedBackground)) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = AmayaGroupedSettingsTokens.contentHorizontalPadding,
                end = AmayaGroupedSettingsTokens.contentHorizontalPadding,
                bottom = AmayaGroupedSettingsTokens.floatingActionButtonContentClearance
            ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AmayaGroupedSettingsTokens.sectionSpacing)
        ) {
            item {
                Spacer(
                    Modifier
                        .statusBarsPadding()
                        .height(AmayaGroupedSettingsTokens.screenContentTopSpacer)
                )
            }
            item {
                if (records.isNotEmpty()) {
                    AmayaSection(stringResource(R.string.saved)) {
                        records.forEachIndexed { index, record ->
                            MemoryRecordCard(record = record, colors = colors, onDelete = { deleting = record })
                            if (index < records.lastIndex) AmayaDivider()
                        }
                    }
                } else {
                    com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
                        title = stringResource(R.string.no_saved_items),
                        subtitle = if (area == MemoryArea.PROJECT && state.workspacePath == null) stringResource(R.string.select_a_workspace_first) else stringResource(R.string.add_a_memory_to_get_started),
                        icon = if (area == MemoryArea.PROJECT) androidx.compose.material.icons.Icons.Default.FolderOpen else androidx.compose.material.icons.Icons.Default.Person,
                        modifier = Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateListTopSpacing)
                    )
                }
            }
        }

        AmayaTopScrim(Modifier.align(Alignment.TopCenter))

        TopAppBar(
            title = {
                Text(
                    stringResource(if (area == MemoryArea.USER) R.string.about_you else R.string.project_memory),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = AmayaGroupedSettingsTokens.topBarTitleStartPadding),
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                com.amaya.intelligence.ui.components.shared.SettingsBackButton(onClick = onNavigateBack)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = AmayaGroupedSettingsTokens.topBarHorizontalPadding),
            windowInsets = WindowInsets(0.dp)
        )

        val showFab = area != MemoryArea.PROJECT || state.workspacePath != null
        if (showFab) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { androidx.compose.material3.Icon(Icons.Default.Add, stringResource(R.string.add_memory)) },
                text = { androidx.compose.material3.Text(stringResource(R.string.memory)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AmayaGroupedSettingsTokens.floatingActionButtonInset)
                    .amayaFloatingActionButtonBottomPadding()
            )
        }
    }

    if (showAddSheet) {
        AddMemorySheet(
            area = area,
            onDismiss = { showAddSheet = false },
            onAdd = onAdd
        )
    }
    deleting?.let { record -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.delete_memory_sentence)) },
        text = { Text(record.content) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDelete(record); deleting = null }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
    ) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemorySheet(
    area: MemoryArea,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val colors = iosAmayaColors()
    var text by remember { mutableStateOf("") }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_memory_area, stringResource(if (area == MemoryArea.USER) R.string.about_you else R.string.project_memory))
    ) {
        val cancel = { dismiss() }
        val save = { dismiss { onAdd(text) } }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(area.inputLabel()) },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.primaryText)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = cancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(AmayaGroupedSettingsTokens.rowIconTextGap))
            Button(
                onClick = save,
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun MemoryRecordCard(record: MemoryRecord, colors: com.amaya.intelligence.ui.screens.amaya.IosAmayaColors, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AmayaGroupedSettingsTokens.rowHorizontalPadding,
                vertical = AmayaGroupedSettingsTokens.rowVerticalPadding
            ),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AmayaGroupedSettingsTokens.rowTextSpacing)
        ) {
            Text(
                record.content,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primaryText
            )
            if (record.reason.isNotBlank()) {
                Text(
                    record.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete_memory), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MemoryArea.inputLabel(): String = when (this) {
    MemoryArea.USER -> stringResource(R.string.preference_or_profile_fact)
    MemoryArea.PROJECT -> stringResource(R.string.workspace_or_project_fact)
}

