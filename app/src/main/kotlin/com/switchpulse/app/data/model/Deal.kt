package com.switchpulse.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    SWITCH_2,
    SWITCH_1;

    val shortLabel: String
        get() = when (this) {
            SWITCH_2 -> "SW2"
            SWITCH_1 -> "SW1"
        }

    val displayName: String
        get() = when (this) {
            SWITCH_2 -> "Switch 2"
            SWITCH_1 -> "Switch"
        }
}

@Serializable
enum class PlatformFilter {
    ALL,
    SWITCH_2,
    SWITCH_1;

    val label: String
        get() = when (this) {
            ALL -> "Tutti"
            SWITCH_2 -> "SW2"
            SWITCH_1 -> "SW1"
        }
}

@Serializable
enum class DealMatchSource {
    NONE,
    LOCAL,
    GEMINI
}

@Serializable
enum class DealMatchSentiment {
    NONE,
    RECOMMENDED,
    DISCOURAGED
}

@Serializable
data class Deal(
    val id: String,
    val nsuid: String?,
    val title: String,
    val publisher: String,
    val platform: Platform,
    val regularPriceEur: Double,
    val discountedPriceEur: Double,
    val discountPercent: Int,
    val endsAtIso: String?,
    val imageUrl: String?,
    val eshopUrl: String,
    val popularityRank: Int,
    val startsAtIso: String? = null,
    val releasedAtIso: String? = null,
    val chartRank: Int? = null,
    val steamAppId: Int? = null,
    val steamReviewCount: Int = 0,
    val steamPositivePercent: Int? = null,
    val metacriticScore: Int? = null,
    val hotScore: Double = 0.0,
    val steamTagIds: List<Int> = emptyList(),
    val steamTagNames: List<String> = emptyList(),
    val gameCategories: List<String> = emptyList(),
    val developer: String? = null,
    val excerpt: String? = null,
    val aiAffinityScore: Double? = null,
    val aiReason: String? = null,
    val aiDetailedReason: String? = null,
    val personalScore: Double = 0.0,
    val matchReason: String? = null,
    val matchSource: DealMatchSource = DealMatchSource.NONE,
    val matchSentiment: DealMatchSentiment = DealMatchSentiment.NONE
) {
    fun toLikedGame(
        rating: Int? = null,
        isOwned: Boolean = false
    ): LikedGame = LikedGame(
        id = id,
        title = title,
        publisher = publisher,
        developer = developer,
        imageUrl = imageUrl,
        eshopUrl = eshopUrl,
        gameCategories = gameCategories,
        steamTagIds = steamTagIds,
        steamTagNames = steamTagNames,
        excerpt = excerpt,
        rating = rating,
        isOwned = isOwned,
        addedAtMs = System.currentTimeMillis()
    )
}

/**
 * Entry in the user's personal Library & Taste Profile.
 * Supports independent 1-5 star ratings (attraction/repulsion) and "owned" status (backlog
 * excluded from deal feeds without requiring a rating).
 */
@Serializable
data class LikedGame(
    val id: String,
    val title: String,
    val publisher: String = "",
    val developer: String? = null,
    val imageUrl: String? = null,
    val eshopUrl: String? = null,
    val gameCategories: List<String> = emptyList(),
    val steamTagIds: List<Int> = emptyList(),
    val steamTagNames: List<String> = emptyList(),
    val excerpt: String? = null,
    val rating: Int? = 5,
    val isOwned: Boolean = false,
    val userNote: String? = null,
    val addedAtMs: Long = 0L
) {
    val isPositiveRating: Boolean get() = (rating ?: 0) >= 4
    val isNegativeRating: Boolean get() = rating != null && rating <= 2
}

@Serializable
data class GameExternalInsights(
    val dealId: String,
    val metacriticScore: Int? = null,
    val metacriticUrl: String? = null,
    val hltbMainHours: String? = null,
    val hltbMainExtraHours: String? = null,
    val hltbCompletionistHours: String? = null,
    val aiVerdict: String? = null,
    val summaryNote: String? = null,
    val sourceLabel: String = "",
    val profileSignature: String = "",
    val fetchedAtMs: Long = 0L
)

/**
 * Shared title normalizer for cross-edition deduplication (SW1 vs SW2) and Steam matching.
 */
object DealTitleNormalizer {
    private val TRADEMARKS = Regex("[™®©:]")
    private val SWITCH_SUFFIX = Regex("\\s*[–\\-]\\s*nintendo switch.*$", RegexOption.IGNORE_CASE)
    private val SWITCH_EDITION = Regex("\\s*nintendo switch.*edition.*$", RegexOption.IGNORE_CASE)
    private val NON_ALNUM = Regex("[^a-z0-9 ]+")
    private val MULTI_SPACE = Regex("\\s+")

    fun normalize(title: String): String {
        val cleaned = title.lowercase()
            .replace(TRADEMARKS, "")
            .replace(SWITCH_SUFFIX, "")
            .replace(SWITCH_EDITION, "")
            .replace(NON_ALNUM, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
        return cleaned.ifEmpty { title.lowercase().trim() }
    }

    fun resolveNintendoEshopUrl(rawUrl: String?): String {
        val trimmed = rawUrl.orEmpty().trim()
        return when {
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> "https://www.nintendo.com$trimmed"
            else -> "https://www.nintendo.com/it-it/"
        }
    }
}

data class FilterSettings(
    val minDiscountPercent: Int = 30,
    val minRegularPriceEur: Float = 10f,
    val appPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val widgetPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val syncIntervalHours: Int = 12,
    val blacklistIds: Set<String> = emptySet(),
    val blacklistTitles: Map<String, String> = emptyMap(),
    val likedGames: List<LikedGame> = emptyList(),
    val recommendationWeight: Int = 50,
    val forYouOnlyMode: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-4-hydrogen",
    val geminiStatusSummary: String = "",
    val geminiSignature: String = "",
    val userTastePrompt: String = "",
    val githubRepo: String = "",
    val autoCheckUpdates: Boolean = true,
    val lastSyncEpochMs: Long = 0L
)
