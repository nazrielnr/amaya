package com.amaya.intelligence.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class McpActivity : ComponentActivity() {

    @Inject lateinit var aiSettingsManager: AiSettingsManager

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
        fun start(context: Context) {
            context.startActivity(Intent(context, McpActivity::class.java))
        }
    }
}
