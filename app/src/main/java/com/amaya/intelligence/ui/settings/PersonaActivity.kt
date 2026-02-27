package com.amaya.intelligence.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Standalone Activity for Persona settings to leverage native OS transitions
 * and predictive back gestures.
 */
@AndroidEntryPoint
class PersonaActivity : ComponentActivity() {

    @Inject
    lateinit var personaRepository: PersonaRepository

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by aiSettingsManager.settingsFlow.collectAsState(
                initial = com.amaya.intelligence.data.remote.api.AiSettings()
            )
            val isDarkTheme = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AmayaTheme(darkTheme = isDarkTheme, accentColor = settings.accentColor) {
                PersonaScreen(
                    onNavigateBack = { finish() },
                    personaRepository = personaRepository
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            val intent = android.content.Intent(activity, PersonaActivity::class.java)
            activity.startActivity(intent)
        }
    }
}
