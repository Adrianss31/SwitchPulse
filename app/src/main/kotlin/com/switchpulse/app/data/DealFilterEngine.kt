package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FeedSortMode
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import java.time.Duration
import java.time.Instant
import java.util.Locale

object DealFilterEngine {

    fun filterTopTen(
        rawDeals: List<Deal>,
        settings: FilterSettings,
        platformOverride: PlatformFilter = settings.appPlatformFilter,
        limit: Int = 10
    ): List<Deal> {
        val recommended = RecommendationEngine.applyRecommendations(rawDeals, settings)
        return rankEligibleDeals(
            scoredDeals = recommended,
            settings = settings,
            platformOverride = platformOverride,
            limit = limit
        )
    }

    /**
     * Returns the full deduplicated catalog matching filters & local/AI scoring (uncapped),
     * enabling global search and urgent-expiry filtering across all fetched eShop deals.
     */
    fun filterAllEligible(
        rawDeals: List<Deal>,
        settings: FilterSettings,
        platformOverride: PlatformFilter = settings.appPlatformFilter
    ): List<Deal> {
        val recommended = RecommendationEngine.applyRecommendations(rawDeals, settings)
        val ranked = rankEligibleDeals(
            scoredDeals = recommended,
            settings = settings,
            platformOverride = platformOverride,
            limit = Int.MAX_VALUE,
            applyFeedOnlyFilters = true
        )
        return sortFeedDeals(ranked, settings.feedSortMode)
    }

    fun sortFeedDeals(deals: List<Deal>, sortMode: FeedSortMode): List<Deal> = when (sortMode) {
        FeedSortMode.PERSONAL -> deals
        FeedSortMode.DISCOUNT -> deals.sortedWith(
            compareByDescending<Deal> { it.discountPercent }
                .thenByDescending { if (it.personalScore > 0.0) it.personalScore else it.hotScore }
        )
        FeedSortMode.PRICE_ASC -> deals.sortedWith(
            compareBy<Deal> { it.discountedPriceEur }
                .thenByDescending { if (it.personalScore > 0.0) it.personalScore else it.hotScore }
        )
    }

    fun countFeedMatches(
        rawDeals: List<Deal>,
        settings: FilterSettings,
        limit: Int = 30
    ): Int = filterAllEligible(rawDeals, settings, settings.appPlatformFilter).take(limit).size

    /**
     * Computes rank changes between [previousIds] (captured before a data refresh) and [currentIds].
     * Positive delta means the game climbed up in rank (e.g., 5 -> 3 yields +2).
     */
    fun computeRankDeltas(
        currentIds: List<String>,
        previousIds: List<String>
    ): Map<String, Int> {
        if (previousIds.isEmpty() || currentIds.isEmpty()) return emptyMap()
        val prevIndexById = previousIds.withIndex().associate { it.value to it.index }
        val result = HashMap<String, Int>(currentIds.size)
        currentIds.forEachIndexed { currentIdx, id ->
            val prevIdx = prevIndexById[id] ?: return@forEachIndexed
            val delta = prevIdx - currentIdx
            if (delta != 0) result[id] = delta
        }
        return result
    }

    /**
     * Returns eligible, deduplicated deal candidates ranked by the same baseline local score used
     * by [filterTopTen] so the AI evaluates every title likely to appear in the Top 30 feed.
     */
    fun selectCandidatesForAi(
        rawDeals: List<Deal>,
        settings: FilterSettings,
        limit: Int = 40
    ): List<Deal> {
        val rawById = rawDeals.associateBy { it.id }
        val localBaseline = RecommendationEngine.applyRecommendations(
            deals = rawDeals.map { it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null) },
            settings = settings
        )
        return rankEligibleDeals(
            scoredDeals = localBaseline,
            settings = settings,
            platformOverride = settings.appPlatformFilter,
            limit = limit
        ).mapNotNull { scored -> rawById[scored.id] }
    }

    private fun rankEligibleDeals(
        scoredDeals: List<Deal>,
        settings: FilterSettings,
        platformOverride: PlatformFilter,
        limit: Int,
        applyFeedOnlyFilters: Boolean = false
    ): List<Deal> {
        val hasScores = scoredDeals.any { it.hotScore > 0.0 || it.personalScore > 0.0 }
        val ownedGames = settings.likedGames.filter { it.isOwned }
        val ownedIds = ownedGames.map { it.id }.toSet()
        val ownedNormTitles = ownedGames.map { DealTitleNormalizer.normalize(it.title) }.toSet()

        val filtered = scoredDeals
            .asSequence()
            .filter { deal -> deal.id !in settings.blacklistIds }
            .filter { deal ->
                deal.id !in ownedIds && DealTitleNormalizer.normalize(deal.title) !in ownedNormTitles
            }
            .filter { deal ->
                !settings.forYouOnlyMode || deal.matchSentiment != DealMatchSentiment.DISCOURAGED
            }
            .filter { deal ->
                !applyFeedOnlyFilters || !settings.expiringSoonOnly || isUrgentExpiry(deal.endsAtIso)
            }
            .filter { deal -> deal.discountPercent >= settings.minDiscountPercent }
            .filter { deal -> deal.regularPriceEur >= settings.minRegularPriceEur }
            .filter { deal ->
                when (platformOverride) {
                    PlatformFilter.ALL -> true
                    PlatformFilter.SWITCH_2 -> deal.platform == Platform.SWITCH_2
                    PlatformFilter.SWITCH_1 -> deal.platform == Platform.SWITCH_1
                }
            }

        val ordered = if (hasScores) {
            filtered.sortedWith(
                compareByDescending<Deal> { if (it.personalScore > 0.0) it.personalScore else it.hotScore }
                    .thenByDescending { it.platform == Platform.SWITCH_2 }
                    .thenBy { it.popularityRank }
            )
        } else {
            filtered.sortedBy { it.popularityRank }
        }

        return ordered
            .distinctBy { deal -> DealTitleNormalizer.normalize(itOrSelf(deal)) }
            .take(limit)
            .toList()
    }

    private fun itOrSelf(deal: Deal): String = deal.title

    fun formatEur(amount: Double): String {
        return String.format(Locale.ITALY, "%.2f €", amount)
    }

    fun formatExpiryShort(endsAtIso: String?, now: Instant = Instant.now()): String? {
        if (endsAtIso.isNullOrBlank()) return null
        val end = runCatching { Instant.parse(endsAtIso) }.getOrNull() ?: return null
        val hours = Duration.between(now, end).toHours()
        return when {
            hours < 0 -> null
            hours < 24 -> "Scade oggi"
            hours < 48 -> "Scade domani"
            else -> "${hours / 24}g rimasti"
        }
    }

    fun isUrgentExpiry(endsAtIso: String?, now: Instant = Instant.now()): Boolean {
        if (endsAtIso.isNullOrBlank()) return false
        val end = runCatching { Instant.parse(endsAtIso) }.getOrNull() ?: return false
        val hours = Duration.between(now, end).toHours()
        return hours in 0..47
    }
}
