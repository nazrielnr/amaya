package com.amaya.intelligence.ui.activities.settings.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.settings.remote.RemoteSettingsScreen
import com.amaya.intelligence.ui.theme.AmayaTheme

class RemoteSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                RemoteSettingsScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, RemoteSettingsActivity::class.java))
        }
    }
}
