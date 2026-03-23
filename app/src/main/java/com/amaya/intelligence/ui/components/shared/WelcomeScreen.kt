package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

@Composable
fun WelcomeScreen(
    onPromptClick: (String) -> Unit,
    currentWorkspace: String?,
    onNewProjectClick: () -> Unit,
    workspaces: List<com.amaya.intelligence.domain.models.RemoteWorkspace> = emptyList(),
    onWorkspaceClick: () -> Unit = {}
) {
    val greetings = listOf(
        "What's on your mind?",
        "Ready when you are",
        "Let's get started",
        "How can I help?",
        "What should we tackle?",
        "Ask me anything",
        "Let's figure it out"
    )
    val now = remember { LocalDateTime.now() }
    val greeting = greetings[(now.dayOfYear + now.hour) % greetings.size]

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(24.dp))

        if (currentWorkspace != null) {
            val folderName = currentWorkspace.substringAfterLast("/").substringAfterLast("\\")
            Surface(
                onClick = onWorkspaceClick,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable { onWorkspaceClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        folderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ── ScrollablePills ──────────────────────────────────────────────────────────

@Composable
fun ScrollablePills(
    onPromptClick: (String) -> Unit
) {
    data class PillItem(val icon: ImageVector, val label: String, val prompt: String)

    val pills = listOf(
        PillItem(Icons.Default.Description, "Summarize", "Summarize this document"),
        PillItem(Icons.Default.Email, "Draft email", "Draft an email"),
        PillItem(Icons.Default.Lightbulb, "Explain", "Explain this concept"),
        PillItem(Icons.Default.Code, "Write code", "Write a script"),
        PillItem(Icons.Default.Edit, "Rewrite", "Rewrite this text"),
        PillItem(Icons.Default.Search, "Research", "Research this topic"),
        PillItem(Icons.Default.Translate, "Translate", "Translate this text"),
        PillItem(Icons.Default.CheckCircle, "Review", "Review this code")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { pill ->
            Surface(
                onClick = { onPromptClick(pill.prompt) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(pill.icon, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pill.label, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
