package com.amaya.intelligence.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class McpActivity : AppCompatActivity() {

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                McpScreen(
                    onNavigateBack = { finish() },
                    aiSettingsManager = aiSettingsManager
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, McpActivity::class.java))
        }
    }
}
