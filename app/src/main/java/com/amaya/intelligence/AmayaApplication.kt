package com.amaya.intelligence

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Amaya Intelligence.
 * Uses Hilt for dependency injection and provides HiltWorkerFactory
 * so WorkManager workers (e.g. [com.amaya.intelligence.service.ReminderWorker]) can be injected.
 */
@HiltAndroidApp
class AmayaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization if needed
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
