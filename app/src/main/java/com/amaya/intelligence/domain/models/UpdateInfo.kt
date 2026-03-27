package com.amaya.intelligence.domain.models

/**
 * Model representing the latest available update for the app.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val isNewer: Boolean = false
)
