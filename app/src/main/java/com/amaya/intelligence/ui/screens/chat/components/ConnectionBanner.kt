package com.amaya.intelligence.ui.screens.chat.components

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ConnectionState

/**
 * Banner showing connection status for remote sessions.
 * Displayed when remote mode is active but not connected.
 */
@Composable
fun ConnectionBanner(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = connectionState != ConnectionState.CONNECTED,
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connectionState == ConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (connectionState == ConnectionState.CONNECTING)
                        stringResource(R.string.reconnecting_to_server)
                    else
                        stringResource(R.string.disconnected_from_server_attempting_to_reconnect),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
