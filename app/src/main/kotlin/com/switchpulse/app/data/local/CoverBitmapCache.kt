package com.switchpulse.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Downloads and downscales cover artwork into app cache so Jetpack Glance widgets
 * can render crisp Nintendo covers offline in both Hero 16:9 (W1) and Tall (W12) crops
 * while staying within RemoteViews binder limits (< 1.5 MB total).
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
        val activeFilenames = HashSet<String>(deals.size * 2)
        deals.forEach { (id, _) ->
            val safeId = sanitizeId(id)
            activeFilenames += "${safeId}_hero.jpg"
            activeFilenames += "${safeId}_tall.jpg"
        }
        pruneUnused(activeFilenames)

        deals.forEachIndexed { index, (id, url) ->
            if (!url.isNullOrBlank()) {
                val heroFile = coverFile(id, Kind.HERO)
                val tallFile = coverFile(id, Kind.TALL)
                if (!heroFile.exists() || !tallFile.exists()) {
                    downloadAndPrepareBoth(
                        url = url,
                        heroTarget = heroFile,
                        tallTarget = tallFile,
                        heroWidth = if (index == 0) 480 else 280
                    )
                }
            }
        }
        enforceSizeBudget(maxTotalBytes = MAX_CACHE_BYTES)
    }

    private fun pruneUnused(activeFilenames: Set<String>) {
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in activeFilenames) {
                    file.delete()
                }
            }
        }
    }

    private fun enforceSizeBudget(maxTotalBytes: Long) {
        runCatching {
            val files = cacheDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
            var total = files.sumOf { it.length() }
            for (f in files) {
                if (total <= maxTotalBytes) break
                val len = f.length()
                if (f.delete()) total -= len
            }
        }
    }

    fun loadCachedBitmap(dealId: String, hero: Boolean = false): Bitmap? {
        return loadCachedBitmap(dealId, if (hero) Kind.HERO else Kind.HERO)
    }

    fun loadCachedBitmap(dealId: String, kind: Kind): Bitmap? {
        val primary = coverFile(dealId, kind)
        val fallback = coverFile(dealId, if (kind == Kind.HERO) Kind.TALL else Kind.HERO)
        val file = when {
            primary.exists() -> primary
            fallback.exists() -> fallback
            else -> return null
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun sanitizeId(dealId: String): String =
        dealId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "deal" }

    private fun coverFile(dealId: String, kind: Kind): File {
        val suffix = when (kind) {
            Kind.HERO -> "hero"
            Kind.TALL -> "tall"
        }
        return File(cacheDir, "${sanitizeId(dealId)}_$suffix.jpg")
    }

    private fun downloadAndPrepareBoth(
        url: String,
        heroTarget: File,
        tallTarget: File,
        heroWidth: Int
    ) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                try {
                    if (!heroTarget.exists()) {
                        val heroBitmap = scaleToWidth(decoded, heroWidth)
                        writeJpeg(heroBitmap, heroTarget, 82)
                        if (heroBitmap !== decoded) heroBitmap.recycle()
                    }
                    if (!tallTarget.exists()) {
                        val tallBitmap = centerCropScale(decoded, targetWidth = 380, targetHeight = 340)
                        writeJpeg(tallBitmap, tallTarget, 82)
                        if (tallBitmap !== decoded) tallBitmap.recycle()
                    }
                } finally {
                    decoded.recycle()
                }
            }
        }
    }

    private fun scaleToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / source.width.coerceAtLeast(1)
        if (scale >= 1f) return source
        val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun centerCropScale(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
        val dstRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val cropW: Int
        val cropH: Int
        if (srcRatio > dstRatio) {
            cropH = source.height
            cropW = (cropH * dstRatio).roundToInt().coerceIn(1, source.width)
        } else {
            cropW = source.width
            cropH = (cropW / dstRatio).roundToInt().coerceIn(1, source.height)
        }
        val left = ((source.width - cropW) / 2).coerceAtLeast(0)
        val top = ((source.height - cropH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
        return try {
            Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        } finally {
            if (cropped !== source) cropped.recycle()
        }
    }

    private fun writeJpeg(bitmap: Bitmap, target: File, quality: Int) {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
        FileOutputStream(target).use { out ->
            out.write(buffer.toByteArray())
        }
    }

    enum class Kind {
        HERO,
        TALL
    }

    companion object {
        private const val MAX_CACHE_BYTES = 1_450_000L // < 1.5 MB budget for RemoteViews
    }
}
