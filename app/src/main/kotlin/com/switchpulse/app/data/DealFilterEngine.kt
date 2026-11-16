package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealTitleNormalizer
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
        val hasScores = recommended.any { it.hotScore > 0.0 || it.personalScore > 0.0 }

        val ownedGames = settings.likedGames.filter { it.isOwned }
        val ownedIds = ownedGames.map { it.id }.toSet()
        val ownedNormTitles = ownedGames.map { DealTitleNormalizer.normalize(it.title) }.toSet()

        val filtered = recommended
            .asSequence()
            .filter { deal -> deal.id !in settings.blacklistIds }
            .filter { deal ->
                deal.id !in ownedIds && DealTitleNormalizer.normalize(deal.title) !in ownedNormTitles
            }
            .filter { deal ->
                !settings.forYouOnlyMode || deal.matchSentiment != DealMatchSentiment.DISCOURAGED
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

        // Deduplicate cross-platform editions (e.g. Switch 1 + Switch 2 versions of the same title)
        // after applying the platform filter so SW1 and SW2 tabs still show their respective edition.
        return ordered
            .distinctBy { deal -> DealTitleNormalizer.normalize(deal.title) }
            .take(limit)
            .toList()
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
        val hasScores = localBaseline.any { it.hotScore > 0.0 || it.personalScore > 0.0 }

        val ownedGames = settings.likedGames.filter { it.isOwned }
        val ownedIds = ownedGames.map { it.id }.toSet()
        val ownedNormTitles = ownedGames.map { DealTitleNormalizer.normalize(it.title) }.toSet()

        val filtered = localBaseline
            .asSequence()
            .filter { deal -> deal.id !in settings.blacklistIds }
            .filter { deal ->
                deal.id !in ownedIds && DealTitleNormalizer.normalize(deal.title) !in ownedNormTitles
            }
            .filter { deal ->
                !settings.forYouOnlyMode || deal.matchSentiment != DealMatchSentiment.DISCOURAGED
            }
            .filter { deal -> deal.discountPercent >= settings.minDiscountPercent }
            .filter { deal -> deal.regularPriceEur >= settings.minRegularPriceEur }
            .filter { deal ->
                when (settings.appPlatformFilter) {
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
            .distinctBy { deal -> DealTitleNormalizer.normalize(deal.title) }
            .take(limit)
            .mapNotNull { scored -> rawById[scored.id] }
            .toList()
    }

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
