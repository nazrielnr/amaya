package com.amaya.intelligence.ui.screens.persona.remote

import androidx.compose.runtime.Composable
import com.amaya.intelligence.ui.screens.shared.ComingSoonScreen

@Composable
fun RemotePersonaScreen(
    onNavigateBack: () -> Unit
) {
    ComingSoonScreen(
        featureName = "Persona",
        onNavigateBack = onNavigateBack
    )
}
