package com.picassoworkshop.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.picassoworkshop.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewerThanCurrent: Boolean,
    val htmlUrl: String
)

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class UpdateAvailable(val release: AppReleaseInfo, val currentVersion: String) : AppUpdateState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long, val release: AppReleaseInfo) : AppUpdateState()
    data class ReadyToInstall(val apkFile: File, val release: AppReleaseInfo) : AppUpdateState()
    object UpToDate : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class AppUpdateManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    var cachedRelease: AppReleaseInfo? = null
        private set

    val currentVersionName: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Throwable) {
            "1.0.0"
        }

    fun checkForAppUpdates(repo: String = "CharlesLennon/picasso-workshop") {
        if (_updateState.value is AppUpdateState.Checking || _updateState.value is AppUpdateState.Downloading) {
            return
        }

        _updateState.value = AppUpdateState.Checking

        scope.launch {
            try {
                val releaseInfo = withContext(Dispatchers.IO) {
                    fetchLatestRelease(repo)
                }

                if (releaseInfo != null) {
                    cachedRelease = releaseInfo
                    if (releaseInfo.isNewerThanCurrent) {
                        _updateState.value = AppUpdateState.UpdateAvailable(
                            release = releaseInfo,
                            currentVersion = currentVersionName
                        )
                    } else {
                        _updateState.value = AppUpdateState.UpToDate
                    }
                } else {
                    _updateState.value = AppUpdateState.UpToDate
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for app updates")
                _updateState.value = AppUpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    private fun fetchLatestRelease(repo: String): AppReleaseInfo? {
        val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "PicassoWorkshop-Android")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        try {
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return null
            }
            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode from GitHub Releases API")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val bodyText = json.optString("body", "")
            val htmlUrl = json.optString("html_url", "")
            val assets = json.optJSONArray("assets")

            var apkUrl = ""
            var apkName = "picasso-workshop-update.apk"
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = assetName
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isBlank()) {
                return null
            }

            val isNewer = compareVersions(tagName, currentVersionName) > 0

            return AppReleaseInfo(
                tagName = tagName,
                releaseTitle = name,
                releaseNotes = bodyText,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                isNewerThanCurrent = isNewer,
                htmlUrl = htmlUrl
            )
        } finally {
            conn.disconnect()
        }
    }

    fun startDownload(release: AppReleaseInfo) {
        scope.launch {
            _updateState.value = AppUpdateState.Downloading(
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = release.apkSizeBytes,
                release = release
            )

            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(release) { progress, downloaded, total ->
                        scope.launch(Dispatchers.Main) {
                            _updateState.value = AppUpdateState.Downloading(
                                progress = progress,
                                bytesDownloaded = downloaded,
                                totalBytes = total,
                                release = release
                            )
                        }
                    }
                }

                _updateState.value = AppUpdateState.ReadyToInstall(
                    apkFile = apkFile,
                    release = release
                )
            } catch (e: Exception) {
                Timber.e(e, "APK download failed")
                _updateState.value = AppUpdateState.Error("Download failed: ${e.message}")
            }
        }
    }

    private fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File {
        val downloadDir = File(context.cacheDir, "apk_updates")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val destFile = File(downloadDir, release.apkFileName)
        if (destFile.exists()) destFile.delete()

        val url = URL(release.apkDownloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "PicassoWorkshop-Android")
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        var actualConn = conn
        val redirectCode = actualConn.responseCode
        if (redirectCode == HttpURLConnection.HTTP_MOVED_PERM || redirectCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            val redirectUrl = actualConn.getHeaderField("Location")
            actualConn.disconnect()
            actualConn = URL(redirectUrl).openConnection() as HttpURLConnection
            actualConn.setRequestProperty("User-Agent", "PicassoWorkshop-Android")
        }

        val totalBytes = if (release.apkSizeBytes > 0) release.apkSizeBytes else actualConn.contentLengthLong

        actualConn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 200) {
                        lastProgressUpdate = now
                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        onProgress(progress, totalRead, totalBytes)
                    }
                }
                output.flush()
            }
        }
        actualConn.disconnect()

        return destFile
    }

    fun installApk(apkFile: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = AppUpdateState.Error("Cannot launch installer: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = AppUpdateState.Idle
    }

    companion object {
        fun compareVersions(v1: String, v2: String): Int {
            val cleanV1 = v1.removePrefix("v").trim()
            val cleanV2 = v2.removePrefix("v").trim()

            val parts1 = cleanV1.split(".", "-").mapNotNull { it.toIntOrNull() }
            val parts2 = cleanV2.split(".", "-").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }
    }
}
