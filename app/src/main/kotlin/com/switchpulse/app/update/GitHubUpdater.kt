package com.switchpulse.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data object NotConfigured : UpdateCheckResult
    data object NoApkAsset : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

enum class InstallLaunchResult {
    LAUNCHED,
    NEEDS_UNKNOWN_SOURCES_PERMISSION
}

class GitHubUpdater(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatestRelease(
        repoSlug: String,
        currentVersionName: String,
        currentVersionCode: Int,
        overrideEndpointUrl: String? = null
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val cleanSlug = repoSlug.trim()
        if (!REPO_REGEX.matches(cleanSlug)) {
            return@withContext if (cleanSlug.isEmpty()) {
                UpdateCheckResult.NotConfigured
            } else {
                UpdateCheckResult.Error("Formato repository non valido (usa owner/repo)")
            }
        }

        val url = overrideEndpointUrl ?: "https://api.github.com/repos/$cleanSlug/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SwitchPulse-Updater/1.0")
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext UpdateCheckResult.Error("Nessuna GitHub Release trovata per $cleanSlug")
                }
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error("GitHub API HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val dto = json.decodeFromString<GitHubReleaseDto>(body)
                val apkAsset = dto.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext UpdateCheckResult.NoApkAsset

                val info = ReleaseInfo(
                    tagName = dto.tagName,
                    title = dto.name.ifBlank { dto.tagName },
                    changelog = dto.body.orEmpty(),
                    htmlUrl = dto.htmlUrl,
                    apkDownloadUrl = apkAsset.browserDownloadUrl,
                    apkFileName = apkAsset.name,
                    apkSizeBytes = apkAsset.size
                )

                if (isRemoteNewer(dto.tagName, currentVersionName, currentVersionCode)) {
                    UpdateCheckResult.UpdateAvailable(info)
                } else {
                    UpdateCheckResult.UpToDate(currentVersionName)
                }
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error(err.message ?: "Errore controllo aggiornamenti")
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "switchpulse-update.apk")
        if (targetFile.exists()) targetFile.delete()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "SwitchPulse-Updater/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download APK HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Corpo risposta vuoto")
            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
        }
        onProgress(1f)
        targetFile
    }

    fun launchInstaller(context: Context, apkFile: File): InstallLaunchResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permIntent)
            return InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return InstallLaunchResult.LAUNCHED
    }

    companion object {
        private val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

        internal fun isRemoteNewer(
            remoteTag: String,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            val remoteParts = parseNumericParts(remoteTag)
            val localParts = parseNumericParts(currentVersionName)

            if (remoteParts.isEmpty()) return false

            // Single-number tags like "build-4" or "v4" compare directly against versionCode
            if (remoteParts.size == 1 && localParts.size > 1) {
                return remoteParts[0] > currentVersionCode
            }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }

        private fun parseNumericParts(raw: String): List<Int> {
            return Regex("\\d+").findAll(raw).mapNotNull { it.value.toIntOrNull() }.toList()
        }
    }
}

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)
