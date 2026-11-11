package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ModelOption
import com.amaya.intelligence.ui.screens.amaya.iosAmayaColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    modelOptions: List<ModelOption>,
    activeModelKey: String,
    onSelect: (ModelOption) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = iosAmayaColors()
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(modelOptions, query) {
        val value = query.trim().lowercase()
        if (value.isBlank()) modelOptions else modelOptions.filter {
            it.name.lowercase().contains(value) ||
                it.modelId.lowercase().contains(value) ||
                it.providerName.lowercase().contains(value)
        }
    }
    val modelsLabel = stringResource(R.string.models)
    val grouped = remember(filtered, modelsLabel) {
        filtered.groupBy { it.providerName.ifBlank { modelsLabel } }
    }

    StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.select_model),
        scrollable = false,
        actions = {
            com.amaya.intelligence.ui.components.shared.AmayaTopBarButton(
                icon = Icons.Default.Search,
                onClick = { showSearch = !showSearch },
                contentDescription = stringResource(R.string.search)
            )
        }
    ) {
        val closeWithSelection: (ModelOption) -> Unit = { option -> dismiss { onSelect(option) } }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = StandardModalSheetDefaults.ContentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                if (showSearch) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                                }
                            },
                            placeholder = { Text(stringResource(R.string.search_models)) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
                if (modelOptions.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_models_shown_in_chat_add_models_in_settings_manage_models),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_models_found), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    grouped.forEach { (provider, options) ->
                        item(key = "header_$provider") {
                            Text(
                                provider.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.headerText,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                            )
                        }
                        item(key = "group_$provider") {
                            Surface(shape = RoundedCornerShape(16.dp), color = colors.groupSurface, border = androidx.compose.foundation.BorderStroke(0.7.dp, colors.border), modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    options.forEachIndexed { index, option ->
                                        val isActive = option.id == activeModelKey
                                        Surface(
                                            onClick = { closeWithSelection(option) },
                                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(if (isActive) MaterialTheme.colorScheme.primary else colors.iconBackground),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    ModelLeadingIcon(
                                                        modelId = option.modelId,
                                                        providerId = option.providerId,
                                                        iconType = option.iconType,
                                                        modifier = Modifier.size(17.dp),
                                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else colors.iconTint
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        option.name,
                                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                                        color = if (isActive) MaterialTheme.colorScheme.primary else colors.primaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (option.name != option.modelId) {
                                                        Text(option.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                        if (index < options.lastIndex) {
                                            HorizontalDivider(color = colors.separator)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}
