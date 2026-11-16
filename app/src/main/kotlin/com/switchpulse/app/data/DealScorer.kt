package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min

/**
 * Pure scoring & publisher classification engine with explicit named weights.
 * Extracted from network clients so scoring formulas can be maintained and unit-tested cleanly.
 */
object DealScorer {

    private const val CHART_BONUS_PER_RANK = 1.1
    private const val CHART_HOT_BONUS_PER_RANK = 0.65
    private const val CHART_RANK_CEILING = 50
    private const val POP_RANK_LIMIT = 900

    private const val PRE_POP_MAX = 22.0
    private const val PRE_POP_DECAY = 3.2
    private const val SW2_MAX = 16.0
    private const val SW2_DECAY = 2.8
    private const val PRE_FRESH_MAX = 30.0
    private const val PRE_DISC_WEIGHT = 0.35
    private const val PRE_REGULAR_MIN_EUR = 12.99
    private const val PRE_REGULAR_BONUS = 6.0
    private const val NINTENDO_PRE_BONUS = 20.0
    private const val MAJOR_PRE_BONUS = 12.0

    private const val NINTENDO_ACCLAIM = 34.0
    private const val STEAM_VERIFIED_MIN_REVIEWS = 300
    private const val STEAM_LOG_WEIGHT = 7.6
    private const val STEAM_NEGATIVE_SENTIMENT_PENALTY = -12.0
    private const val STEAM_HIGH_SENTIMENT_BONUS = 4.0
    private const val CHART_ONLY_ACCLAIM = 18.0
    private const val MAJOR_ONLY_ACCLAIM = 10.0
    private const val SHORTLIST_MISS_ACCLAIM = -12.0
    private const val UNVERIFIED_ACCLAIM = -14.0

    private const val HOT_POP_MAX = 12.0
    private const val HOT_POP_DECAY = 1.8
    private const val HOT_FRESH_MAX = 32.0
    private const val HOT_FRESH_RECENT_BONUS = 4.0
    private const val HOT_DISC_WEIGHT = 0.34
    private const val HOT_REGULAR_MIN_EUR = 14.0
    private const val HOT_REGULAR_BONUS = 5.0
    private const val HOT_SCORE_SHIFT = 25.0

    private val NINTENDO_EXACT_NAMES = setOf(
        "nintendo",
        "nintendo of europe",
        "nintendo of america",
        "nintendo co., ltd.",
        "nintendo co. ltd.",
        "the pokemon company",
        "the pokémon company"
    )

    private val KNOWN_MAJOR_PUBLISHERS = listOf(
        "nintendo", "square enix", "capcom", "rockstar", "bandai namco", "sega", "atlus",
        "devolver", "team17", "ubisoft", "bethesda", "2k", "konami", "koei tecmo",
        "warner", "electronic arts", "ea", "annapurna", "raw fury", "supergiant",
        "team cherry", "klei", "fromsoftware", "cd projekt", "dotemu", "focus",
        "thq nordic", "nis america", "spike chunsoft", "marvelous", "curve",
        "chucklefish", "hooded horse", "aspyr", "nightdive", "coffee stain",
        "humble", "milestone", "nacon", "atari", "abylight", "playdead",
        "tinybuild", "kepler", "disney", "outright"
    )

    private val MAJOR_PUBLISHER_REGEX = Regex(
        "\\b(?:" + KNOWN_MAJOR_PUBLISHERS.joinToString("|") { Regex.escape(it) } + ")\\b",
        RegexOption.IGNORE_CASE
    )

    fun isNintendoFirstParty(publisher: String): Boolean {
        val normalized = publisher.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return false
        return normalized in NINTENDO_EXACT_NAMES || normalized.startsWith("nintendo ")
    }

    fun isKnownMajorPublisher(publisher: String): Boolean {
        if (publisher.isBlank()) return false
        return MAJOR_PUBLISHER_REGEX.containsMatchIn(publisher)
    }

    fun isVerifiedQualityDeal(deal: Deal): Boolean =
        deal.steamReviewCount >= STEAM_VERIFIED_MIN_REVIEWS ||
            deal.chartRank != null ||
            isNintendoFirstParty(deal.publisher)

