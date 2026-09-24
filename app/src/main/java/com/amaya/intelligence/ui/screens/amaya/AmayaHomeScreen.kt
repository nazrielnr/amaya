package com.amaya.intelligence.ui.screens.amaya

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

@Composable
fun AmayaHomeScreen(
    state: AmayaUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onMemory: () -> Unit,
    onReview: () -> Unit,
    onSkills: () -> Unit,
    onRecommendations: () -> Unit
) {
    AmayaScaffold("Amaya", snackbarHostState, onNavigateBack) {
        if (state.pendingProposals.isNotEmpty()) {
            AmayaSection("Needs Attention") {
                AmayaNavigationRow(
                    icon = Icons.Default.SettingsSuggest,
                    title = "${state.pendingProposals.size} suggestion${if (state.pendingProposals.size == 1) "" else "s"} need review",
                    subtitle = "Save or dismiss memory and context updates",
                    onClick = onReview
                )
            }
        }
        AmayaSection("Intelligence") {
            AmayaNavigationRow(Icons.Default.TaskAlt, "Recommendations", "Evidence-grounded next steps with verification", onRecommendations)
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Memory, "Memory", "${state.totalMemoryCount} saved items", onMemory)
            AmayaDivider()
            AmayaNavigationRow(Icons.Default.Psychology, "Skills", "${state.enabledSkills} enabled workflows", onSkills)
        }
    }
}
