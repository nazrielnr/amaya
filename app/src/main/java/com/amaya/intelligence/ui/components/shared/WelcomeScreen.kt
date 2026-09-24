package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Welcome state shown when a chat has no messages.
 *
 * [header] is an optional slot rendered above the greeting — used by remote
 * sessions (Windows Bridge, Antigravity) to surface a "connected" pill without
 * each caller re-implementing the layout.
 *
 * [showWorkspaceChip] hides the "Select Workspace" / current-workspace chip for
 * sessions that don't use workspaces (e.g. Windows Bridge).
 */
@Composable
fun WelcomeScreen(
    onPromptClick: (String) -> Unit,
    currentWorkspace: String?,
    onNewProjectClick: () -> Unit,
    workspaces: List<com.amaya.intelligence.domain.models.RemoteWorkspace> = emptyList(),
    onWorkspaceClick: () -> Unit = {},
    showWorkspaceChip: Boolean = true,
    header: (@Composable () -> Unit)? = null
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (header != null) {
            header()
            Spacer(Modifier.height(16.dp))
        }

        Text(
            greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        // Suggested prompts so a fresh conversation is not a blank page: tapping a
        // pill fills the composer ("Summarize", "Write code", ...). This used to be
        // dead code — ScrollablePills was defined but never rendered, so the empty
        // chat state showed only a greeting with no way in.
        Spacer(Modifier.height(24.dp))
        ScrollablePills(onPromptClick = onPromptClick)
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
        PillItem(Icons.Default.Email, "Draft email", "Draft an email to "),
        PillItem(Icons.Default.Lightbulb, "Explain", "Explain this concept"),
        PillItem(Icons.Default.Code, "Write code", "Write a script"),
        PillItem(Icons.Default.Edit, "Rewrite", "Rewrite this text"),
        PillItem(Icons.Default.Search, "Research", "Research this topic"),
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
