package com.example.data.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.config.AppVersionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val releaseTag: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
    val changelog: String = "",
    val isAvailable: Boolean = true
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String, val errorType: UpdateErrorType) : UpdateCheckResult()
}

enum class UpdateErrorType {
    NO_INTERNET,
    TIMEOUT,
    HTTP_NOT_FOUND,
    DOWNLOAD_FAILED,
    INSUFFICIENT_STORAGE,
    UNKNOWN
}

object UpdateChecker {
    val CURRENT_VERSION = AppVersionConfig.VERSION_NAME
    val CURRENT_BUILD_NUMBER = AppVersionConfig.BUILD_NUMBER

    // Ordered list of known release builds to query
    val KNOWN_RELEASES = listOf(
        ReleaseInfo("Beta10", "Beta10", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta10/b10.apk", changelog = "Fixed Flash Pre-Check OOM, unified AppTopBar, ADB backend diagnostics, and Samsung Firmware Odin tool."),
        ReleaseInfo("Beta8", "Beta8", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta8/b9.apk", changelog = "Optimized ROM packing routines and kernel crash analyzer."),
        ReleaseInfo("Beta7", "Beta7", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta7/b7.apk", changelog = "Added partition analyzer and dynamic Treble checks."),
        ReleaseInfo("Beta6", "Beta6", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta6/b6.apk", changelog = "Improved ADB shell connection stability."),
        ReleaseInfo("Beta5", "Beta5", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta5/b5.apk", changelog = "Initial support for Galaxy J2 Prime LineageOS 18.1 workspace."),
        ReleaseInfo("Beta4", "Beta4", "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases/download/Beta4/b4.apk", changelog = "Initial public beta release.")
    )

    fun parseVersionRank(versionStr: String): Int {
        val digits = versionStr.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentRank = parseVersionRank(CURRENT_VERSION)

        for (release in KNOWN_RELEASES) {
            val relRank = parseVersionRank(release.versionName)
            if (relRank > currentRank) {
                // Verify URL reachable
                try {
                    val url = URL(release.downloadUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    val code = conn.responseCode
                    if (code in 200..399) {
                        val len = conn.contentLengthLong
                        val verifiedRelease = release.copy(sizeBytes = if (len > 0) len else 15 * 1024 * 1024L)
                        return@withContext UpdateCheckResult.UpdateAvailable(verifiedRelease, CURRENT_VERSION)
                    }
                } catch (e: java.net.UnknownHostException) {
                    return@withContext UpdateCheckResult.Error("No internet connection available.", UpdateErrorType.NO_INTERNET)
                } catch (e: java.net.SocketTimeoutException) {
                    return@withContext UpdateCheckResult.Error("Connection timed out while checking for updates.", UpdateErrorType.TIMEOUT)
                } catch (e: Exception) {
                    // Try next release or return error
                }
            }
        }

        return@withContext UpdateCheckResult.UpToDate(CURRENT_VERSION)
    }

    suspend fun downloadUpdateApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Security: Enforce HTTPS for APK delivery
            if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext null
            }

            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val destFile = File(updateDir, "GalaxyJ2primeTool_update.apk")
            if (destFile.exists()) destFile.delete()

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true

            val totalSize = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalSize > 0) totalRead.toFloat() / totalSize else 0.5f
                        onProgress(progress, "Downloading: ${totalRead / 1024} KB / ${if (totalSize > 0) "${totalSize / 1024} KB" else "..."}")
                    }
                    output.flush()
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                destFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
