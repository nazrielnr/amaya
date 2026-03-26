package com.amaya.intelligence.ui.screens.agent.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.remote.api.AgentConfig
import com.amaya.intelligence.ui.components.shared.AgentIcon
import com.amaya.intelligence.ui.theme.SectionShape

@Composable
fun AgentCard(
    config: AgentConfig,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        onClick = onClick,
        shape = SectionShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar badge — neutral background like Select Agent
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (config.enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val iconSpec = AgentIcon.resolve(config.modelId, isDark)

                if (iconSpec != null) {
                    Icon(
                        painterResource(id = iconSpec.resId),
                        contentDescription = null,
                        tint = if (iconSpec.tintable) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (config.enabled) 0.9f else 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.name.ifBlank { "Unnamed Agent" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (config.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (config.modelId.isNotBlank()) append(config.modelId)
                        else append("No model set")
                        append(" · ")
                        append(config.providerType.lowercase().replaceFirstChar { it.uppercaseChar() })
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Enable/Disable toggle — only if model is set
            if (config.modelId.isNotBlank()) {
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}
