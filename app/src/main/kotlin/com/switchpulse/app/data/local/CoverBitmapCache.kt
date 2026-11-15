package com.switchpulse.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads and downscales cover artwork into app cache so Jetpack Glance widgets
 * can render real Nintendo covers offline while staying well within RemoteViews binder limits.
 */
class CoverBitmapCache(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val cacheDir = File(context.cacheDir, "widget_covers").apply { mkdirs() }

    suspend fun prefetchCovers(deals: List<Pair<String, String?>>) = withContext(Dispatchers.IO) {
        deals.forEachIndexed { index, (id, url) ->
            if (!url.isNullOrBlank()) {
                val maxWidth = if (index == 0) 480 else 160
                val file = coverFile(id, hero = index == 0)
                if (!file.exists()) {
                    downloadAndScale(url, file, maxWidth)
                }
            }
        }
    }

    fun loadCachedBitmap(dealId: String, hero: Boolean = false): Bitmap? {
        val primary = coverFile(dealId, hero)
        val fallback = coverFile(dealId, !hero)
        val file = when {
            primary.exists() -> primary
            fallback.exists() -> fallback
            else -> return null
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun coverFile(dealId: String, hero: Boolean): File {
        val suffix = if (hero) "hero" else "thumb"
        return File(cacheDir, "${dealId}_$suffix.jpg")
    }

    private fun downloadAndScale(url: String, target: File, targetWidth: Int) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                val scale = targetWidth.toFloat() / decoded.width.coerceAtLeast(1)
                val scaled = if (scale < 1f) {
                    val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
                FileOutputStream(target).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                scaled.recycle()
            }
        }
    }
}
