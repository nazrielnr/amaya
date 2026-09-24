package com.amaya.intelligence.ui.screens.selfimprovement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalStatus
import com.amaya.intelligence.domain.memory.PendingProposalType

@Composable
fun PendingProposalCard(
    proposal: PendingProposal,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(proposal.type.friendlyType()) })
                AssistChip(onClick = {}, label = { Text(proposal.status.friendlyStatus()) })
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(proposal.title.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    proposal.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Suggestion", style = MaterialTheme.typography.labelLarge)
                    Text(proposal.content.take(420), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DetailPill("Destination", proposal.target, Modifier.weight(1f))
                DetailPill("Confidence", "${(proposal.confidence * 100).toInt()}%", Modifier.weight(1f))
                DetailPill("Evidence", proposal.sourceSessionIds.distinct().size.toString(), Modifier.weight(1f))
            }
            if (proposal.evidence.isNotEmpty()) {
                Text(
                    proposal.evidence.take(3).joinToString("\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Approve only accepts the suggestion. Apply makes it active for the next chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proposal.status == PendingProposalStatus.PENDING) {
                    Button(onClick = { onApprove(proposal.id) }) { Text("Approve") }
                    OutlinedButton(onClick = { onReject(proposal.id) }) { Text("Reject") }
                } else if (proposal.status == PendingProposalStatus.APPROVED) {
                    Button(onClick = { onApply(proposal.id) }) { Text("Apply now") }
                    OutlinedButton(onClick = { onReject(proposal.id) }) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
private fun DetailPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun PendingProposalType.friendlyType(): String = when (this) {
    PendingProposalType.USER_PROFILE -> "User preference"
    PendingProposalType.WORKSPACE_FACT -> "Project memory"
    PendingProposalType.DECISION -> "Project decision"
    PendingProposalType.SKILL_CREATE -> "New skill"
    PendingProposalType.SKILL_PATCH -> "Skill update"
    PendingProposalType.SKILL_UPDATE -> "Skill rewrite"
}

private fun PendingProposalStatus.friendlyStatus(): String = when (this) {
    PendingProposalStatus.PENDING -> "Needs review"
    PendingProposalStatus.APPROVED -> "Approved"
    PendingProposalStatus.REJECTED -> "Rejected"
    PendingProposalStatus.APPLIED -> "Applied"
    PendingProposalStatus.EXPIRED -> "Expired"
}
