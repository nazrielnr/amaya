package com.amaya.intelligence.ui.components.local

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
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

import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionInfoSheet(
    totalTokens: Int,
    activeModel: String,
    activeReminderCount: Int,
    onDismiss: () -> Unit,
    inputTokens: Int = 0,
    outputTokens: Int = 0,
    providerId: String? = null,
    providerNameOverride: String? = null,
    modelDisplayNameOverride: String? = null
) {
    StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.session_info)
    ) {
        SessionInfoRow(
            icon = Icons.Default.Error,
            label = stringResource(R.string.tokens_used),
            value = if (totalTokens > 0) ContextWindowUtils.formatTokenCount(totalTokens) else "0"
        )

        if (inputTokens > 0 || outputTokens > 0) {
            SessionInfoRow(
                icon = Icons.AutoMirrored.Filled.Login,
                label = stringResource(R.string.input_tokens),
                value = ContextWindowUtils.formatTokenCount(inputTokens)
            )
            SessionInfoRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.output_tokens),
                value = ContextWindowUtils.formatTokenCount(outputTokens)
            )
        }

        SessionInfoRow(
            icon = Icons.Default.AccountTree,
            label = stringResource(R.string.provider),
            value = providerNameOverride ?: providerId ?: stringResource(R.string.unknown)
        )

        SessionInfoRow(
            icon = Icons.Default.Psychology,
            label = stringResource(R.string.model),
            value = modelDisplayNameOverride ?: activeModel
        )

        SessionInfoRow(
            icon = Icons.Default.Alarm,
            label = stringResource(R.string.active_reminders),
            value = if (activeReminderCount > 0) pluralStringResource(R.plurals.active_reminder_count, activeReminderCount, activeReminderCount) else stringResource(R.string.none),
            valueColor = if (activeReminderCount > 0) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
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
