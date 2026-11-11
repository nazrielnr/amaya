package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ConversationModeOption

/**
 * Generic conversation-mode picker. Each provider supplies its own list of
 * [options] through its `IdeProvider.conversationModes` declaration, so the UI
 * stays provider-neutral.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationModeSheet(
    options: List<ConversationModeOption>,
    selectedId: String?,
    title: String = stringResource(R.string.conversation_mode),
    onSelect: (ConversationModeOption) -> Unit,
    onDismiss: () -> Unit
) {
    StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = title
    ) {
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.this_provider_has_no_conversation_modes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            options.forEach { option ->
                ConversationModeItem(
                    option = option,
                    isSelected = option.id == selectedId,
                    onClick = { dismiss { onSelect(option) } }
                )
            }
        }
    }
}

@Composable
private fun ConversationModeItem(
    option: ConversationModeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (option.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
