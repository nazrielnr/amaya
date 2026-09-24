package com.amaya.intelligence.ui.screens.amaya

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalType

private data class IosReviewColors(
    val groupSurface: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val separator: Color
)

@Composable
private fun iosReviewColors(): IosReviewColors {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        IosReviewColors(
            groupSurface = Color(0xFF1C1C1E),
            border = Color.White.copy(alpha = 0.10f),
            primaryText = Color(0xFFF2F2F7),
            secondaryText = Color(0xFFEBEBF5).copy(alpha = 0.60f),
            separator = Color.White.copy(alpha = 0.10f)
        )
    } else {
        IosReviewColors(
            groupSurface = Color.White,
            border = Color.Black.copy(alpha = 0.08f),
            primaryText = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF3C3C43).copy(alpha = 0.62f),
            separator = Color(0xFF3C3C43).copy(alpha = 0.13f)
        )
    }
}

@Composable
fun ReviewScreen(
    state: AmayaUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: (String) -> Unit
) {
    val colors = iosReviewColors()
    AmayaScaffold("Review", snackbarHostState, onNavigateBack) {
        if (state.pendingProposals.isEmpty()) {
            AmayaSection("Queue") {
                AmayaStatusRow("Pending suggestions", "0")
            }
        } else {
            val grouped = state.pendingProposals.groupBy { it.type.reviewGroup() }
            grouped.forEach { (title, proposals) ->
                AmayaSection(title) {
                    proposals.forEachIndexed { index, proposal ->
                        SuggestionCard(proposal, onSave, onDismiss, colors = colors)
                        if (index < proposals.lastIndex) AmayaDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    proposal: PendingProposal,
    onSave: (String) -> Unit,
    onDismiss: (String) -> Unit,
    colors: IosReviewColors
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.groupSurface,
        border = BorderStroke(0.7.dp, colors.border),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                proposal.type.friendlyTitle(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 19.sp
                ),
                color = colors.primaryText
            )
            Text(
                "\"${proposal.content.take(220)}\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = colors.secondaryText,
                maxLines = 3
            )
            Text(
                "Save to: ${proposal.type.destinationLabel()} · ${proposal.reason}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = colors.secondaryText,
                maxLines = 2
            )
            if (proposal.evidence.isNotEmpty()) {
                Text(
                    proposal.evidence.take(2).joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    maxLines = 4
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(proposal.id) }) { Text(if (proposal.type.isSkillType()) "Save Skill" else "Save") }
                OutlinedButton(onClick = { onDismiss(proposal.id) }) { Text("Dismiss") }
            }
        }
    }
}

private fun PendingProposalType.reviewGroup(): String = when {
    isSkillType() -> "Skills"
    this == PendingProposalType.USER_PROFILE -> "User Memory"
    this == PendingProposalType.WORKSPACE_FACT -> "Project Memory"
    else -> "Memory"
}

private fun PendingProposalType.friendlyTitle(): String = when (this) {
    PendingProposalType.USER_PROFILE -> "User preference"
    PendingProposalType.WORKSPACE_FACT -> "Project fact"
    PendingProposalType.DECISION -> "Project decision"
    PendingProposalType.SKILL_CREATE -> "New reusable workflow"
    PendingProposalType.SKILL_PATCH -> "Skill improvement"
    PendingProposalType.SKILL_UPDATE -> "Skill update"
}

private fun PendingProposalType.destinationLabel(): String = when (this) {
    PendingProposalType.USER_PROFILE -> "Memory > User Profile"
    PendingProposalType.WORKSPACE_FACT -> "Memory > Project Memory"
    PendingProposalType.DECISION -> "Memory > Project Memory"
    PendingProposalType.SKILL_CREATE,
    PendingProposalType.SKILL_PATCH,
    PendingProposalType.SKILL_UPDATE -> "Skills"
}
