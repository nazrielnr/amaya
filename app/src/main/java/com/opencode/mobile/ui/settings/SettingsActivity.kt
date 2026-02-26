package com.opencode.mobile.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.opencode.mobile.data.remote.api.AiSettingsManager
import com.opencode.mobile.ui.theme.OpenCodeTheme
import com.opencode.mobile.ui.workspace.WorkspaceActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Standalone Activity for Settings.
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentWorkspace = intent.getStringExtra("workspace_path")

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
                SettingsScreen(
                    onNavigateBack = { finish() },
                    currentWorkspace = currentWorkspace,
                    onNavigateToWorkspace = {
                        WorkspaceActivity.start(this@SettingsActivity)
                    },
                    aiSettingsManager = aiSettingsManager,
                    onNavigateToPersona = {
                        PersonaActivity.start(this@SettingsActivity)
                    },
                    onNavigateToAgents = {
                        AgentsActivity.start(this@SettingsActivity)
                    }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity, workspacePath: String? = null) {
            val intent = android.content.Intent(activity, SettingsActivity::class.java).apply {
                putExtra("workspace_path", workspacePath)
            }
            activity.startActivity(intent)
        }
    }
}
