package com.amaya.intelligence.ui.activities.chat.local

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.chat.local.LocalChatScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocalChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                LocalChatScreen(
                    activeReminderCount = intent.getIntExtra("active_reminder_count", -1),
                    onNavigateToSettings = {
                        com.amaya.intelligence.ui.activities.settings.local.LocalSettingsActivity.start(this)
                    },
                    onNavigateToWorkspace = {
                        com.amaya.intelligence.ui.activities.project.local.LocalProjectActivity.start(this)
                    },
                    onNavigateToRemoteSession = {
                        startActivity(android.content.Intent(this, com.amaya.intelligence.ui.activities.remote.RemoteSessionActivity::class.java))
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: android.content.Context, activeReminderCount: Int = -1) {
            val intent = android.content.Intent(context, LocalChatActivity::class.java).apply {
                putExtra("active_reminder_count", activeReminderCount)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
