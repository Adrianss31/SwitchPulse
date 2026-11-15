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
    val steamReviewCount: Int = 0,
    val metacriticScore: Int? = null,
    val hotScore: Double = 0.0
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
}

data class FilterSettings(
    val minDiscountPercent: Int = 30,
    val minRegularPriceEur: Float = 10f,
    val appPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val widgetPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val syncIntervalHours: Int = 12,
    val blacklistIds: Set<String> = emptySet(),
    val blacklistTitles: Map<String, String> = emptyMap(),
    val githubRepo: String = "",
    val autoCheckUpdates: Boolean = true,
    val lastSyncEpochMs: Long = 0L
)
