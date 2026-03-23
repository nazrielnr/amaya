package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.domain.models.AgentSelectorItem
import com.amaya.intelligence.ui.components.shared.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    agentItems: List<AgentSelectorItem>,
    activeAgentId: String,
    isRemote: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onSelect: (AgentSelectorItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        onRefresh?.invoke()
    }

    LaunchedEffect(isRemote) {
        if (!isRemote) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5 * 60 * 1000L)
            onRefresh?.invoke()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Agent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (onRefresh != null && isRemote) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            if (agentItems.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.SmartToy, null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                        Text("No active agents",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("Enable agents in Settings → AI Agents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    }
                }
            } else {
                agentItems.forEach { item ->
                    val isSelected = item.id == activeAgentId ||
                        (activeAgentId.isBlank() && item == agentItems.firstOrNull())
                    val missingModel = item.modelId.isBlank()
                    val isDark = isSystemInDarkTheme()

                    Surface(
                        onClick = { onSelect(item) },
                        shape = RoundedCornerShape(14.dp),

                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconRes = when(item.iconType) {
                                    "gpt" -> if (isDark) com.amaya.intelligence.R.drawable.ic_chatgpt_dark else com.amaya.intelligence.R.drawable.ic_chatgpt_light
                                    "gemini" -> com.amaya.intelligence.R.drawable.ic_gemini
                                    "claude" -> com.amaya.intelligence.R.drawable.ic_claude
                                    else -> 0
                                }

                                if (missingModel) {
                                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                } else if (iconRes != 0) {
                                    Icon(
                                        painterResource(id = iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = Color.Unspecified
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        null, modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.isRemote) {
                                    Text(
                                        if (missingModel) "No model ID — edit in Settings" else item.modelId,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (missingModel) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (item.quotaStr != null || item.resetTime != null) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        if (item.quotaStr != null) {
                                            Text(
                                                "Quota: ${item.quotaStr}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item.resetTime != null) {
                                            val formattedReset = TimeUtils.parseResetTime(item.resetTime)
                                            Text(
                                                "Resets at $formattedReset",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (isSelected) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
