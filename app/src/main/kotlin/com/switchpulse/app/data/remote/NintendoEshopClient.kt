package com.switchpulse.app.data.remote

import com.switchpulse.app.data.RecommendationEngine
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt

class NintendoEshopClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val searchBaseUrl: String = SEARCH_URL,
    private val priceBaseUrl: String = PRICE_URL,
    private val chartsUrl: String = CHARTS_URL,
    private val steamSearchUrl: String = STEAM_SEARCH_URL,
    private val steamBrowseItemsUrl: String = STEAM_BROWSE_ITEMS_URL,
    private val steamTagListUrl: String = STEAM_TAG_LIST_URL,
    private val clock: Clock = Clock.systemUTC()
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedSteamTagNames: Map<Int, String> = emptyMap()

    /**
     * Fetches discounted Switch & Switch 2 deals across popularity, Switch 2, recent releases,
     * optional liked-category niches, and Nintendo IT Best Sellers chart, then enriches shortlisted
     * candidates via Steam batch browse metadata (reviews, sentiment %, micro-tags, developer).
     */
    suspend fun fetchPopularDeals(likedGames: List<LikedGame> = emptyList()): List<Deal> = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val recentCutoffIso = RECENT_CUTOFF_FORMATTER.format(
            now.minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)
        )
        val preferredCats = RecommendationEngine.topLikedCategories(likedGames, limit = 3)

        val (generalDocs, switch2Docs, recentDocs, tasteDocs, chartEntries) = coroutineScope {
            val generalDeferred = async {
                querySearch(
                    fq = "type:GAME AND price_has_discount_b:true AND (system_type:*nintendoswitch* OR system_type:*nintendoswitch2*)",
                    rows = 350
                )
            }
            val switch2Deferred = async {
                querySearch(
                    fq = "type:GAME AND price_has_discount_b:true AND system_type:*nintendoswitch2*",
                    rows = 150
                )
            }
            val recentDeferred = async {
                runCatching {
                    querySearch(
                        fq = "type:GAME AND price_has_discount_b:true AND dates_released_dts:[$recentCutoffIso TO *] AND (system_type:*nintendoswitch* OR system_type:*nintendoswitch2*)",
                        rows = 180
                    )
                }.getOrDefault(emptyList())
            }
            val tasteDeferred = async {
                if (preferredCats.isEmpty()) {
                    emptyList()
                } else {
                    val catClause = preferredCats.joinToString(" OR ")
                    runCatching {
                        querySearch(
                            fq = "type:GAME AND price_has_discount_b:true AND game_categories_txt:($catClause) AND (system_type:*nintendoswitch* OR system_type:*nintendoswitch2*)",
                            rows = 120
                        )
                    }.getOrDefault(emptyList())
                }
            }
            val chartsDeferred = async { fetchBestSellerCharts() }

            FetchBundle(
                general = generalDeferred.await(),
                switch2 = switch2Deferred.await(),
                recent = recentDeferred.await(),
                taste = tasteDeferred.await(),
                charts = chartsDeferred.await()
            )
        }

        val chartRanks = HashMap<String, Int>(chartEntries.size)
        val discountedChartIds = ArrayList<String>()
        chartEntries.forEachIndexed { index, entry ->
            val id = entry.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            chartRanks[id] = index + 1
            if (entry.priceHasDiscountB == true) {
                discountedChartIds.add(id)
            }
        }

        val candidatePool = mergeCandidates(
            generalDocs = generalDocs,
            switch2Docs = switch2Docs,
            recentDocs = recentDocs,
            tasteDocs = tasteDocs,
            discountedChartIds = discountedChartIds
        )

        val nsuids = candidatePool.mapNotNull { pickPrimaryNsuid(it.doc.nsuidTxt) }.distinct()
        val livePrices = fetchLivePrices(nsuids)

        val baseDeals = candidatePool.mapNotNull { candidate ->
            mapDocToDeal(
                candidate = candidate,
                chartRank = chartRanks[candidate.id],
                livePrices = livePrices,
                now = now
            )
        }

        val shortlist = buildShortlist(baseDeals, preferredCats, now)
        val steamAcclaimByTitle = fetchSteamAcclaimBatch(shortlist)

        baseDeals.map { deal ->
            val baseTitle = DealTitleNormalizer.normalize(deal.title)
            val steam = steamAcclaimByTitle[baseTitle]
            val reviewCount = steam?.reviewCount ?: 0
            val positivePct = steam?.positivePercent
            val finalScore = computeHotScore(
                deal = deal,
                steamReviewCount = reviewCount,
                steamPositivePercent = positivePct,
                wasShortlisted = baseTitle in steamAcclaimByTitle,
                now = now
            )
            deal.copy(
                steamAppId = steam?.appId,
                steamReviewCount = reviewCount,
                steamPositivePercent = positivePct,
                steamTagIds = steam?.tagIds.orEmpty(),
                steamTagNames = steam?.tagNames.orEmpty(),
                developer = steam?.developer,
                excerpt = steam?.shortDescription ?: deal.excerpt,
                hotScore = finalScore
            )
        }
    }

    /**
     * Searches Nintendo Switch / Switch 2 games by title (discounted or full price)
     * and enriches matched items with Steam micro-tags so users can seed their library & ratings.
     */
    suspend fun searchGamesForProfile(query: String): List<LikedGame> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val docs = runCatching {
            querySearch(
                q = cleanQuery,
                fq = "type:GAME AND (system_type:*nintendoswitch* OR system_type:*nintendoswitch2*)",
                rows = 10
            )
        }.getOrDefault(emptyList())

        val candidates = docs.mapNotNull { doc ->
            val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = doc.title?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val imageUrl = resolveDocImageUrl(doc)
            LikedGame(
                id = id,
                title = title,
                publisher = doc.publisher?.trim().orEmpty(),
                imageUrl = imageUrl,
                eshopUrl = DealTitleNormalizer.resolveNintendoEshopUrl(doc.url),
                gameCategories = doc.gameCategoriesTxt.orEmpty().map { it.lowercase() },
                excerpt = doc.excerpt?.trim(),
                rating = null,
                isOwned = false
            )
        }.distinctBy { DealTitleNormalizer.normalize(it.title) }

        if (candidates.isEmpty()) return@withContext emptyList()

        val steamByTitle = fetchSteamAcclaimForTitles(
            titles = candidates.map { it.title to it.publisher }
        )
        candidates.map { game ->
            val steam = steamByTitle[DealTitleNormalizer.normalize(game.title)]
            if (steam == null) game
            else game.copy(
                developer = steam.developer ?: game.developer,
                steamTagIds = steam.tagIds,
                steamTagNames = steam.tagNames,
                excerpt = steam.shortDescription ?: game.excerpt
            )
        }
    }

    /**
     * Resolves real Nintendo eShop cover artwork, URL, publisher and current pricing (discounted or full-price)
     * for AI-recommended games so discovery picks render cleanly and link straight to eShop/Library.
     */
    suspend fun enrichDiscoveryPicks(picks: List<AiDiscoveryPick>): List<AiDiscoveryPick> = withContext(Dispatchers.IO) {
        if (picks.isEmpty()) return@withContext emptyList()
        val semaphore = Semaphore(4)
        coroutineScope {
            picks.map { pick ->
                async(Dispatchers.IO) {
                    val normTarget = DealTitleNormalizer.normalize(pick.title)
                    val docs = runCatching {
                        semaphore.withPermit {
                            querySearch(
                                q = normTarget,
                                fq = "type:GAME AND (system_type:*nintendoswitch* OR system_type:*nintendoswitch2*)",
                                rows = 4
                            )
                        }
                    }.getOrDefault(emptyList())

                    val matched = docs.firstOrNull { doc ->
                        DealTitleNormalizer.normalize(doc.title.orEmpty()) == normTarget
                    } ?: docs.firstOrNull { doc ->
                        val docNorm = DealTitleNormalizer.normalize(doc.title.orEmpty())
                        docNorm.contains(normTarget) || normTarget.contains(docNorm)
                    } ?: docs.firstOrNull()

                    if (matched == null) {
                        pick
                    } else {
                        val regular = matched.priceRegularF?.takeIf { it > 0.0 }
                        val discounted = matched.priceDiscountedF?.takeIf { it > 0.0 }
                        val pct = matched.priceDiscountPercentageF?.toInt()?.coerceIn(0, 99) ?: 0
                        val activePrice = if (pct > 0 && discounted != null) discounted else regular
                        pick.copy(
                            eshopId = matched.fsId?.takeIf { it.isNotBlank() },
                            publisher = matched.publisher?.trim().orEmpty(),
                            imageUrl = resolveDocImageUrl(matched),
                            eshopUrl = DealTitleNormalizer.resolveNintendoEshopUrl(matched.url),
                            gameCategories = matched.gameCategoriesTxt.orEmpty().map { it.lowercase() },
                            regularPriceEur = regular,
                            currentPriceEur = activePrice,
                            discountPercent = pct
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private fun mergeCandidates(
        generalDocs: List<SolrDoc>,
        switch2Docs: List<SolrDoc>,
        recentDocs: List<SolrDoc>,
        tasteDocs: List<SolrDoc>,
        discountedChartIds: List<String>
    ): List<RankedCandidate> {
        val byId = LinkedHashMap<String, RankedCandidate>()

        generalDocs.forEachIndexed { index, doc ->
            val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            byId[id] = RankedCandidate(id = id, doc = doc, popAll = index + 1)
        }
        switch2Docs.forEachIndexed { index, doc ->
            val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val existing = byId[id]
            if (existing == null) {
                byId[id] = RankedCandidate(id = id, doc = doc, popAll = generalDocs.size + index + 1, popSw2 = index + 1)
            } else {
                byId[id] = existing.copy(popSw2 = index + 1)
            }
        }
        recentDocs.forEachIndexed { index, doc ->
            val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val existing = byId[id]
            if (existing == null) {
                byId[id] = RankedCandidate(
                    id = id,
                    doc = doc,
                    popAll = generalDocs.size + switch2Docs.size + index + 1,
                    popRecent = index + 1
                )
            } else {
                byId[id] = existing.copy(popRecent = index + 1)
            }
        }
        tasteDocs.forEachIndexed { index, doc ->
            val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            byId.putIfAbsent(
                id,
                RankedCandidate(
                    id = id,
                    doc = doc,
                    popAll = generalDocs.size + switch2Docs.size + recentDocs.size + index + 1,
                    popTaste = index + 1
                )
            )
        }

        val missingChartIds = discountedChartIds.filter { it !in byId }
        if (missingChartIds.isNotEmpty()) {
            val chartDocs = runCatching {
                querySearch(
                    fq = "fs_id:(${missingChartIds.joinToString(" OR ")})",
                    rows = missingChartIds.size.coerceAtLeast(10)
                )
            }.getOrDefault(emptyList())

            chartDocs.forEachIndexed { index, doc ->
                val id = doc.fsId?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                byId.putIfAbsent(
                    id,
                    RankedCandidate(
                        id = id,
                        doc = doc,
                        popAll = generalDocs.size + switch2Docs.size + recentDocs.size + tasteDocs.size + index + 1
                    )
                )
            }
        }

        return byId.values.toList()
    }

    private fun querySearch(fq: String, rows: Int, q: String = "*"): List<SolrDoc> {
        val url = searchBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fq", fq)
            .addQueryParameter("rows", rows.toString())
            .addQueryParameter("start", "0")
            .addQueryParameter("wt", "json")
            .apply {
                if (q == "*") addQueryParameter("sort", "popularity asc")
            }
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

    private fun fetchBestSellerCharts(): List<NintendoChartEntry> = runCatching {
        val request = Request.Builder()
            .url(chartsUrl)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Origin", "https://www.nintendo.com")
            .header("Referer", "https://www.nintendo.com/")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            json.decodeFromString<List<NintendoChartEntry>>(body)
        }
    }.getOrDefault(emptyList())

    private suspend fun fetchLivePrices(nsuids: List<String>): Map<String, PriceEntry> = coroutineScope {
        if (nsuids.isEmpty()) return@coroutineScope emptyMap()
        val semaphore = Semaphore(4)
        val chunks = nsuids.chunked(50)

        val entries = chunks.map { chunk ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchPriceChunk(chunk)
                }
            }
        }.awaitAll().flatten()

        entries.associateBy { it.titleId.toString() }
    }

    private fun fetchPriceChunk(chunk: List<String>): List<PriceEntry> = runCatching {
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

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            json.decodeFromString<NintendoPriceResponse>(body).prices
        }
    }.getOrDefault(emptyList())

    internal fun mapDocToDeal(
        candidate: RankedCandidate,
        chartRank: Int?,
        livePrices: Map<String, PriceEntry>,
        now: Instant = clock.instant()
    ): Deal? {
        val doc = candidate.doc
        val id = candidate.id
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
            discounted / (1.0 - computedPct / 100.0)
        }

        val platform = detectPlatform(doc.systemType)
        val eshopUrl = DealTitleNormalizer.resolveNintendoEshopUrl(doc.url)

        val imageUrl = resolveDocImageUrl(doc)

        val publisher = doc.publisher?.trim().orEmpty().ifBlank { "Nintendo eShop" }
        val releasedAtIso = doc.datesReleasedDts?.firstOrNull()
        val startsAtIso = live?.discountPrice?.startDatetime
        val effectivePopRank = minOf(
            candidate.popAll,
            candidate.popRecent ?: Int.MAX_VALUE,
            candidate.popTaste ?: Int.MAX_VALUE
        )

        val deal = Deal(
            id = id,
            nsuid = nsuid,
            title = title,
            publisher = publisher,
            platform = platform,
            regularPriceEur = normalizedRegular,
            discountedPriceEur = discounted,
            discountPercent = computedPct,
            endsAtIso = live?.discountPrice?.endDatetime,
            imageUrl = imageUrl,
            eshopUrl = eshopUrl,
            popularityRank = effectivePopRank,
            startsAtIso = startsAtIso,
            releasedAtIso = releasedAtIso,
            chartRank = chartRank,
            gameCategories = doc.gameCategoriesTxt.orEmpty().map { it.lowercase() },
            excerpt = doc.excerpt?.trim()?.takeIf { it.isNotBlank() }
        )

        return deal.copy(
            hotScore = computePreScore(deal = deal, sw2Rank = candidate.popSw2, now = now)
        )
    }

    /**
     * Builds a shortlist (~75-85 titles) across Best Sellers, overall pre-score,
     * fresh discounts, Switch 2 highlights, and liked categories for fast batch Steam enrichment.
     */
    private fun buildShortlist(
        deals: List<Deal>,
        preferredCategories: List<String>,
        now: Instant
    ): List<Deal> {
        val eligible = deals.filter { it.regularPriceEur >= 5.0 && it.discountPercent >= 20 }
        val deduped = eligible
            .sortedByDescending { it.hotScore + if (it.platform == Platform.SWITCH_2) 4.0 else 0.0 }
            .distinctBy { DealTitleNormalizer.normalize(it.title) }

        val picked = LinkedHashMap<String, Deal>()
        deduped.filter { it.chartRank != null }.forEach { picked[it.id] = it }
        deduped.take(42).forEach { picked[it.id] = it }
        deduped.filter { deal ->
            discountAgeDays(deal.startsAtIso, now) <= 7.5 &&
                deal.discountPercent >= 35 &&
                deal.regularPriceEur >= 10.0
        }.take(24).forEach { picked[it.id] = it }
        deduped.filter { it.platform == Platform.SWITCH_2 }
            .take(22)
            .forEach { picked[it.id] = it }

        if (preferredCategories.isNotEmpty()) {
            val catSet = preferredCategories.toSet()
            deduped.filter { deal -> deal.gameCategories.any { it in catSet } }
                .take(22)
                .forEach { picked[it.id] = it }
        }

        return picked.values.toList()
    }

    private suspend fun fetchSteamAcclaimBatch(shortlist: List<Deal>): Map<String, SteamAcclaim> {
        return fetchSteamAcclaimForTitles(shortlist.map { it.title to it.publisher })
    }

    private suspend fun fetchSteamAcclaimForTitles(
        titles: List<Pair<String, String>>
    ): Map<String, SteamAcclaim> = coroutineScope {
        val semaphore = Semaphore(8)
        val uniqueTargets = titles
            .filterNot { (_, pub) -> isNintendoFirstParty(pub) }
            .map { (title, _) -> DealTitleNormalizer.normalize(title) }
            .distinct()

        if (uniqueTargets.isEmpty()) return@coroutineScope emptyMap()

        val tagMapDeferred = async(Dispatchers.IO) { ensureTagNamesLoaded() }
        val appIdsByNormTitle = uniqueTargets.map { normTitle ->
            async(Dispatchers.IO) {
                val appId = semaphore.withPermit { lookupSteamAppId(normTitle) }
                normTitle to appId
            }
        }.awaitAll().mapNotNull { (norm, appId) ->
            appId?.let { norm to it }
        }

        if (appIdsByNormTitle.isEmpty()) return@coroutineScope emptyMap()

        val tagMap = tagMapDeferred.await()
        val uniqueAppIds = appIdsByNormTitle.map { it.second }.distinct()
        val browseById = fetchSteamBrowseItemsBatch(uniqueAppIds, tagMap)

        appIdsByNormTitle.associate { (normTitle, appId) ->
            val enriched = (browseById[appId] ?: SteamAcclaim()).copy(appId = appId)
            normTitle to enriched
        }
    }

    private fun lookupSteamAppId(normalizedTitle: String): Int? {
        val searchUrl = steamSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("term", normalizedTitle)
            .addQueryParameter("cc", "IT")
            .build()

        val searchBody = executeGet(searchUrl.toString()) ?: return null
        val searchItems = runCatching {
            json.decodeFromString<SteamStoreSearchResponse>(searchBody).items
        }.getOrDefault(emptyList())

        return searchItems.firstOrNull { item ->
            val rawName = item.name.orEmpty()
            val lower = rawName.lowercase()
            if (IGNORED_STEAM_SUFFIXES.any { lower.contains(it) }) return@firstOrNull false
            val candidateNorm = DealTitleNormalizer.normalize(rawName)
            candidateNorm == normalizedTitle ||
                (normalizedTitle.length >= 6 &&
                    (candidateNorm.startsWith("$normalizedTitle ") || normalizedTitle.startsWith("$candidateNorm ")))
        }?.id
    }

    private suspend fun fetchSteamBrowseItemsBatch(
        appIds: List<Int>,
        tagNames: Map<Int, String>
    ): Map<Int, SteamAcclaim> = coroutineScope {
        if (appIds.isEmpty()) return@coroutineScope emptyMap()
        val semaphore = Semaphore(3)

        val items = appIds.chunked(45).map { chunk ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    fetchBrowseChunk(chunk)
                }
            }
        }.awaitAll().flatten()

        items.associate { item ->
            val tags = item.tagids.orEmpty()
            val resolvedNames = tags.map { id -> tagNames[id] ?: "" }
            val dev = item.basicInfo?.developers?.firstOrNull()?.name?.trim()?.takeIf { it.isNotBlank() }
            val shortDesc = item.basicInfo?.shortDescription?.trim()?.takeIf { it.isNotBlank() }
            val summary = item.reviews?.summaryFiltered
            item.appid to SteamAcclaim(
                reviewCount = summary?.reviewCount ?: 0,
                positivePercent = summary?.percentPositive,
                tagIds = tags,
                tagNames = resolvedNames,
                developer = dev,
                shortDescription = shortDesc
            )
        }
    }

    private fun fetchBrowseChunk(appIds: List<Int>): List<SteamBrowseStoreItem> = runCatching {
        val payload = SteamBrowseInput(
            ids = appIds.map { SteamBrowseId(appid = it) },
            context = SteamBrowseContext(language = "italian", countryCode = "IT"),
            dataRequest = SteamBrowseDataRequest(
                includeTagCount = 10,
                includeReviews = true,
                includeBasicInfo = true
            )
        )
        val url = steamBrowseItemsUrl.toHttpUrl().newBuilder()
            .addQueryParameter("input_json", json.encodeToString(payload))
            .build()

        val body = executeGet(url.toString()) ?: return emptyList()
        json.decodeFromString<SteamBrowseResponse>(body).response.storeItems
    }.getOrDefault(emptyList())

    private fun ensureTagNamesLoaded(): Map<Int, String> {
        val existing = cachedSteamTagNames
        if (existing.isNotEmpty()) return existing

        val loaded = runCatching {
            val body = executeGet(steamTagListUrl) ?: return@runCatching emptyMap()
            json.decodeFromString<SteamTagListResponse>(body)
                .response
                .tags
                .associate { it.tagid to it.name }
        }.getOrDefault(emptyMap())

        if (loaded.isNotEmpty()) {
            cachedSteamTagNames = loaded
        }
        return loaded
    }

    private fun executeGet(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    internal fun computePreScore(deal: Deal, sw2Rank: Int?, now: Instant): Double {
        val ageDays = discountAgeDays(deal.startsAtIso, now)
        val freshPts = freshnessScore(ageDays, maxFreshBoost = 30.0)
        val chartPts = deal.chartRank?.let { ((50 - it).coerceAtLeast(1)) * 1.1 } ?: 0.0
        val popPts = if (deal.popularityRank < 900) {
            (22.0 - ln(1.0 + deal.popularityRank) * 3.2).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val sw2Pts = sw2Rank?.let { (16.0 - ln(1.0 + it) * 2.8).coerceAtLeast(0.0) } ?: 0.0
        val discPts = min(deal.discountPercent, 85) * 0.35 + if (deal.regularPriceEur >= 12.99) 6.0 else 0.0
        val tierPts = when {
            isNintendoFirstParty(deal.publisher) -> 20.0
            isKnownMajorPublisher(deal.publisher) -> 12.0
            else -> 0.0
        }
        return chartPts + popPts + sw2Pts + freshPts + discPts + tierPts
    }

    internal fun computeHotScore(
        deal: Deal,
        steamReviewCount: Int,
        steamPositivePercent: Int?,
        wasShortlisted: Boolean,
        now: Instant
    ): Double {
        val isNintendo = isNintendoFirstParty(deal.publisher)
        val isMajor = isKnownMajorPublisher(deal.publisher)
        val acclaimPts = when {
            isNintendo -> 34.0
            steamReviewCount >= 300 -> {
                val base = log10(steamReviewCount + 1.0) * 7.6
                val sentimentAdj = when {
                    steamPositivePercent != null && steamPositivePercent < 60 -> -12.0
                    steamPositivePercent != null && steamPositivePercent >= 90 -> 4.0
                    else -> 0.0
                }
                base + sentimentAdj
            }
            deal.chartRank != null -> 18.0
            isMajor -> 10.0
            wasShortlisted -> -12.0
            else -> -14.0
        }
        val chartPts = deal.chartRank?.let { ((50 - it).coerceAtLeast(1)) * 0.65 } ?: 0.0
        val popPts = if (deal.popularityRank < 900) {
            (12.0 - ln(1.0 + deal.popularityRank) * 1.8).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val freshPts = freshnessScore(discountAgeDays(deal.startsAtIso, now), maxFreshBoost = 32.0, baseRecentBonus = 4.0)
        val discPts = min(deal.discountPercent, 85) * 0.34 + if (deal.regularPriceEur >= 14.0) 5.0 else 0.0
        val releaseYear = deal.releasedAtIso?.take(4)?.toIntOrNull() ?: 2000
        val currentYear = now.atZone(ZoneOffset.UTC).year
        val recencyPts = when {
            releaseYear >= currentYear - 1 -> 10.0
            releaseYear >= currentYear - 2 -> 4.0
            else -> 0.0
        }

        // Shift upward so legitimate scored items stay strictly > 0.0
        return (acclaimPts + chartPts + popPts + freshPts + discPts + recencyPts + 25.0)
            .coerceAtLeast(0.1)
    }

    private fun freshnessScore(
        ageDays: Double,
        maxFreshBoost: Double,
        baseRecentBonus: Double = 0.0
    ): Double = when {
        ageDays <= 7.0 -> ((7.0 - ageDays) / 7.0) * maxFreshBoost + baseRecentBonus
        ageDays <= 14.0 -> ((14.0 - ageDays) / 14.0) * 8.0
        else -> -((ageDays - 14.0) * 1.2).coerceAtMost(18.0)
    }

    private fun discountAgeDays(startsAtIso: String?, now: Instant): Double {
        if (startsAtIso.isNullOrBlank()) return 12.0
        val start = runCatching { Instant.parse(startsAtIso) }.getOrNull() ?: return 12.0
        val hours = Duration.between(start, now).toHours().coerceAtLeast(0)
        return hours / 24.0
    }

    private fun resolveDocImageUrl(doc: SolrDoc): String? = listOf(
        doc.imageUrlH16x9S,
        doc.wishlistEmailBanner640w,
        doc.imageUrlH2x1S,
        doc.imageUrlSqS,
        doc.imageUrl
    ).firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun isNintendoFirstParty(publisher: String): Boolean =
        publisher.lowercase().contains("nintendo")

    private fun isKnownMajorPublisher(publisher: String): Boolean {
        val lower = publisher.lowercase()
        return KNOWN_MAJOR_PUBLISHERS.any { lower.contains(it) }
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
        const val CHARTS_URL = "https://api.games.nintendo-europe.com/charts?pageLocale=it_IT"
        const val STEAM_SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
        const val STEAM_BROWSE_ITEMS_URL = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/"
        const val STEAM_TAG_LIST_URL = "https://api.steampowered.com/IStoreService/GetTagList/v1/?language=italian"

        private const val USER_AGENT = "SwitchPulse/1.1 (Android)"
        private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36"
        private const val RECENT_WINDOW_DAYS = 730L
        private val RECENT_CUTOFF_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'")
        private val IGNORED_STEAM_SUFFIXES = listOf("soundtrack", " ost", "artbook", "demo")

        private val KNOWN_MAJOR_PUBLISHERS = listOf(
            "nintendo", "square enix", "capcom", "rockstar", "bandai namco", "sega", "atlus",
            "devolver", "team17", "ubisoft", "bethesda", "2k", "konami", "koei tecmo",
            "warner", "electronic arts", "ea ", "annapurna", "raw fury", "supergiant",
            "team cherry", "klei", "fromsoftware", "cd projekt", "dotemu", "focus",
            "thq nordic", "nis america", "spike chunsoft", "marvelous", "curve",
            "chucklefish", "hooded horse", "aspyr", "nightdive", "coffee stain",
            "humble", "milestone", "nacon", "atari", "abylight", "playdead",
            "tinybuild", "kepler", "disney", "outright"
        )

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private data class FetchBundle(
    val general: List<SolrDoc>,
    val switch2: List<SolrDoc>,
    val recent: List<SolrDoc>,
    val taste: List<SolrDoc>,
    val charts: List<NintendoChartEntry>
)

internal data class RankedCandidate(
    val id: String,
    val doc: SolrDoc,
    val popAll: Int,
    val popSw2: Int? = null,
    val popRecent: Int? = null,
    val popTaste: Int? = null
)

internal data class SteamAcclaim(
    val appId: Int? = null,
    val reviewCount: Int = 0,
    val positivePercent: Int? = null,
    val tagIds: List<Int> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val developer: String? = null,
    val shortDescription: String? = null
)

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
    val excerpt: String? = null,
    @SerialName("game_categories_txt") val gameCategoriesTxt: List<String>? = null,
    @SerialName("system_type") val systemType: List<String>? = null,
    val url: String? = null,
    @SerialName("dates_released_dts") val datesReleasedDts: List<String>? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("image_url_h16x9_s") val imageUrlH16x9S: String? = null,
    @SerialName("image_url_h2x1_s") val imageUrlH2x1S: String? = null,
    @SerialName("image_url_sq_s") val imageUrlSqS: String? = null,
    @SerialName("wishlist_email_banner640w_image_url_s") val wishlistEmailBanner640w: String? = null,
    @SerialName("nsuid_txt") val nsuidTxt: List<String>? = null,
    @SerialName("price_regular_f") val priceRegularF: Double? = null,
    @SerialName("price_discounted_f") val priceDiscountedF: Double? = null,
    @SerialName("price_discount_percentage_f") val priceDiscountPercentageF: Double? = null
)

@Serializable
internal data class NintendoChartEntry(
    @SerialName("fs_id") val fsId: String? = null,
    @SerialName("price_has_discount_b") val priceHasDiscountB: Boolean? = null
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
    @SerialName("start_datetime") val startDatetime: String? = null,
    @SerialName("end_datetime") val endDatetime: String? = null
)

@Serializable
internal data class SteamStoreSearchResponse(
    val items: List<SteamStoreSearchItem> = emptyList()
)

@Serializable
internal data class SteamStoreSearchItem(
    val id: Int,
    val name: String? = null
)

@Serializable
internal data class SteamBrowseInput(
    val ids: List<SteamBrowseId>,
    val context: SteamBrowseContext,
    @SerialName("data_request") val dataRequest: SteamBrowseDataRequest
)

@Serializable
internal data class SteamBrowseId(
    val appid: Int
)

@Serializable
internal data class SteamBrowseContext(
    val language: String,
    @SerialName("country_code") val countryCode: String
)

@Serializable
internal data class SteamBrowseDataRequest(
    @SerialName("include_tag_count") val includeTagCount: Int,
    @SerialName("include_reviews") val includeReviews: Boolean,
    @SerialName("include_basic_info") val includeBasicInfo: Boolean
)

@Serializable
internal data class SteamBrowseResponse(
    val response: SteamBrowseResponseBody = SteamBrowseResponseBody()
)

@Serializable
internal data class SteamBrowseResponseBody(
    @SerialName("store_items") val storeItems: List<SteamBrowseStoreItem> = emptyList()
)

@Serializable
internal data class SteamBrowseStoreItem(
    val appid: Int = 0,
    @SerialName("basic_info") val basicInfo: SteamBasicInfo? = null,
    val tagids: List<Int>? = null,
    val reviews: SteamBrowseReviews? = null
)

@Serializable
internal data class SteamBasicInfo(
    @SerialName("short_description") val shortDescription: String? = null,
    val developers: List<SteamCreatorInfo>? = null
)

@Serializable
internal data class SteamCreatorInfo(
    val name: String? = null
)

@Serializable
internal data class SteamBrowseReviews(
    @SerialName("summary_filtered") val summaryFiltered: SteamReviewSummary? = null
)

@Serializable
internal data class SteamReviewSummary(
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("percent_positive") val percentPositive: Int? = null
)

@Serializable
internal data class SteamTagListResponse(
    val response: SteamTagListBody = SteamTagListBody()
)

@Serializable
internal data class SteamTagListBody(
    val tags: List<SteamTagEntry> = emptyList()
)

@Serializable
internal data class SteamTagEntry(
    val tagid: Int = 0,
    val name: String = ""
)
