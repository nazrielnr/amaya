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
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
