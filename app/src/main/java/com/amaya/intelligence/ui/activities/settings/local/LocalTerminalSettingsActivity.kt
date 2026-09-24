package com.amaya.intelligence.ui.activities.settings.local

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.ui.screens.settings.local.TerminalSettingsScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalTerminalSettingsActivity : AppCompatActivity() {
    @Inject lateinit var repository: TerminalSettingsRepository
    @Inject lateinit var sandboxManager: LinuxSandboxManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                TerminalSettingsScreen(
                    repository = repository,
                    sandboxManager = sandboxManager,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(activity: Activity) {
            activity.startActivity(Intent(activity, LocalTerminalSettingsActivity::class.java))
        }
    }
}
