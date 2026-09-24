package com.amaya.intelligence.ui.screens.recommendations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationPriority
import com.amaya.intelligence.domain.memory.RecommendationStatus

private enum class RecommendationFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    VERIFIED("Verified"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}

@Composable
fun RecommendationsScreen(
    state: RecommendationsUiState,
    onAccept: (String) -> Unit,
    onStart: (String) -> Unit,
    onVerify: (String, String) -> Unit,
    onComplete: (String) -> Unit,
    onArchive: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(RecommendationFilter.ALL) }
    var pendingVerify by remember { mutableStateOf<Recommendation?>(null) }
    var evidenceText by remember { mutableStateOf("") }

    val filtered = state.recommendations.filter { it.matches(selectedFilter) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecommendationFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                item { Text("Loading…", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (filtered.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No recommendations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Evidence-grounded next steps appear here: after failed builds, blocker turns, or any suggestion made with the recommendation tool.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { recommendation ->
                    RecommendationCard(
                        recommendation = recommendation,
                        onAccept = onAccept,
                        onStart = onStart,
                        onVerify = { pendingVerify = recommendation },
                        onComplete = onComplete,
                        onArchive = onArchive
                    )
                }
            }
        }
    }

    pendingVerify?.let { recommendation ->
        VerifyEvidenceDialog(
            title = recommendation.title,
            verificationRule = recommendation.verificationRule,
            evidence = evidenceText,
            onEvidenceChange = { evidenceText = it },
            onConfirm = {
                if (evidenceText.isNotBlank()) {
                    onVerify(recommendation.id, evidenceText)
                    evidenceText = ""
                    pendingVerify = null
                }
            },
            onDismiss = {
                evidenceText = ""
                pendingVerify = null
            }
        )
    }
}

@Composable
private fun RecommendationCard(
    recommendation: Recommendation,
    onAccept: (String) -> Unit,
    onStart: (String) -> Unit,
    onVerify: (Recommendation) -> Unit,
    onComplete: (String) -> Unit,
    onArchive: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(recommendation.status)
                PriorityChip(recommendation.priority)
            }
            Text(recommendation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (recommendation.rationale.isNotBlank()) {
                Text(
                    recommendation.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recommendation.verificationRule.isNotBlank()) {
                Text(
                    "Verify rule: ${recommendation.verificationRule}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recommendation.evidence.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Evidence", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    recommendation.evidence.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(top = 2.dp).size(14.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val memoryCount = recommendation.relatedMemoryIds.size
            val skillCount = recommendation.relatedSkillIds.size
            if (memoryCount > 0 || skillCount > 0) {
                Text(
                    "Linked to $memoryCount memor${if (memoryCount == 1) "y" else "ies"} · $skillCount skill${if (skillCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val actions = recommendation.availableActions()
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (RecommendationAction.ACCEPT in actions) {
                        Button(onClick = { onAccept(recommendation.id) }) { Text("Accept") }
                    }
                    if (RecommendationAction.START in actions) {
                        Button(onClick = { onStart(recommendation.id) }) { Text("Start") }
                    }
                    if (RecommendationAction.VERIFY in actions) {
                        OutlinedButton(onClick = { onVerify(recommendation) }) { Text("Verify") }
                    }
                    if (RecommendationAction.COMPLETE in actions) {
                        OutlinedButton(onClick = { onComplete(recommendation.id) }) { Text("Mark done") }
                    }
                    if (RecommendationAction.ARCHIVE in actions) {
                        TextButton(onClick = { onArchive(recommendation.id) }) { Text("Archive") }
                    }
                }
            }
        }
    }
}

private enum class RecommendationAction { ACCEPT, START, VERIFY, COMPLETE, ARCHIVE }

private fun Recommendation.availableActions(): List<RecommendationAction> = when (status) {
    RecommendationStatus.SUGGESTED -> listOf(RecommendationAction.ACCEPT, RecommendationAction.ARCHIVE)
    RecommendationStatus.ACCEPTED -> listOf(RecommendationAction.START, RecommendationAction.VERIFY, RecommendationAction.COMPLETE, RecommendationAction.ARCHIVE)
    RecommendationStatus.IN_PROGRESS -> listOf(RecommendationAction.VERIFY, RecommendationAction.COMPLETE, RecommendationAction.ARCHIVE)
    RecommendationStatus.VERIFIED -> listOf(RecommendationAction.COMPLETE, RecommendationAction.ARCHIVE)
    RecommendationStatus.COMPLETED, RecommendationStatus.ARCHIVED -> emptyList()
}

@Composable
private fun StatusChip(status: RecommendationStatus) {
    val (label, container) = when (status) {
        RecommendationStatus.SUGGESTED -> "Suggested" to MaterialTheme.colorScheme.tertiaryContainer
        RecommendationStatus.ACCEPTED -> "Accepted" to MaterialTheme.colorScheme.primaryContainer
        RecommendationStatus.IN_PROGRESS -> "In progress" to MaterialTheme.colorScheme.secondaryContainer
        RecommendationStatus.VERIFIED -> "Verified" to Color(0xFF2E7D32).copy(alpha = 0.16f)
        RecommendationStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.surfaceVariant
        RecommendationStatus.ARCHIVED -> "Archived" to MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        RecommendationStatus.VERIFIED -> Color(0xFF1B5E20)
        RecommendationStatus.COMPLETED, RecommendationStatus.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
private fun PriorityChip(priority: RecommendationPriority) {
    val (label, color) = when (priority) {
        RecommendationPriority.HIGH -> "High" to Color(0xFFD93025)
        RecommendationPriority.MEDIUM -> "Medium" to Color(0xFFB26A00)
        RecommendationPriority.LOW -> "Low" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = {
            Box(Modifier.padding(start = 8.dp).size(8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    )
}

@Composable
private fun VerifyEvidenceDialog(
    title: String,
    verificationRule: String,
    evidence: String,
    onEvidenceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify with evidence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prove \"$title\" is done with evidence the system can check.", style = MaterialTheme.typography.bodyMedium)
                if (verificationRule.isNotBlank()) {
                    Text("Evidence must contain: ${verificationRule}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = evidence,
                    onValueChange = onEvidenceChange,
                    placeholder = { Text("e.g. Build successful — arm64-v8a release APK produced") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = evidence.isNotBlank()) { Text("Verify") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Recommendation.matches(filter: RecommendationFilter): Boolean = when (filter) {
    RecommendationFilter.ALL -> true
    RecommendationFilter.ACTIVE -> status in Recommendation.ACTIVE_STATUSES
    RecommendationFilter.VERIFIED -> status == RecommendationStatus.VERIFIED
    RecommendationFilter.COMPLETED -> status == RecommendationStatus.COMPLETED
    RecommendationFilter.ARCHIVED -> status == RecommendationStatus.ARCHIVED
}
