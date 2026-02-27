package com.amaya.intelligence.ui.workspace

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.browser.ProjectBrowserScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone Activity for Workspace Browser.
 * Android OS handles all transition animations natively at the system level.
 */
@AndroidEntryPoint
class WorkspaceActivity : ComponentActivity() {

    @javax.inject.Inject
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
            AmayaTheme(darkTheme = isDarkTheme) {
                ProjectBrowserScreen(
                    onWorkspaceSelected = { path ->
                        val resultIntent = Intent().apply {
                            putExtra("selected_workspace_path", path)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        const val REQUEST_CODE = 1001
        const val RESULT_KEY = "selected_workspace_path"

        fun start(activity: Activity) {
            val intent = Intent(activity, WorkspaceActivity::class.java)
            activity.startActivity(intent)
        }

        fun startForResult(activity: Activity) {
            val intent = Intent(activity, WorkspaceActivity::class.java)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CODE)
        }
    }
}
