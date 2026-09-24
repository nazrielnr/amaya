package com.amaya.intelligence.ui.activities.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.domain.terminal.TerminalSessionManager
import com.amaya.intelligence.ui.activities.settings.local.LocalTerminalSettingsActivity
import com.amaya.intelligence.ui.screens.terminal.TerminalScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TerminalActivity : AppCompatActivity() {

    @Inject lateinit var sessionManager: TerminalSessionManager
    @Inject lateinit var sandboxManager: LinuxSandboxManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val workspaceDir = intent.getStringExtra(EXTRA_WORKSPACE_DIR)
        sessionManager.setWorkspace(workspaceDir)

        setContent {
            AmayaTheme {
                TerminalScreen(
                    sessionManager = sessionManager,
                    sandboxManager = sandboxManager,
                    onNavigateBack = { finish() },
                    onNavigateToSettings = {
                        LocalTerminalSettingsActivity.start(this)
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_WORKSPACE_DIR = "extra_workspace_dir"

        fun start(context: Context, workspaceDir: String? = null) {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                workspaceDir?.let { putExtra(EXTRA_WORKSPACE_DIR, it) }
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
