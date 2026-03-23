package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.ui.res.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpEditSheet(
    existing: McpServerConfig?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val maxSheetHeight = (0.75f * LocalConfiguration.current.screenHeightDp).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var serverUrl by remember { mutableStateOf(existing?.serverUrl ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var headers by remember {
        mutableStateOf<List<Pair<String, String>>>(
            existing?.headers?.entries?.map { it.key to it.value } ?: emptyList()
        )
    }

    val nameError = when {
        name.isBlank() -> "Name is required"
        existingNames.contains(name.trim()) -> "Name already used"
        else -> null
    }
    val urlError = if (serverUrl.isBlank()) "Server URL is required" else null
    val isValid = nameError == null && urlError == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (existing != null) "Edit MCP Server" else "New MCP Server",
                    style = MaterialTheme.typography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                .compositeOver(MaterialTheme.colorScheme.background)
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim() },
                label = { Text(UiStrings.Labels.NAME) },
                placeholder = { Text(UiStrings.Placeholders.NAME_EXAMPLE) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it.trim() },
                label = { Text(UiStrings.Mcp.SERVER_URL) },
                placeholder = { Text(UiStrings.Placeholders.SERVER_URL_EXAMPLE) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)) }
            )

            HorizontalDivider()
            Text(UiStrings.Mcp.HEADERS_OPTIONAL, style = MaterialTheme.typography.labelLarge)
            Text(
                UiStrings.Mcp.HEADERS_DESCRIPTION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            headers.indices.forEach { idx ->
                val (key, value) = headers[idx]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { newKey ->
                            headers = headers.mapIndexed { i, p -> if (i == idx) newKey to p.second else p }
                        },
                        label = { Text(UiStrings.Mcp.KEY) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newVal ->
                            headers = headers.mapIndexed { i, p -> if (i == idx) p.first to newVal else p }
                        },
                        label = { Text(UiStrings.Mcp.VALUE) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(onClick = {
                        headers = headers.filterIndexed { i, _ -> i != idx }
                    }) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }

            OutlinedButton(
                onClick = { headers = headers + ("" to "") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Header")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (enabled) "Server will be used by the AI agent" else "Server will be skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Button(
                onClick = {
                    val validHeaders = headers
                        .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
                        .associate { pair -> pair.first to pair.second }
                    onSave(
                        McpServerConfig(
                            name = name.trim(),
                            serverUrl = serverUrl.trim(),
                            headers = validHeaders,
                            enabled = enabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (existing != null) "Update Server" else "Add Server")
            }
        }
    }
}
