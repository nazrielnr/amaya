package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.tools.ConfirmationRequest

@Composable
fun ConfirmationDialog(request: ConfirmationRequest, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp)) },
        title = { Text("Approval Required", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                Text(request.reason, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        request.details,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = CircleShape) { Text("Allow Action") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Deny") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}
