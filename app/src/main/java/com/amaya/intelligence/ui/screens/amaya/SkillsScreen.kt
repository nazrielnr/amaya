package com.amaya.intelligence.ui.screens.amaya

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.amaya.intelligence.domain.skills.SkillStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun SkillsScreen(
    state: AmayaUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onToggleSkillEnabled: (String, Boolean) -> Unit
) {
    AmayaScaffold(stringResource(R.string.skills), snackbarHostState, onNavigateBack) {
        if (state.skillSuggestions > 0) {
            AmayaSection(stringResource(R.string.review)) {
                AmayaStatusRow(stringResource(R.string.skill_candidates), "${state.skillSuggestions}", stringResource(R.string.waiting_for_approval))
            }
        }

        val visibleSkills = state.skills.filter { it.status != SkillStatus.ARCHIVED }
        if (visibleSkills.isNotEmpty()) {
            AmayaSection(stringResource(R.string.saved_skills)) {
                visibleSkills.forEachIndexed { index, skill ->
                    val status = when {
                        skill.needsReview -> stringResource(R.string.needs_review)
                        skill.status == SkillStatus.STALE -> stringResource(R.string.stale)
                        skill.enabled -> stringResource(R.string.available_when_relevant)
                        else -> stringResource(R.string.disabled)
                    }
                    val usage = pluralStringResource(R.plurals.skill_usage_count, skill.usageCount, skill.usageCount)
                    AmayaSwitchRow(
                        title = skill.name,
                        subtitle = "${skill.description.ifBlank { stringResource(R.string.reusable_workflow) }} · $usage · $status",
                        checked = skill.enabled,
                        onCheckedChange = { enabled -> onToggleSkillEnabled(skill.name, enabled) },
                        enabled = skill.status != SkillStatus.ARCHIVED
                    )
                    if (index < visibleSkills.lastIndex) AmayaDivider()
                }
            }
        } else {
            com.amaya.intelligence.ui.components.shared.SettingsEmptyState(
                title = stringResource(R.string.no_saved_skills),
                subtitle = stringResource(R.string.create_a_reusable_workflow_from_chat_when_needed),
                icon = androidx.compose.material.icons.Icons.Default.Psychology,
                modifier = androidx.compose.ui.Modifier.padding(top = AmayaGroupedSettingsTokens.emptyStateScreenTopSpacing)
            )
        }
    }
}
