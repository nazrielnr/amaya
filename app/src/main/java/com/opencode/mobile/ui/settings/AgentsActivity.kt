package com.opencode.mobile.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.opencode.mobile.data.remote.api.AiSettingsManager
import com.opencode.mobile.ui.theme.OpenCodeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AgentsActivity : ComponentActivity() {

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by aiSettingsManager.settingsFlow.collectAsState(
                initial = com.opencode.mobile.data.remote.api.AiSettings()
            )
            val isDarkTheme = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            OpenCodeTheme(darkTheme = isDarkTheme, accentColor = settings.accentColor) {
                AgentsScreen(
                    onNavigateBack = { finish() },
                    aiSettingsManager = aiSettingsManager
                )
            }
        }
    }

    companion object {
        fun start(activity: Activity) {
            activity.startActivity(Intent(activity, AgentsActivity::class.java))
        }
    }
}
