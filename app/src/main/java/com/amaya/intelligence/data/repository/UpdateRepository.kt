package com.amaya.intelligence.data.repository

import com.amaya.intelligence.BuildConfig
import com.amaya.intelligence.data.remote.api.GitHubUpdateService
import com.amaya.intelligence.domain.models.UpdateInfo
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for app update operations.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubService: GitHubUpdateService
) {
    companion object {
        private const val OWNER = "nazrielnr"
        private const val REPO = "amaya"
    }

    suspend fun getLatestUpdate(): UpdateInfo? {
        return try {
            val response = gitHubService.getLatestRelease(OWNER, REPO)
            val latestVersionName = response.tagName.removePrefix("v").trim()
            
            val currentVersionName = BuildConfig.VERSION_NAME
            
            val downloadUrl = response.assets.find { it.name.endsWith(".apk") }?.downloadUrl
                ?: "https://github.com/$OWNER/$REPO/releases/latest"

            val isNewer = isVersionNewer(latestVersionName, currentVersionName)

            UpdateInfo(
                versionName = latestVersionName,
                versionCode = 0,
                changelog = response.body,
                downloadUrl = downloadUrl,
                isNewer = isNewer
            )
        } catch (e: Exception) {
            errorLog("UpdateRepository", "Failed to fetch latest update", e)
            null
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest == current) return false
        
        // Split version into numeric parts and suffix (e.g., "1.0.0-alpha" -> ["1.0.0", "alpha"])
        val latestParts = latest.split('-', limit = 2)
        val currentParts = current.split('-', limit = 2)
        
        val latestNumeric = latestParts[0].split('.').mapNotNull { it.toIntOrNull() }
        val currentNumeric = currentParts[0].split('.').mapNotNull { it.toIntOrNull() }
        
        // 1. Compare numeric segments
        val length = maxOf(latestNumeric.size, currentNumeric.size)
        for (i in 0 until length) {
            val l = latestNumeric.getOrElse(i) { 0 }
            val c = currentNumeric.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        
        // 2. Numeric parts are equal, compare suffixes
        val latestSuffix = latestParts.getOrNull(1)
        val currentSuffix = currentParts.getOrNull(1)
        
        return when {
            // No suffix is newer than any suffix (e.g., 1.0.0 > 1.0.0-rc)
            latestSuffix == null && currentSuffix != null -> true
            latestSuffix != null && currentSuffix == null -> false
            // Both have suffixes, compare lexicographically (alpha < beta < rc)
            latestSuffix != null && currentSuffix != null -> latestSuffix > currentSuffix
            else -> false
        }
    }
}
