package com.amaya.intelligence.ui.activities.project.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.project.remote.RemoteProjectBrowserScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemoteProjectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                RemoteProjectBrowserScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, RemoteProjectActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
