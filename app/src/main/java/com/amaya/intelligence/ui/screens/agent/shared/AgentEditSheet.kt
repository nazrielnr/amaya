package com.amaya.intelligence.ui.screens.agent.shared

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.ui.res.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditSheet(
    config: AgentConfig,
    apiKey: String,
    isNew: Boolean,
    maxSheetHeight: Dp,
    onDismiss: () -> Unit,
    onSave: (AgentConfig, String) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    var name by remember(config.id) { mutableStateOf(config.name) }
    var providerType by remember(config.id) { mutableStateOf(config.providerType) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var modelId by remember(config.id) { mutableStateOf(config.modelId) }
    var key by remember(config.id) { mutableStateOf(apiKey) }
    var enabled by remember(config.id) { mutableStateOf(config.enabled) }
    var maxTokensStr by remember(config.id) { mutableStateOf(config.maxTokens.toString()) }
    var showKey by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isNew) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isNew) "New Agent" else "Edit Agent",
                    style = MaterialTheme.typography.titleMedium
                )
            }
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(UiStrings.Labels.NAME) },
            placeholder = { Text(UiStrings.Placeholders.AGENT_NAME_EXAMPLE) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
        )

        // Provider type dropdown
        var providerExpanded by remember { mutableStateOf(false) }
        val providerOptions = listOf("OPENAI", "ANTHROPIC", "GEMINI", "CUSTOM")
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = it }
        ) {
            OutlinedTextField(
                value = providerType.lowercase().replaceFirstChar { it.uppercaseChar() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                providerOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.lowercase().replaceFirstChar { it.uppercaseChar() }) },
                        onClick = { providerType = option; providerExpanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // API Key
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(UiStrings.Agents.API_KEY) },
            placeholder = { Text(UiStrings.Placeholders.API_KEY_EXAMPLE) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        // Base URL (only for OPENAI-compatible / CUSTOM)
        if (providerType == "OPENAI" || providerType == "CUSTOM") {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(UiStrings.Agents.BASE_URL) },
                placeholder = { Text(UiStrings.Placeholders.BASE_URL_EXAMPLE) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Model ID
        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            label = { Text(UiStrings.Agents.MODEL_ID) },
            placeholder = { Text(UiStrings.Placeholders.MODEL_ID_EXAMPLE) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(18.dp)) }
        )

        // Max Tokens
        OutlinedTextField(
            value = maxTokensStr,
            onValueChange = { v -> if (v.all { it.isDigit() }) maxTokensStr = v },
            label = { Text("Max Tokens") },
            placeholder = { Text("8192") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp)) },
            supportingText = { Text("Max output tokens (default: 8192, Anthropic supports up to 16000)") }
        )

        // Enabled toggle
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (enabled) "Agent available to AI" else "Agent will be skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Validation errors
        val nameError = name.trim().isBlank()
        val modelIdError = modelId.trim().isBlank()
        if (nameError || modelIdError) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (nameError) {
                    Text(
                        "• Agent name is required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (modelIdError) {
                    Text(
                        "• Model ID is required (e.g. gpt-4o, claude-sonnet-4-20250514)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Save + Delete row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onDelete != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val finalBaseUrl = when (providerType) {
                        "ANTHROPIC", "GEMINI" -> ""
                        else -> baseUrl.trim()
                    }
                    onSave(
                        config.copy(
                            name = name.trim(),
                            providerType = providerType,
                            baseUrl = finalBaseUrl,
                            modelId = modelId.trim(),
                            enabled = enabled,
                            maxTokens = maxTokensStr.toIntOrNull()?.coerceIn(256, 64000) ?: 8192
                        ),
                        key.trim()
                    )
                },
                enabled = name.isNotBlank() && modelId.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Agent?") },
            text = { Text("\"${config.name.ifBlank { "This agent" }}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete?.invoke() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
