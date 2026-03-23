package com.amaya.intelligence.ui.components.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

@Composable
fun RemoteWelcomeScreen(
    onPromptClick: (String) -> Unit,
    serverName: String?,
    onConnectClick: () -> Unit = {}
) {
    val greetings = listOf(
        "Ready to connect?",
        "Remote session awaits",
        "Connect to continue",
        "Choose a server",
        "Start your remote session"
    )
    val now = remember { LocalDateTime.now() }
    val greeting = greetings[(now.dayOfYear + now.hour) % greetings.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = serverName ?: "No server connected",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (serverName == null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConnectClick,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Connect to Server")
            }
        }
    }
}
