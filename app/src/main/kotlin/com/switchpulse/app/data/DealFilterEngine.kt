package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
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
        return rawDeals
            .asSequence()
            .filter { deal -> deal.id !in settings.blacklistIds }
            .filter { deal -> deal.discountPercent >= settings.minDiscountPercent }
            .filter { deal -> deal.regularPriceEur >= settings.minRegularPriceEur }
            .filter { deal ->
                when (platformOverride) {
                    PlatformFilter.ALL -> true
                    PlatformFilter.SWITCH_2 -> deal.platform == Platform.SWITCH_2
                    PlatformFilter.SWITCH_1 -> deal.platform == Platform.SWITCH_1
                }
            }
            .sortedBy { it.popularityRank }
            .take(limit)
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
