package com.amaya.intelligence.ui.activities.cronjob.local

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.ui.screens.cronjob.local.LocalCronJobScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalCronJobActivity : AppCompatActivity() {

    @Inject
    lateinit var cronJobRepository: CronJobRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                LocalCronJobScreen(
                    onNavigateBack = { finish() },
                    cronJobRepository = cronJobRepository
                )
            }
        }
    }

    companion object {
        fun start(activity: android.app.Activity) {
            activity.startActivity(android.content.Intent(activity, LocalCronJobActivity::class.java))
        }
    }
}
