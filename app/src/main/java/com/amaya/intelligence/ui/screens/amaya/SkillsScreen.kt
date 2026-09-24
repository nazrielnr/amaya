package com.amaya.intelligence.ui.screens.amaya

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.amaya.intelligence.domain.skills.SkillMetadata
import com.amaya.intelligence.domain.skills.SkillStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SkillsScreen(
    state: AmayaUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onToggleSkillEnabled: (String, Boolean) -> Unit
) {
    AmayaScaffold("Skills", snackbarHostState, onNavigateBack) {
        if (state.skillSuggestions > 0) {
            AmayaSection("Review") {
                AmayaStatusRow("Skill candidates", "${state.skillSuggestions}", "Waiting for approval")
            }
        }

        val visibleSkills = state.skills.filter { it.status != SkillStatus.ARCHIVED }
        if (visibleSkills.isNotEmpty()) {
            AmayaSection("Saved Skills") {
                visibleSkills.forEachIndexed { index, skill ->
                    val status = when {
                        skill.needsReview -> "Needs review"
                        skill.status == SkillStatus.STALE -> "Stale"
                        skill.enabled -> "Available when relevant"
                        else -> "Disabled"
                    }
                    val usage = "Used ${skill.usageCount} time${if (skill.usageCount == 1) "" else "s"}"
                    AmayaSwitchRow(
                        title = skill.name,
                        subtitle = listOfNotNull(
                            skill.description.ifBlank { "Reusable workflow" },
                            usage,
                            reputationLabel(skill),
                            status
                        ).joinToString(" · "),
                        checked = skill.enabled,
                        onCheckedChange = { enabled -> onToggleSkillEnabled(skill.name, enabled) },
                        enabled = skill.status != SkillStatus.ARCHIVED
                    )
                    if (index < visibleSkills.lastIndex) AmayaDivider()
                }
            }
        } else {
            com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
                title = "No saved skills",
                subtitle = "Create a reusable workflow from chat when needed",
                icon = androidx.compose.material.icons.Icons.Default.Psychology,
                modifier = androidx.compose.ui.Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateScreenTopSpacing)
            )
        }
    }
}

/**
 * Scheme §1.4 reputation badge. `dynamicReputation` is 0.6×SuccessRate + 0.4×FrequencyNorm,
 * recomputed at end-of-session housekeeping. Brand-new skills (no outcomes yet) show "New"
 * instead of a misleading 0%; skills with recorded outcomes show the stored score.
 */
private fun reputationLabel(skill: SkillMetadata): String {
    val outcomes = skill.successCount + skill.failureCount
    return when {
        outcomes == 0 -> "New"
        else -> "Reputation ${(skill.dynamicReputation.coerceIn(0.0, 1.0) * 100).roundToInt()}%"
    }
}
