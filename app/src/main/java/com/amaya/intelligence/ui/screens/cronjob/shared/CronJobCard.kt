package com.amaya.intelligence.ui.screens.cronjob.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.CronRecurringType
import com.amaya.intelligence.data.local.db.entity.CronSessionMode
import com.amaya.intelligence.ui.theme.SectionShape
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CronJobCard(
    job: CronJobEntity,
    iconBrush: Brush,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.getDefault()) }
    val timeStr = fmt.format(Date(job.triggerTimeMillis))
    val isPast = job.triggerTimeMillis < System.currentTimeMillis() && job.recurringType == CronRecurringType.ONCE

    val isDark = isSystemInDarkTheme()
    val isActive = job.isActive && !isPast

    Surface(
        shape = SectionShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) iconBrush else SolidColor(MaterialTheme.colorScheme.surfaceContainerLow)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (job.recurringType) {
                            CronRecurringType.ONCE   -> Icons.Default.Alarm
                            CronRecurringType.DAILY  -> Icons.Default.Repeat
                            CronRecurringType.WEEKLY -> Icons.Default.DateRange
                        },
                        contentDescription = null,
                        tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    job.title.ifBlank { "Reminder" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isActive)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isActive,
                    onCheckedChange = { if (!isPast) onToggle(it) },
                    enabled = !isPast
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    timeStr,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraLight),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPast) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Expired",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (job.prompt.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    job.prompt,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraLight),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        job.recurringType.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (job.sessionMode == CronSessionMode.CONTINUE) Icons.Default.Forum
                            else Icons.Default.AddComment,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (job.sessionMode == CronSessionMode.CONTINUE) "Continue" else "New chat",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
