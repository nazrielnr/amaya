package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Prominent dialog for an in-flight `ask_user` question. The tool loop is suspended until the
 * user answers (free text or a quick option chip) or dismisses. Complements the inline question
 * in [ToolCallCard] by staying visible even when the tool card is scrolled off-screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskUserClarificationDialog(
    question: String,
    options: List<String>,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var answer by remember(question) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Question from AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question, style = MaterialTheme.typography.bodyLarge)
                if (options.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        options.take(6).forEach { option ->
                            FilterChip(
                                selected = answer == option,
                                onClick = { answer = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type your answer…") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAnswer(answer.trim()) },
                enabled = answer.isNotBlank()
            ) { Text("Answer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )
}
