package com.amaya.intelligence.ui.screens.persona.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pills: List<String> = emptyList(),
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { 
                Text(
                    placeholder, 
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraLight)
                ) 
            },
            singleLine = maxLines == 1,
            maxLines = maxLines,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = if (isDark) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
        )
        if (pills.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (pill in pills) {
                    AssistChip(
                        onClick = {
                            val activeText = value.trim()
                            if (!activeText.contains(pill)) {
                                val newText = if (activeText.isEmpty()) pill else "$activeText, $pill"
                                onValueChange(newText)
                            }
                        },
                        label = { Text(pill, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = null
                    )
                }
            }
        }
    }
}