    fun computePreScore(deal: Deal, sw2Rank: Int?, now: Instant): Double {
        val ageDays = discountAgeDays(deal.startsAtIso, now)
        val freshPts = freshnessScore(ageDays, maxFreshBoost = PRE_FRESH_MAX)
        val chartPts = deal.chartRank?.let { ((CHART_RANK_CEILING - it).coerceAtLeast(1)) * CHART_BONUS_PER_RANK } ?: 0.0
        val popPts = if (deal.popularityRank < POP_RANK_LIMIT) {
            (PRE_POP_MAX - ln(1.0 + deal.popularityRank) * PRE_POP_DECAY).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val sw2Pts = sw2Rank?.let { (SW2_MAX - ln(1.0 + it) * SW2_DECAY).coerceAtLeast(0.0) } ?: 0.0
        val discPts = min(deal.discountPercent, 85) * PRE_DISC_WEIGHT +
            if (deal.regularPriceEur >= PRE_REGULAR_MIN_EUR) PRE_REGULAR_BONUS else 0.0
        val tierPts = when {
            isNintendoFirstParty(deal.publisher) -> NINTENDO_PRE_BONUS
            isKnownMajorPublisher(deal.publisher) -> MAJOR_PRE_BONUS
            else -> 0.0
        }
        return chartPts + popPts + sw2Pts + freshPts + discPts + tierPts
    }

    fun computeHotScore(
        deal: Deal,
        steamReviewCount: Int,
        steamPositivePercent: Int?,
        wasShortlisted: Boolean,
        now: Instant
    ): Double {
        val isNintendo = isNintendoFirstParty(deal.publisher)
        val isMajor = isKnownMajorPublisher(deal.publisher)
        val acclaimPts = when {
            isNintendo -> NINTENDO_ACCLAIM
            steamReviewCount >= STEAM_VERIFIED_MIN_REVIEWS -> {
                val base = log10(steamReviewCount + 1.0) * STEAM_LOG_WEIGHT
                val sentimentAdj = when {
                    steamPositivePercent != null && steamPositivePercent < 60 -> STEAM_NEGATIVE_SENTIMENT_PENALTY
                    steamPositivePercent != null && steamPositivePercent >= 90 -> STEAM_HIGH_SENTIMENT_BONUS
                    else -> 0.0
                }
                base + sentimentAdj
            }
            deal.chartRank != null -> CHART_ONLY_ACCLAIM
            isMajor -> MAJOR_ONLY_ACCLAIM
            wasShortlisted -> SHORTLIST_MISS_ACCLAIM
            else -> UNVERIFIED_ACCLAIM
        }
        val chartPts = deal.chartRank?.let { ((CHART_RANK_CEILING - it).coerceAtLeast(1)) * CHART_HOT_BONUS_PER_RANK } ?: 0.0
        val popPts = if (deal.popularityRank < POP_RANK_LIMIT) {
            (HOT_POP_MAX - ln(1.0 + deal.popularityRank) * HOT_POP_DECAY).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val freshPts = freshnessScore(
            ageDays = discountAgeDays(deal.startsAtIso, now),
            maxFreshBoost = HOT_FRESH_MAX,
            baseRecentBonus = HOT_FRESH_RECENT_BONUS
        )
        val discPts = min(deal.discountPercent, 85) * HOT_DISC_WEIGHT +
            if (deal.regularPriceEur >= HOT_REGULAR_MIN_EUR) HOT_REGULAR_BONUS else 0.0
        val releaseYear = deal.releasedAtIso?.take(4)?.toIntOrNull() ?: 2000
        val currentYear = now.atZone(ZoneOffset.UTC).year
        val recencyPts = when {
            releaseYear >= currentYear - 1 -> 10.0
            releaseYear >= currentYear - 2 -> 4.0
            else -> 0.0
        }

        return (acclaimPts + chartPts + popPts + freshPts + discPts + recencyPts + HOT_SCORE_SHIFT)
            .coerceAtLeast(0.1)
    }

    fun freshnessScore(
        ageDays: Double,
        maxFreshBoost: Double,
        baseRecentBonus: Double = 0.0
    ): Double = when {
        ageDays <= 7.0 -> ((7.0 - ageDays) / 7.0) * maxFreshBoost + baseRecentBonus
        ageDays <= 14.0 -> ((14.0 - ageDays) / 14.0) * 8.0
        else -> -((ageDays - 14.0) * 1.2).coerceAtMost(18.0)
    }

    fun discountAgeDays(startsAtIso: String?, now: Instant): Double {
        if (startsAtIso.isNullOrBlank()) return 12.0
        val start = runCatching { Instant.parse(startsAtIso) }.getOrNull() ?: return 12.0
        val hours = Duration.between(start, now).toHours().coerceAtLeast(0)
        return hours / 24.0
    }
}
