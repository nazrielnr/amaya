package com.amaya.intelligence

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Amaya Intelligence.
 *
 * Sets AppCompatDelegate night mode BEFORE any Activity is created so that:
 * - Window background matches the correct theme (no predictive-back flash)
 * - Status bar / nav bar decorations are correct on first frame
 * - All child Activities inherit DayNight automatically
 */
@HiltAndroidApp
class AmayaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var aiSettingsManager: AiSettingsManager

    override fun onCreate() {
        super.onCreate()
        // Apply theme synchronously before any Activity starts so that
        // window background, predictive back, and transitions use correct colours.
        applyThemeFromSettings()
    }

    /**
     * Reads the saved theme preference and applies it via AppCompatDelegate.
     * Called on startup so every Activity gets the right DayNight mode before rendering.
     * Also call this whenever the user changes the theme setting.
     */
    fun applyThemeFromSettings() {
        val theme = aiSettingsManager.getSettings().theme
        val mode = when (theme) {
            "light"  -> AppCompatDelegate.MODE_NIGHT_NO
            "dark"   -> AppCompatDelegate.MODE_NIGHT_YES
            else     -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
