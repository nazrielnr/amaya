package com.amaya.intelligence.ui.screens.mcp.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun McpFormatGuide(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "MCP Server Guide",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Amaya supports HTTP-based MCP servers only (no npx/stdio). The server must accept JSON-RPC 2.0 POST requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Required JSON-RPC methods:", style = MaterialTheme.typography.labelMedium)
            Text(
                "• tools/list  - discover available tools\n• tools/call  - invoke a specific tool",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Config JSON format:", style = MaterialTheme.typography.labelMedium)
            Text(
                "{\n" +
                "  \"mcpServers\": {\n" +
                "    \"my-server\": {\n" +
                "      \"serverUrl\": \"https://mcp.example.com/mcp\",\n" +
                "      \"headers\": {\n" +
                "        \"Authorization\": \"Bearer YOUR_TOKEN\"\n" +
                "      },\n" +
                "      \"enabled\": true\n" +
                "    }\n" +
                "  }\n" +
                "}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
