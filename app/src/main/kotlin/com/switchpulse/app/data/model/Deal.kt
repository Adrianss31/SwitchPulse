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
    val popularityRank: Int
)

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
