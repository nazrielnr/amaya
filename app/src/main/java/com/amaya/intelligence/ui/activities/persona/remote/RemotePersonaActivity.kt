package com.amaya.intelligence.ui.activities.persona.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.persona.remote.RemotePersonaScreen
import com.amaya.intelligence.ui.theme.AmayaTheme

class RemotePersonaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                RemotePersonaScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, RemotePersonaActivity::class.java))
        }
    }
}
