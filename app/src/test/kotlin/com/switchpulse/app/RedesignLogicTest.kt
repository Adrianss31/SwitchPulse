package com.switchpulse.app

import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FeedSortMode
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameDetailModel
import com.switchpulse.app.data.model.GameDetailOrigin
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.widget.carouselSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedesignLogicTest {

    private fun sampleDeal(
        id: String,
        title: String = "Game $id",
        discount: Int = 50,
        regular: Double = 39.99,
        discounted: Double = 19.99,
        aiScore: Double? = null
    ) = Deal(
        id = id,
        nsuid = "700100000$id",
        title = title,
        publisher = "Indie Studio",
        platform = Platform.SWITCH_1,
        regularPriceEur = regular,
        discountedPriceEur = discounted,
        discountPercent = discount,
        endsAtIso = null,
        imageUrl = "https://example.com/$id.jpg",
        eshopUrl = "https://www.nintendo.com/it-it/$id",
        popularityRank = 1,
        aiAffinityScore = aiScore
    )

    @Test
    fun `computeRankDeltas returns climb and drop offsets matching previous refresh order`() {
        val prev = listOf("a", "b", "c", "d")
        val curr = listOf("c", "a", "b", "e")

        val deltas = DealFilterEngine.computeRankDeltas(curr, prev)
        assertEquals(2, deltas["c"])   // climbed from index 2 -> 0 (+2)
        assertEquals(-1, deltas["a"])  // dropped from index 0 -> 1 (-1)
        assertEquals(-1, deltas["b"])  // dropped from index 1 -> 2 (-1)
        assertNull(deltas["e"])        // newly entered
    }

    @Test
    fun `carouselSlots yields exact 3-panel window across ranks 1 to 10`() {
        assertEquals(listOf(1, 2, 3), carouselSlots(1, 10))
        assertEquals(listOf(1, 2, 3), carouselSlots(2, 10))
        assertEquals(listOf(3, 4, 5), carouselSlots(4, 10))
        assertEquals(listOf(8, 9, 10), carouselSlots(9, 10))
        assertEquals(listOf(8, 9, 10), carouselSlots(10, 10))
        assertEquals(listOf(1, 2), carouselSlots(1, 2))
        assertEquals(emptyList<Int>(), carouselSlots(1, 0))
    }

    @Test
    fun `countFeedMatches reflects draft filters accurately for filters sheet live counter`() {
        val deals = listOf(
            sampleDeal("1", discount = 70, regular = 39.99, discounted = 11.99),
            sampleDeal("2", discount = 50, regular = 29.99, discounted = 14.99),
            sampleDeal("3", discount = 20, regular = 29.99, discounted = 23.99),
            sampleDeal("4", discount = 60, regular = 4.99, discounted = 1.99)
        )

        val strict = FilterSettings(minDiscountPercent = 40, minRegularPriceEur = 10f, feedSortMode = FeedSortMode.DISCOUNT)
        assertEquals(2, DealFilterEngine.countFeedMatches(deals, strict))

        val relaxed = FilterSettings(minDiscountPercent = 20, minRegularPriceEur = 0f)
        assertEquals(4, DealFilterEngine.countFeedMatches(deals, relaxed))
    }

    @Test
    fun `GameDetailModel builds cleanly from Deal, LikedGame, and AiDiscoveryPick`() {
        val deal = sampleDeal("101", title = "Triangle Strategy", aiScore = 94.0)
            .copy(aiDetailedReason = "Tattico a turni su griglia.", isHistoricalLow = true)
        val fromDeal = GameDetailModel.fromDeal(deal, rank = 1)
        assertEquals(GameDetailOrigin.FEED_DEAL, fromDeal.origin)
        assertEquals(1, fromDeal.rank)
        assertEquals(94, fromDeal.aiScore)
        assertEquals("Imperdibile per te", fromDeal.aiHeadline)
        assertTrue(fromDeal.isHistoricalLow)

        val libGame = LikedGame(id = "fe3h", title = "Fire Emblem: Three Houses", rating = 5, isOwned = true)
        val fromLib = GameDetailModel.fromLibraryGame(libGame, matchingActiveDeal = null)
        assertEquals(GameDetailOrigin.LIBRARY_GAME, fromLib.origin)
        assertNull(fromLib.rank)
        assertNull(fromLib.discountedPriceEur)

        val pick = AiDiscoveryPick(
            title = "Into the Breach",
            pitchHook = "Scacchi con i mech",
            reason = "Partite brevi e scelte tattiche pure",
            currentPriceEur = 14.99,
            regularPriceEur = 14.99,
            discountPercent = 0
        )
        val fromPick = GameDetailModel.fromDiscoveryPick(pick, matchingActiveDeal = null)
        assertEquals(GameDetailOrigin.DISCOVERY_PICK, fromPick.origin)
        assertEquals("Scacchi con i mech", fromPick.aiHeadline)
        assertEquals("Partite brevi e scelte tattiche pure", fromPick.aiReasonText)
        assertEquals(14.99, fromPick.discountedPriceEur!!, 0.001)
    }
}
