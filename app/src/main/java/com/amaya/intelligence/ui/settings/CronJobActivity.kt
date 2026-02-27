package com.amaya.intelligence.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CronJobActivity : ComponentActivity() {

    @Inject lateinit var cronJobRepository: CronJobRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                CronJobScreen(
                    onNavigateBack = { finish() },
                    cronJobRepository = cronJobRepository
                )
            }
        }
    }
}
