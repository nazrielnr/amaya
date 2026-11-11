package com.amaya.intelligence.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.amaya.intelligence.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    suspend fun download(updateUrl: String, onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(Uri.parse(updateUrl).scheme == "https") { "Update URL must use HTTPS" }
            val target = File(context.cacheDir, "updates/amaya.apk").apply { parentFile?.mkdirs() }
            val temporary = File(target.parentFile, "${target.name}.download")
            temporary.delete()
            httpClient.newCall(Request.Builder().url(updateUrl).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed (${response.code})" }
                val body = requireNotNull(response.body) { "Update download was empty" }
                val totalBytes = body.contentLength().takeIf { it > 0 }
                check(totalBytes == null || totalBytes <= MAX_APK_BYTES) { "Update is too large" }
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        var lastReportedBytes = 0L
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            downloadedBytes += bytesRead
                            check(downloadedBytes <= MAX_APK_BYTES) { "Update is too large" }
                            output.write(buffer, 0, bytesRead)
                            if (downloadedBytes - lastReportedBytes >= PROGRESS_REPORT_BYTES || downloadedBytes == totalBytes) {
                                lastReportedBytes = downloadedBytes
                                onProgress(downloadedBytes, totalBytes)
                            }
                        }
                        if (downloadedBytes != lastReportedBytes) onProgress(downloadedBytes, totalBytes)
                    }
                }
            }
            check(temporary.length() > 0) { "Update download was empty" }
            check(temporary.renameTo(target)) { "Could not save update" }
            validate(target)
            target
        }.also { if (it.isFailure) File(context.cacheDir, "updates/amaya.apk.download").delete() }
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun validate(apk: File) {
        val archive = context.packageManager.getPackageArchiveInfo(apk.path, PACKAGE_INFO_FLAGS)
            ?: error("Downloaded file is not an Android package")
        check(archive.packageName == context.packageName) { "Update is for a different app" }
        check(PackageInfoCompat.getLongVersionCode(archive) > BuildConfig.VERSION_CODE.toLong()) { "Update is not newer" }
        check(signatures(archive).contentEquals(signatures(context.packageManager.getPackageInfo(context.packageName, PACKAGE_INFO_FLAGS)))) {
            "Update is not signed with this app's certificate"
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: android.content.pm.PackageInfo): ByteArray = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            info.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()
        else -> info.signatures?.singleOrNull()?.toByteArray()
    } ?: error("Could not verify app signature")

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 300L * 1024 * 1024
        const val PROGRESS_REPORT_BYTES = 256L * 1024
        val PACKAGE_INFO_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
    }
}
