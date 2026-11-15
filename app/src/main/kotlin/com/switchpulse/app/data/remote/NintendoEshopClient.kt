package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class NintendoEshopClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val searchBaseUrl: String = SEARCH_URL,
    private val priceBaseUrl: String = PRICE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches popular discounted Switch & Switch 2 games from Nintendo Europe Solr (IT)
     * and enriches them with live EUR prices and discount end timestamps from Nintendo Price API.
     */
    suspend fun fetchPopularDeals(): List<Deal> = withContext(Dispatchers.IO) {
        val generalDocs = querySearch(
            fq = "type:GAME AND price_has_discount_b:true AND (system_type:*nintendoswitch*)",
            rows = 80
        )
        val switch2Docs = querySearch(
            fq = "type:GAME AND price_has_discount_b:true AND system_type:*nintendoswitch2*",
            rows = 40
        )

        val mergedDocs = mergeByIdPreservingRank(generalDocs, switch2Docs)
        val nsuids = mergedDocs.mapNotNull { pickPrimaryNsuid(it.nsuidTxt) }.distinct()
        val livePrices = fetchLivePrices(nsuids)

        mergedDocs.mapIndexedNotNull { index, doc ->
            mapDocToDeal(doc = doc, rank = index + 1, livePrices = livePrices)
        }
    }

    private fun querySearch(fq: String, rows: Int): List<SolrDoc> {
        val url = searchBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", "*")
            .addQueryParameter("fq", fq)
            .addQueryParameter("rows", rows.toString())
            .addQueryParameter("start", "0")
            .addQueryParameter("wt", "json")
            .addQueryParameter("sort", "popularity asc")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Nintendo Search HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return json.decodeFromString<SolrSearchResponse>(body).response.docs
        }
    }

    private fun fetchLivePrices(nsuids: List<String>): Map<String, PriceEntry> {
        if (nsuids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, PriceEntry>()

        nsuids.chunked(50).forEach { chunk ->
            val url = priceBaseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("country", "IT")
                .addQueryParameter("lang", "it")
                .addQueryParameter("ids", chunk.joinToString(","))
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val parsed = json.decodeFromString<NintendoPriceResponse>(body)
                        parsed.prices.forEach { entry ->
                            result[entry.titleId.toString()] = entry
                        }
                    }
                }
            }
        }

        return result
    }

    internal fun mapDocToDeal(
        doc: SolrDoc,
        rank: Int,
        livePrices: Map<String, PriceEntry>
    ): Deal? {
        val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return null
        val title = doc.title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val nsuid = pickPrimaryNsuid(doc.nsuidTxt)
        val live = nsuid?.let { livePrices[it] }

        val regular = live?.regularPrice?.rawValue?.toDoubleOrNull()
            ?: doc.priceRegularF?.takeIf { it > 0.0 }
            ?: 0.0
        val discounted = live?.discountPrice?.rawValue?.toDoubleOrNull()
            ?: doc.priceDiscountedF?.takeIf { it > 0.0 }
            ?: 0.0

        if (discounted <= 0.0) return null

        val computedPct = if (regular > discounted && regular > 0.0) {
            (((regular - discounted) / regular) * 100.0).roundToInt()
        } else {
            doc.priceDiscountPercentageF?.roundToInt() ?: 0
        }

        if (computedPct <= 0) return null

        val normalizedRegular = if (regular > discounted) {
            regular
        } else {
            // Fallback estimate when regularPrice is missing in Solr and live API omitted it
            (discounted / (1.0 - computedPct / 100.0))
        }

        val platform = detectPlatform(doc.systemType)
        val relativeUrl = doc.url.orEmpty()
        val eshopUrl = when {
            relativeUrl.startsWith("http") -> relativeUrl
            relativeUrl.startsWith("/") -> "https://www.nintendo.com$relativeUrl"
            else -> "https://www.nintendo.com/it-it/"
        }

        val imageUrl = doc.imageUrlH16x9S
            ?: doc.wishlistEmailBanner640w
            ?: doc.imageUrlSqS

        return Deal(
            id = id,
            nsuid = nsuid,
            title = title,
            publisher = doc.publisher?.trim().orEmpty().ifBlank { "Nintendo eShop" },
            platform = platform,
            regularPriceEur = normalizedRegular,
            discountedPriceEur = discounted,
            discountPercent = computedPct,
            endsAtIso = live?.discountPrice?.endDatetime,
            imageUrl = imageUrl,
            eshopUrl = eshopUrl,
            popularityRank = rank
        )
    }

    private fun mergeByIdPreservingRank(
        primary: List<SolrDoc>,
        secondary: List<SolrDoc>
    ): List<SolrDoc> {
        val seen = HashSet<String>(primary.size + secondary.size)
        val merged = ArrayList<SolrDoc>(primary.size + secondary.size)
        (primary + secondary).forEach { doc ->
            val id = doc.fsId
            if (!id.isNullOrBlank() && seen.add(id)) {
                merged.add(doc)
            }
        }
        return merged
    }

    private fun pickPrimaryNsuid(nsuids: List<String>?): String? {
        if (nsuids.isNullOrEmpty()) return null
        return nsuids.firstOrNull { it.startsWith("7001") } ?: nsuids.firstOrNull()
    }

    private fun detectPlatform(systemTypes: List<String>?): Platform {
        val joined = systemTypes.orEmpty().joinToString(",").lowercase()
        return if (joined.contains("nintendoswitch2")) {
            Platform.SWITCH_2
        } else {
            Platform.SWITCH_1
        }
    }

    companion object {
        const val SEARCH_URL = "https://search.nintendo-europe.com/it/select"
        const val PRICE_URL = "https://api.ec.nintendo.com/v1/price"
        private const val USER_AGENT = "SwitchPulse/1.0 (Android)"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
internal data class SolrSearchResponse(
    val response: SolrResponseBody = SolrResponseBody()
)

@Serializable
internal data class SolrResponseBody(
    val docs: List<SolrDoc> = emptyList()
)

@Serializable
internal data class SolrDoc(
    @SerialName("fs_id") val fsId: String? = null,
    val title: String? = null,
    val publisher: String? = null,
    @SerialName("system_type") val systemType: List<String>? = null,
    val url: String? = null,
    @SerialName("image_url_h16x9_s") val imageUrlH16x9S: String? = null,
    @SerialName("image_url_sq_s") val imageUrlSqS: String? = null,
    @SerialName("wishlist_email_banner640w_image_url_s") val wishlistEmailBanner640w: String? = null,
    @SerialName("nsuid_txt") val nsuidTxt: List<String>? = null,
    @SerialName("price_regular_f") val priceRegularF: Double? = null,
    @SerialName("price_discounted_f") val priceDiscountedF: Double? = null,
    @SerialName("price_discount_percentage_f") val priceDiscountPercentageF: Double? = null
)

@Serializable
internal data class NintendoPriceResponse(
    val prices: List<PriceEntry> = emptyList()
)

@Serializable
internal data class PriceEntry(
    @SerialName("title_id") val titleId: Long,
    @SerialName("regular_price") val regularPrice: PriceAmount? = null,
    @SerialName("discount_price") val discountPrice: DiscountPriceAmount? = null
)

@Serializable
internal data class PriceAmount(
    @SerialName("raw_value") val rawValue: String? = null
)

@Serializable
internal data class DiscountPriceAmount(
    @SerialName("raw_value") val rawValue: String? = null,
    @SerialName("end_datetime") val endDatetime: String? = null
)
