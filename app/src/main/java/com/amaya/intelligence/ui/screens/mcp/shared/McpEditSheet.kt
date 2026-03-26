package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpEditSheet(
    initialJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rawJson by remember {
        mutableStateOf(
            initialJson.ifBlank {
                """
                {
                  "mcpServers": {}
                }
                """.trimIndent()
            }
        )
    }

    val rawJsonError = when {
        rawJson.isBlank() -> "Raw JSON is required"
        runCatching { org.json.JSONObject(rawJson) }.isFailure -> "Invalid MCP JSON"
        else -> null
    }
    val isValid = rawJsonError == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
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
                    "New MCP",
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
                value = rawJson,
                onValueChange = { rawJson = it },
                placeholder = {
                    Text(
                        "{\n  \"mcpServers\": {\n    \"my-server\": {\n      \"serverUrl\": \"https://mcp.example.com/mcp\",\n      \"headers\": {},\n      \"enabled\": true\n    }\n  }\n}",
                        fontFamily = FontFamily.Monospace
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                maxLines = 24,
                shape = RoundedCornerShape(12.dp),
                isError = rawJsonError != null,
                supportingText = rawJsonError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )

            Button(
                onClick = {
                    onSave(rawJson.trim())
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}
