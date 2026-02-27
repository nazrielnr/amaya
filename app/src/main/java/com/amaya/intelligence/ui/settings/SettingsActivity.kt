package com.amaya.intelligence.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.theme.AmayaTheme
import com.amaya.intelligence.ui.workspace.WorkspaceActivity
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
                initial = com.amaya.intelligence.data.remote.api.AiSettings()
            )
            val isDarkTheme = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            AmayaTheme(darkTheme = isDarkTheme, accentColor = settings.accentColor) {
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
                    },
                    onNavigateToReminders = {
                        startActivity(android.content.Intent(this@SettingsActivity, CronJobActivity::class.java))
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: android.content.Context, workspacePath: String? = null) {
            val intent = android.content.Intent(context, SettingsActivity::class.java).apply {
                putExtra("workspace_path", workspacePath)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
