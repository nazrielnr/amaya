package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.data.remote.mcp.McpServerTestResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpEditSheet(
    initialJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTestConnection: (suspend (McpServerConfig) -> McpServerTestResult)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<McpServerTestResult?>(null) }

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

    fun testServerConnection() {
        if (!isValid || onTestConnection == null) return
        scope.launch {
            isTesting = true
            testFeedback = null
            try {
                val config = McpConfig.fromJson(rawJson)
                val serversToTest = config.servers.ifEmpty {
                    listOfNotNull(McpServerConfig.fromRawJson("custom", rawJson))
                }
                if (serversToTest.isEmpty()) {
                    testFeedback = McpServerTestResult(
                        isSuccess = false,
                        message = "No server configuration found in JSON to test"
                    )
                    return@launch
                }
                val target = serversToTest.first()
                if (target.serverUrl.isBlank()) {
                    testFeedback = McpServerTestResult(
                        isSuccess = false,
                        message = "Server URL is blank"
                    )
                    return@launch
                }
                testFeedback = onTestConnection.invoke(target)
            } catch (e: Exception) {
                testFeedback = McpServerTestResult(
                    isSuccess = false,
                    message = "Error testing server: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                isTesting = false
            }
        }
    }

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "MCP Configuration"
    ) {
        com.amaya.intelligence.ui.screens.amaya.AmayaSection("JSON Configuration") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = {
                        rawJson = it
                        testFeedback = null
                    },
                    placeholder = {
                        Text(
                            "{\n  \"mcpServers\": {\n    \"my-server\": {\n      \"serverUrl\": \"https://mcp.example.com/mcp\",\n      \"headers\": {},\n      \"enabled\": true\n    }\n  }\n}",
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 20,
                    shape = RoundedCornerShape(12.dp),
                    isError = rawJsonError != null,
                    supportingText = rawJsonError?.let { { Text(it) } },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                if (testFeedback != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (testFeedback!!.isSuccess) Color(0x1F34C759) else Color(0x1FFF3B30),
                        border = BorderStroke(1.dp, if (testFeedback!!.isSuccess) Color(0x4034C759) else Color(0x40FF3B30)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (testFeedback!!.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (testFeedback!!.isSuccess) Color(0xFF34C759) else Color(0xFFFF3B30),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (testFeedback!!.isSuccess) "Server Active (${testFeedback!!.latencyMs}ms)" else "Server Inactive / Unreachable",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (testFeedback!!.isSuccess) Color(0xFF34C759) else Color(0xFFFF3B30)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                testFeedback!!.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (testFeedback!!.toolNames.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Discovered tools (${testFeedback!!.toolCount}): " + testFeedback!!.toolNames.take(6).joinToString(", ") + if (testFeedback!!.toolCount > 6) "..." else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { testServerConnection() },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = isValid && !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Testing...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test Server", fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = { onSave(rawJson.trim()) },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = isValid && !isTesting
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Save Config",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
