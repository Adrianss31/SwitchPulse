package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DealScorerTest {

    private fun sampleDeal(
        publisher: String,
        steamReviewCount: Int = 0,
        steamPositivePercent: Int = 0,
        chartRank: Int? = null
    ) = Deal(
        id = "1",
        nsuid = null,
        title = "Sample Game",
        publisher = publisher,
        platform = Platform.SWITCH_1,
        regularPriceEur = 39.99,
        discountedPriceEur = 19.99,
        discountPercent = 50,
        endsAtIso = null,
        imageUrl = null,
        eshopUrl = "https://www.nintendo.com/it-it/sample",
        popularityRank = 1,
        chartRank = chartRank,
        steamReviewCount = steamReviewCount,
        steamPositivePercent = steamPositivePercent
    )

    @Test
    fun `empty publisher is never treated as Nintendo first party or major publisher`() {
        assertFalse(DealScorer.isNintendoFirstParty(""))
        assertFalse(DealScorer.isKnownMajorPublisher(""))
        assertEquals("Nintendo eShop", sampleDeal("").displayPublisher)
        assertFalse(DealScorer.isVerifiedQualityDeal(sampleDeal("")))
    }

    @Test
    fun `publisher matching respects word boundaries for short tokens like ea`() {
        assertFalse(DealScorer.isKnownMajorPublisher("Idea Factory"))
        assertFalse(DealScorer.isKnownMajorPublisher("Seaside Studio"))
        assertTrue(DealScorer.isKnownMajorPublisher("EA Sports"))
        assertTrue(DealScorer.isKnownMajorPublisher("Electronic Arts"))
        assertTrue(DealScorer.isNintendoFirstParty("Nintendo of Europe"))
        assertFalse(DealScorer.isNintendoFirstParty("The Nintendo Indie Helper"))
    }
}
