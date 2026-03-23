package com.amaya.intelligence.ui.screens.cronjob.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.ui.screens.settings.shared.SettingsSectionCard

@Composable
fun CronJobList(
    jobs: List<CronJobEntity>,
    iconPalettes: List<Brush>,
    onToggle: (CronJobEntity, Boolean) -> Unit,
    onDelete: (CronJobEntity) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topPadding,
            bottom = 100.dp
        )
    ) {
        if (jobs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No reminders yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Tap + to schedule a reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }

        if (jobs.isNotEmpty()) {
            item {
                SettingsSectionCard(title = "Automation") {
                    jobs.forEachIndexed { index, job ->
                        val paletteIndex = jobs.indexOf(job) % iconPalettes.size
                        CronJobCard(
                            job = job,
                            iconBrush = iconPalettes[paletteIndex],
                            onToggle = { active -> onToggle(job, active) },
                            onDelete = { onDelete(job) }
                        )
                        if (index < jobs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 78.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}
