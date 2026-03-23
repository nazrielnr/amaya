package com.amaya.intelligence.ui.activities.mcp.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.mcp.remote.RemoteMcpScreen
import com.amaya.intelligence.ui.theme.AmayaTheme

class RemoteMcpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                RemoteMcpScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, RemoteMcpActivity::class.java))
        }
    }
}
