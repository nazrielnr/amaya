package com.amaya.intelligence.ui.activities.persona.local

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.ui.screens.persona.local.LocalPersonaScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalPersonaActivity : AppCompatActivity() {

    @Inject
    lateinit var personaRepository: PersonaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                LocalPersonaScreen(
                    onNavigateBack = { finish() },
                    personaRepository = personaRepository
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, LocalPersonaActivity::class.java))
        }
    }
}
