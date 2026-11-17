package com.switchpulse.app.data.remote

import com.switchpulse.app.data.DealScorer
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.MetacriticOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches on-demand direct external metadata (Metacritic via Steam/JSON-LD and screenshots via
 * Steam/Nintendo eShop HTML) without calling an LLM/AI scraper.
 */
class GameInsightsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val steamAppDetailsUrl: String = "https://store.steampowered.com/api/appdetails",
    private val steamStoreSearchUrl: String? = null,
    private val metacriticBaseUrl: String? = null,
    private val nintendoProductHtmlOverrideUrl: String? = null,
    private val allowExternalMetadataLookup: Boolean = true
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val usesDefaultSteamEndpoint: Boolean
        get() = steamAppDetailsUrl.startsWith("https://store.steampowered.com", ignoreCase = true)

    suspend fun fetchInsights(
        deal: Deal,
        @Suppress("UNUSED_PARAMETER") geminiApiKey: String = "",
        @Suppress("UNUSED_PARAMETER") geminiModel: String = GeminiModels.FLASH_LITE,
        likedGames: List<LikedGame> = emptyList(),
        userTastePrompt: String = "",
        provider: AiProvider = AiProvider.GEMINI
    ): GameExternalInsights = withContext(Dispatchers.IO) {
        val resolvedAppId = if (allowExternalMetadataLookup) {
            deal.steamAppId ?: resolveSteamAppIdOnDemand(deal)
        } else {
            null
        }
        val steamDetails = if (allowExternalMetadataLookup) {
            resolvedAppId?.let { fetchSteamDetails(it) }
        } else {
            null
        }
        val steamMeta = steamDetails?.metacritic
        val steamScreenshots = steamDetails?.screenshots.orEmpty()

        val (directMeta, nintendoScreenshots) = if (allowExternalMetadataLookup) {
            coroutineScope {
                val metaDeferred = async {
                    if (steamMeta == null) fetchDirectMetacritic(deal.title) else null
                }
                val nintendoDeferred = async {
                    if (steamScreenshots.size < 2) fetchNintendoScreenshots(deal.eshopUrl) else emptyList()
                }
                metaDeferred.await() to nintendoDeferred.await()
            }
        } else {
            null to emptyList()
        }

        val screenshotUrls = (steamScreenshots + nintendoScreenshots)
            .map { it.trim() }
            .filter { it.startsWith("http", ignoreCase = true) }
            .distinct()
            .take(8)

        val profileSig = computeProfileSignature(
            likedGames = likedGames,
            userTastePrompt = userTastePrompt,
            provider = provider,
            modelId = ""
        )

        val officialMetaScore = steamMeta?.score ?: directMeta?.score
        val officialMetaUrl = steamMeta?.url ?: directMeta?.url
        val metacriticScore = officialMetaScore ?: deal.metacriticScore
        val metacriticUrl = officialMetaUrl?.takeIf { it.startsWith("http") }

        val resolvedMetaOrigin = when {
            steamMeta?.score != null -> MetacriticOrigin.STEAM_PC
            directMeta?.score != null -> MetacriticOrigin.METACRITIC
            else -> MetacriticOrigin.NONE
        }

        val sourceParts = buildList {
            when (resolvedMetaOrigin) {
                MetacriticOrigin.STEAM_PC -> add("Steam Metacritic API (PC)")
                MetacriticOrigin.METACRITIC -> add("Metacritic")
                else -> Unit
            }
            if (steamScreenshots.isNotEmpty() && resolvedMetaOrigin != MetacriticOrigin.STEAM_PC) {
                add("Steam")
            }
            if (nintendoScreenshots.isNotEmpty()) {
                add("Nintendo eShop")
            }
        }
        val sourceLabel = sourceParts.joinToString(" · ")

        GameExternalInsights(
            dealId = deal.id,
            metacriticScore = metacriticScore,
            metacriticUrl = metacriticUrl,
            metacriticOrigin = resolvedMetaOrigin,
            hltbMainHours = null,
            hltbMainExtraHours = null,
            hltbCompletionistHours = null,
            hltbIsEstimated = false,
            aiVerdict = null,
            summaryNote = null,
            screenshotUrls = screenshotUrls,
            sourceLabel = sourceLabel,
            profileSignature = profileSig,
            fetchedAtMs = System.currentTimeMillis()
        )
    }

    private fun resolveSteamAppIdOnDemand(deal: Deal): Int? {
        if (!allowExternalMetadataLookup) return null
        if (DealScorer.isNintendoFirstParty(deal.publisher)) return null
        val searchEndpoint = steamStoreSearchUrl?.takeIf { it.isNotBlank() }
            ?: if (usesDefaultSteamEndpoint) "https://store.steampowered.com/api/storesearch/" else return null
        return runCatching {
            val cleanTitle = DealTitleNormalizer.normalize(deal.title)
            if (cleanTitle.length < 2) return@runCatching null
            val url = searchEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("term", cleanTitle)
                .addQueryParameter("l", "english")
                .addQueryParameter("cc", "IT")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                val decoded = json.decodeFromString<SteamStoreSearchResponse>(raw)
                decoded.items.firstOrNull { item ->
                    DealTitleNormalizer.matchesSteamTitle(item.name.orEmpty(), cleanTitle)
                }?.id
            }
        }.getOrNull()
    }

    private fun fetchSteamDetails(appId: Int): SteamDetailsPayload? = runCatching {
        val url = steamAppDetailsUrl.toHttpUrl().newBuilder()
            .addQueryParameter("appids", appId.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string().orEmpty()
            val rootObj = json.parseToJsonElement(raw).jsonObject
            val appEl: JsonElement = rootObj[appId.toString()] ?: return null
            val wrapper = json.decodeFromJsonElement(SteamAppDetailsWrapper.serializer(), appEl)
            if (wrapper.success != true || wrapper.data == null) return null
            val shots = wrapper.data.screenshots
                .mapNotNull { s -> s.pathFull?.takeIf { it.isNotBlank() } ?: s.pathThumbnail?.takeIf { it.isNotBlank() } }
                .distinct()
                .take(8)
            SteamDetailsPayload(
                metacritic = wrapper.data.metacritic,
                screenshots = shots
            )
        }
    }.getOrNull()

    internal fun fetchNintendoScreenshots(eshopUrl: String?): List<String> {
        if (!allowExternalMetadataLookup) return emptyList()
        val targetUrl = nintendoProductHtmlOverrideUrl?.takeIf { it.isNotBlank() } ?: run {
            if (!usesDefaultSteamEndpoint) return emptyList()
            val raw = eshopUrl?.trim().orEmpty()
            if (!raw.startsWith("https://www.nintendo.", ignoreCase = true) ||
                raw.contains("/Search/", ignoreCase = true)
            ) {
                return emptyList()
            }
            raw
        }
        return runCatching {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val html = response.body?.string().orEmpty()
                extractNintendoScreenshotUrls(html)
            }
        }.getOrDefault(emptyList())
    }

    internal fun extractNintendoScreenshotUrls(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        return NINTENDO_SCREENSHOT_REGEX.findAll(html)
            .map { match ->
                val rawPath = match.value
                if (rawPath.startsWith("http", ignoreCase = true)) {
                    rawPath
                } else {
                    "https://www.nintendo.com$rawPath"
                }
            }
            .distinct()
            .take(8)
            .toList()
    }

    internal fun fetchDirectMetacritic(rawTitle: String): SteamMetacriticBlock? {
        if (!allowExternalMetadataLookup) return null
        val baseUrl = metacriticBaseUrl?.takeIf { it.isNotBlank() }
            ?: if (usesDefaultSteamEndpoint) "https://www.metacritic.com" else return null
        val slug = buildMetacriticSlug(rawTitle)
        if (slug.length < 2) return null

        val targetUrl = "${baseUrl.trimEnd('/')}/game/$slug/"
        return runCatching {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string().orEmpty()
                val match = LD_METASCORE_REGEX.find(html)
                val score = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..100 }
                    ?: return@use null
                SteamMetacriticBlock(score = score, url = targetUrl)
            }
        }.getOrNull()
    }

    internal fun buildMetacriticSlug(rawTitle: String): String {
        val normalized = DealTitleNormalizer.normalize(
            rawTitle
                .replace("'", "")
                .replace("’", "")
                .replace("&", " and ")
        )
        return normalized
            .trim()
            .replace(Regex("\\s+"), "-")
            .trim('-')
    }

    companion object {
        private val LD_METASCORE_REGEX = Regex(
            "\"@type\"\\s*:\\s*\"AggregateRating\"[^}]*?\"ratingValue\"\\s*:\\s*(\\d{1,3})",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val NINTENDO_SCREENSHOT_REGEX = Regex(
            "(?:https?://www\\.nintendo\\.com)?/eu/media/images/06_screenshots/[^\\s\"'<>\\\\]+?\\.(?:jpg|jpeg|png|webp)",
            RegexOption.IGNORE_CASE
        )

        fun computeProfileSignature(
            @Suppress("UNUSED_PARAMETER") likedGames: List<LikedGame> = emptyList(),
            @Suppress("UNUSED_PARAMETER") userTastePrompt: String = "",
            @Suppress("UNUSED_PARAMETER") provider: AiProvider = AiProvider.GEMINI,
            @Suppress("UNUSED_PARAMETER") modelId: String = ""
        ): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("v3:direct".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .build()
    }
}

internal data class SteamDetailsPayload(
    val metacritic: SteamMetacriticBlock? = null,
    val screenshots: List<String> = emptyList()
)

@Serializable
internal data class SteamAppDetailsWrapper(
    val success: Boolean? = null,
    val data: SteamAppDetailsData? = null
)

@Serializable
internal data class SteamAppDetailsData(
    val metacritic: SteamMetacriticBlock? = null,
    val screenshots: List<SteamScreenshotBlock> = emptyList()
)

@Serializable
internal data class SteamScreenshotBlock(
    @kotlinx.serialization.SerialName("path_thumbnail") val pathThumbnail: String? = null,
    @kotlinx.serialization.SerialName("path_full") val pathFull: String? = null
)

@Serializable
internal data class SteamMetacriticBlock(
    val score: Int? = null,
    val url: String? = null
)
