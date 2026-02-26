package com.opencode.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for OpenCode Mobile AI Coding Agent.
 * 
 * Uses Hilt for dependency injection to manage:
 * - Database instances (Room)
 * - Network clients (Retrofit/OkHttp)
 * - AI Provider implementations
 * - Tool Executors
 */
@HiltAndroidApp
class OpenCodeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Application-level initialization if needed
    }
}
