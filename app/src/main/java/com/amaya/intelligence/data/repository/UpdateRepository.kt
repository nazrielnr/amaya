package com.amaya.intelligence.data.repository

import com.amaya.intelligence.BuildConfig
import com.amaya.intelligence.data.remote.api.GitHubUpdateService
import com.amaya.intelligence.domain.models.UpdateInfo
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
        // Fork-specific: this build's releases live in the fork, not upstream.
        private const val OWNER = "genzxproject"
        private const val REPO = "amaya"

        /**
         * Releases may ship one APK per ABI, so a plain "first .apk" pick can hand an
         * arm64 device an armeabi-v7a build (or worse, an x86 one). Prefer the asset
         * naming the most-preferred supported ABI, and only fall back to a lone
         * ABI-agnostic asset when the release is a universal APK.
         */
        internal fun selectApkUrl(
            assets: List<Pair<String, String>>,
            supportedAbis: List<String>
        ): String {
            val apks = assets.filter { it.first.endsWith(".apk", ignoreCase = true) }
            if (apks.isEmpty()) return ""

            for (abi in supportedAbis) {
                apks.firstOrNull { it.first.contains(abi, ignoreCase = true) }
                    ?.let { return it.second }
            }
            // No ABI-tagged match: only trust an asset that names no ABI at all.
            val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            return apks.firstOrNull { asset ->
                knownAbis.none { asset.first.contains(it, ignoreCase = true) }
            }?.second.orEmpty()
        }

        internal fun isVersionNewer(latest: String, current: String): Boolean {
            if (latest == current) return false

            val latestParts = latest.split('-', limit = 2)
            val currentParts = current.split('-', limit = 2)
            val latestNumeric = latestParts[0].split('.').mapNotNull { it.toIntOrNull() }
            val currentNumeric = currentParts[0].split('.').mapNotNull { it.toIntOrNull() }

            for (i in 0 until maxOf(latestNumeric.size, currentNumeric.size)) {
                val latestPart = latestNumeric.getOrElse(i) { 0 }
                val currentPart = currentNumeric.getOrElse(i) { 0 }
                if (latestPart != currentPart) return latestPart > currentPart
            }

            val latestSuffix = latestParts.getOrNull(1)
            val currentSuffix = currentParts.getOrNull(1)
            return latestSuffix == null && currentSuffix != null ||
                latestSuffix != null && currentSuffix != null && latestSuffix > currentSuffix
        }
    }

    suspend fun getLatestUpdate(): UpdateInfo? {
        return try {
            val response = gitHubService.getLatestRelease(OWNER, REPO)
            val latestVersionName = response.tagName.removePrefix("v").trim()
            
            val currentVersionName = BuildConfig.VERSION_NAME
            
            val downloadUrl = selectApkUrl(
                response.assets.map { it.name to it.downloadUrl },
                android.os.Build.SUPPORTED_ABIS.toList()
            )
            val isNewer = downloadUrl.isNotBlank() && isVersionNewer(latestVersionName, currentVersionName)

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


}
