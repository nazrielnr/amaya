package com.amaya.intelligence.ui.activities.settings.local

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.screens.settings.local.LocalSettingsScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.amaya.intelligence.ui.activities.persona.local.LocalPersonaActivity
import com.amaya.intelligence.ui.activities.agent.local.LocalAgentActivity
import com.amaya.intelligence.ui.activities.mcp.local.LocalMcpActivity
import com.amaya.intelligence.ui.activities.cronjob.local.LocalCronJobActivity
import com.amaya.intelligence.ui.activities.project.local.LocalProjectActivity

@AndroidEntryPoint
class LocalSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    private var currentWorkspace: String? = null
    private var navigateToWorkspace: Boolean = false
    private var navigateToPersona: Boolean = false
    private var navigateToAgents: Boolean = false
    private var navigateToReminders: Boolean = false
    private var navigateToMcp: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentWorkspace = intent.getStringExtra("current_workspace")
        navigateToWorkspace = intent.getBooleanExtra("navigate_to_workspace", false)
        navigateToPersona = intent.getBooleanExtra("navigate_to_persona", false)
        navigateToAgents = intent.getBooleanExtra("navigate_to_agents", false)
        navigateToReminders = intent.getBooleanExtra("navigate_to_reminders", false)
        navigateToMcp = intent.getBooleanExtra("navigate_to_mcp", false)
        
        enableEdgeToEdge()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LocalProjectActivity.REQUEST_CODE && resultCode == RESULT_OK) {
            val path = data?.getStringExtra(LocalProjectActivity.RESULT_KEY)
            if (path != null) {
                setResult(RESULT_OK, android.content.Intent().apply {
                    putExtra(LocalProjectActivity.RESULT_KEY, path)
                    putExtra("navigate_to_chat", true)
                })
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setContent {
            AmayaTheme {
                LocalSettingsScreen(
                    onNavigateBack = { finish() },
                    currentWorkspace = currentWorkspace,
                    onNavigateToWorkspace = { 
                        LocalProjectActivity.startForResult(this)
                    },
                    aiSettingsManager = aiSettingsManager,
                    onNavigateToPersona = {
                        LocalPersonaActivity.start(this)
                    },
                    onNavigateToAgents = {
                        LocalAgentActivity.start(this)
                    },
                    onNavigateToReminders = {
                        LocalCronJobActivity.start(this)
                    },
                    onNavigateToMcp = {
                        LocalMcpActivity.start(this)
                    }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity, currentWorkspace: String? = null) {
            activity.startActivityForResult(
                android.content.Intent(activity, LocalSettingsActivity::class.java)
                    .putExtra("current_workspace", currentWorkspace),
                REQUEST_CODE
            )
        }

        const val REQUEST_CODE = 1002
    }
}
