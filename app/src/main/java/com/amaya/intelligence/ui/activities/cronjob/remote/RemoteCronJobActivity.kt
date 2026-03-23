package com.amaya.intelligence.ui.activities.cronjob.remote

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.ui.screens.cronjob.remote.RemoteCronJobScreen
import com.amaya.intelligence.ui.theme.AmayaTheme

class RemoteCronJobActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                RemoteCronJobScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, RemoteCronJobActivity::class.java))
        }
    }
}
