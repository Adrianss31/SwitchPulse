package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DealFilterEngineTest {

    private fun sampleDeal(
        id: String,
        platform: Platform,
        regular: Double,
        discounted: Double,
        pct: Int,
        rank: Int,
        endsAtIso: String? = null,
        title: String = "Game $id",
        hotScore: Double = 0.0
    ) = Deal(
        id = id,
        nsuid = "70010000000$id",
        title = title,
        publisher = "Pub",
        platform = platform,
        regularPriceEur = regular,
        discountedPriceEur = discounted,
        discountPercent = pct,
        endsAtIso = endsAtIso,
        imageUrl = null,
        eshopUrl = "https://www.nintendo.com/it-it/$id",
        popularityRank = rank,
        hotScore = hotScore
    )

    @Test
    fun `filterTopTen filters discount, regular price, blacklist, platform, and caps at 10`() {
        val raw = listOf(
            sampleDeal("1", Platform.SWITCH_2, 59.99, 8.99, 85, 1),
            sampleDeal("2", Platform.SWITCH_2, 29.99, 26.99, 10, 2), // filtered: discount < 30
            sampleDeal("3", Platform.SWITCH_1, 4.99, 0.99, 80, 3),   // filtered: regular < 10
            sampleDeal("4", Platform.SWITCH_1, 59.99, 19.79, 67, 4), // filtered: blacklisted
            sampleDeal("5", Platform.SWITCH_1, 39.99, 14.99, 63, 5)
        ) + (6..20).map { idx ->
            sampleDeal(idx.toString(), Platform.SWITCH_2, 49.99, 24.99, 50, idx)
        }

        val settings = FilterSettings(
            minDiscountPercent = 30,
            minRegularPriceEur = 10f,
            blacklistIds = setOf("4")
        )

        val allTop = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.ALL)
        assertEquals(10, allTop.size)
        assertEquals(listOf("1", "5", "6", "7", "8", "9", "10", "11", "12", "13"), allTop.map { it.id })

        val sw2Only = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.SWITCH_2)
        assertTrue(sw2Only.all { it.platform == Platform.SWITCH_2 })
        assertEquals("1", sw2Only.first().id)

        val sw1Only = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.SWITCH_1)
        assertEquals(listOf("5"), sw1Only.map { it.id })
    }

    @Test
    fun `expiry formatting detects today and tomorrow`() {
        val now = Instant.parse("2026-11-15T12:00:00Z")
        val in10Hours = now.plus(10, ChronoUnit.HOURS).toString()
        val in30Hours = now.plus(30, ChronoUnit.HOURS).toString()
        val in5Days = now.plus(5, ChronoUnit.DAYS).toString()

        assertEquals("Scade oggi", DealFilterEngine.formatExpiryShort(in10Hours, now))
        assertEquals("Scade domani", DealFilterEngine.formatExpiryShort(in30Hours, now))
        assertEquals("5g rimasti", DealFilterEngine.formatExpiryShort(in5Days, now))

        assertTrue(DealFilterEngine.isUrgentExpiry(in10Hours, now))
        assertTrue(DealFilterEngine.isUrgentExpiry(in30Hours, now))
        assertFalse(DealFilterEngine.isUrgentExpiry(in5Days, now))
    }

    @Test
    fun `filterTopTen sorts by hotScore when present and deduplicates editions per platform view`() {
        val raw = listOf(
            sampleDeal("101", Platform.SWITCH_1, 49.99, 24.99, 50, 5, title = "BALL x PIT", hotScore = 88.0),
            sampleDeal("102", Platform.SWITCH_2, 49.99, 24.99, 50, 64, title = "BALL x PIT – Nintendo Switch™ 2 Edition", hotScore = 92.5),
            sampleDeal("103", Platform.SWITCH_1, 39.99, 9.99, 75, 1, title = "Evergreen Old Sale", hotScore = 70.0)
        )
        val settings = FilterSettings(minDiscountPercent = 20, minRegularPriceEur = 5f)

        val all = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.ALL)
        assertEquals(listOf("102", "103"), all.map { it.id })

        val sw1 = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.SWITCH_1)
        assertEquals(listOf("101", "103"), sw1.map { it.id })

        val sw2 = DealFilterEngine.filterTopTen(raw, settings, PlatformFilter.SWITCH_2)
        assertEquals(listOf("102"), sw2.map { it.id })
    }
}
