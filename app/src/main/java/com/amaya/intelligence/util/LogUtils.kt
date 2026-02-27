package com.amaya.intelligence.util

import android.util.Log
import com.amaya.intelligence.BuildConfig

/**
 * Debug logging utility that only logs in debug builds.
 * 
 * Usage:
 *   debugLog("MyTag", "My message")
 *   debugLog("MyTag") { "Expensive message: ${computeValue()}" }
 */
inline fun debugLog(tag: String, message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message())
    }
}

fun debugLog(tag: String, message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message)
    }
}

/**
 * Error logging - always logs regardless of build type.
 */
fun errorLog(tag: String, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        Log.e(tag, message, throwable)
    } else {
        Log.e(tag, message)
    }
}
