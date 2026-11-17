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
        val activeFilenames = HashSet<String>(deals.size * 4)
        deals.forEachIndexed { index, (id, _) ->
            val safeId = sanitizeId(id)
            if (index == 0) activeFilenames += "${safeId}_w2_hero.jpg"
            if (index in 1..4) activeFilenames += "${safeId}_w2_thumb.jpg"
            activeFilenames += "${safeId}_w2_main.jpg"
            activeFilenames += "${safeId}_w2_side.jpg"
        }
        pruneUnused(activeFilenames)

        deals.forEachIndexed { index, (id, url) ->
            if (!url.isNullOrBlank()) {
                val heroTarget = if (index == 0) coverFile(id, Kind.HERO) else null
                val thumbTarget = if (index in 1..4) coverFile(id, Kind.THUMB) else null
                val mainTarget = coverFile(id, Kind.TALL_MAIN)
                val sideTarget = coverFile(id, Kind.TALL_SIDE)
                val needsFetch = (heroTarget != null && !heroTarget.exists()) ||
                    (thumbTarget != null && !thumbTarget.exists()) ||
                    !mainTarget.exists() ||
                    !sideTarget.exists()
                if (needsFetch) {
                    downloadAndPrepareVariants(
                        url = url,
                        heroTarget = heroTarget,
                        thumbTarget = thumbTarget,
                        mainTarget = mainTarget,
                        sideTarget = sideTarget
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
        return loadCachedBitmap(dealId, if (hero) Kind.HERO else Kind.THUMB)
    }

    fun loadCachedBitmap(dealId: String, kind: Kind): Bitmap? {
        val candidates = when (kind) {
            Kind.HERO -> listOf(Kind.HERO, Kind.TALL_MAIN, Kind.THUMB)
            Kind.THUMB -> listOf(Kind.THUMB, Kind.TALL_SIDE, Kind.HERO)
            Kind.TALL_MAIN -> listOf(Kind.TALL_MAIN, Kind.HERO, Kind.TALL_SIDE)
            Kind.TALL_SIDE -> listOf(Kind.TALL_SIDE, Kind.TALL_MAIN, Kind.THUMB)
        }
        val file = candidates.map { coverFile(dealId, it) }.firstOrNull { it.exists() } ?: return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
    }

    private fun sanitizeId(dealId: String): String =
        dealId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "deal" }

    private fun coverFile(dealId: String, kind: Kind): File {
        val suffix = when (kind) {
            Kind.HERO -> "w2_hero"
            Kind.THUMB -> "w2_thumb"
            Kind.TALL_MAIN -> "w2_main"
            Kind.TALL_SIDE -> "w2_side"
        }
        return File(cacheDir, "${sanitizeId(dealId)}_$suffix.jpg")
    }

    private fun downloadAndPrepareVariants(
        url: String,
        heroTarget: File?,
        thumbTarget: File?,
        mainTarget: File,
        sideTarget: File
    ) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                val decodeOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return
                try {
                    if (heroTarget != null && !heroTarget.exists()) {
                        val bmp = centerCropScale(decoded, targetWidth = HERO_WIDTH_PX, targetHeight = HERO_HEIGHT_PX)
                        writeJpeg(bmp, heroTarget, 82)
                        if (bmp !== decoded) bmp.recycle()
                    }
                    if (thumbTarget != null && !thumbTarget.exists()) {
                        val bmp = centerCropScale(decoded, targetWidth = THUMB_WIDTH_PX, targetHeight = THUMB_HEIGHT_PX)
                        writeJpeg(bmp, thumbTarget, 80)
                        if (bmp !== decoded) bmp.recycle()
                    }
                    if (!mainTarget.exists()) {
                        val bmp = centerCropScale(decoded, targetWidth = TALL_MAIN_WIDTH_PX, targetHeight = TALL_HEIGHT_PX)
                        writeJpeg(bmp, mainTarget, 82)
                        if (bmp !== decoded) bmp.recycle()
                    }
                    if (!sideTarget.exists()) {
                        val bmp = centerCropScale(decoded, targetWidth = TALL_SIDE_WIDTH_PX, targetHeight = TALL_HEIGHT_PX)
                        writeJpeg(bmp, sideTarget, 78)
                        if (bmp !== decoded) bmp.recycle()
                    }
                } finally {
                    decoded.recycle()
                }
            }
        }
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
        THUMB,
        TALL_MAIN,
        TALL_SIDE
    }

    companion object {
        // Sized so all decoded RGB_565 bitmaps inside any single widget stay < 220 KB total in Binder IPC.
        const val HERO_WIDTH_PX = 340
        const val HERO_HEIGHT_PX = 192
        const val THUMB_WIDTH_PX = 144
        const val THUMB_HEIGHT_PX = 82
        const val TALL_MAIN_WIDTH_PX = 220
        const val TALL_SIDE_WIDTH_PX = 104
        const val TALL_HEIGHT_PX = 196
        private const val MAX_CACHE_BYTES = 1_200_000L
    }
}
