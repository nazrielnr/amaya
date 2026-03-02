package com.amaya.intelligence.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.amaya.intelligence.data.local.db.dao.CronJobDao
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CronJobActivity : AppCompatActivity() {

    @Inject
    lateinit var cronJobRepository: CronJobRepository

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
