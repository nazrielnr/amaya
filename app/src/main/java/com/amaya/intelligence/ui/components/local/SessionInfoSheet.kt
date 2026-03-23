package com.amaya.intelligence.ui.components.local

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.ui.components.shared.ContextWindowUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoSheet(
    totalTokens: Int,
    activeModel: String,
    activeReminderCount: Int,
    onDismiss: () -> Unit
) {
    val contextWindow = ContextWindowUtils.getContextWindow(activeModel)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Session Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            HorizontalDivider()

            SessionInfoRow(
                icon = Icons.Default.AutoAwesome,
                label = "Tokens used",
                value = if (totalTokens > 0) ContextWindowUtils.formatTokenCount(totalTokens) else "0",
                valueColor = when {
                    totalTokens > 100_000 -> MaterialTheme.colorScheme.error
                    totalTokens > 50_000  -> Color(0xFFFF9800)
                    else                  -> MaterialTheme.colorScheme.onSurface
                }
            )

            SessionInfoRow(
                icon = Icons.Default.DataUsage,
                label = "Context window",
                value = contextWindow
            )

            SessionInfoRow(
                icon = Icons.Default.Alarm,
                label = "Active reminders",
                value = if (activeReminderCount > 0) "$activeReminderCount active" else "None",
                valueColor = if (activeReminderCount > 0) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SessionInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
