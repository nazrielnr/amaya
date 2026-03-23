package com.amaya.intelligence.ui.screens.settings.remote

import androidx.compose.runtime.Composable
import com.amaya.intelligence.ui.screens.shared.ComingSoonScreen

@Composable
fun RemoteSettingsScreen(
    onNavigateBack: () -> Unit
) {
    ComingSoonScreen(
        featureName = "Settings",
        onNavigateBack = onNavigateBack
    )
}
