package com.amaya.intelligence.ui.screens.mcp.remote

import androidx.compose.runtime.Composable
import com.amaya.intelligence.ui.screens.shared.ComingSoonScreen

@Composable
fun RemoteMcpScreen(
    onNavigateBack: () -> Unit
) {
    ComingSoonScreen(
        featureName = "MCP Servers",
        onNavigateBack = onNavigateBack
    )
}
