package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    @Test
    fun `cosine similarity boosts matching micro-tags and respects anti-shovelware guardrail`() {
        val liked = LikedGame(
            id = "hades-1",
            title = "Hades",
            publisher = "Supergiant Games",
            developer = "Supergiant Games",
            gameCategories = listOf("action", "indie"),
            steamTagIds = listOf(3959, 1716, 4885),
            steamTagNames = listOf("Roguelite", "Roguelike", "Inferno di proiettili")
        )

        val matchingRoguelite = Deal(
            id = "dead-cells",
            nsuid = null,
            title = "Dead Cells",
            publisher = "Motion Twin",
            platform = Platform.SWITCH_1,
            regularPriceEur = 24.99,
            discountedPriceEur = 12.49,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 15,
            steamReviewCount = 140000,
            hotScore = 60.0,
            steamTagIds = listOf(3959, 1716, 1628),
            steamTagNames = listOf("Roguelite", "Roguelike", "Metroidvania"),
            developer = "Motion Twin",
            gameCategories = listOf("action", "indie"),
            eshopUrl = "https://www.nintendo.com/dead-cells",
            imageUrl = ""
        )

        val unrelatedMainstream = Deal(
            id = "racing-hit",
            nsuid = null,
            title = "Generic Kart",
            publisher = "Big Publisher",
            platform = Platform.SWITCH_1,
            regularPriceEur = 59.99,
            discountedPriceEur = 29.99,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 4,
            steamReviewCount = 12000,
            hotScore = 68.0,
            steamTagIds = listOf(699, 701),
            steamTagNames = listOf("Corse", "Sport"),
            developer = "Kart Studio",
            gameCategories = listOf("racing"),
            eshopUrl = "https://www.nintendo.com/kart",
            imageUrl = ""
        )

        val unverifiedShovelware = Deal(
            id = "clone-99",
            nsuid = null,
            title = "Super Roguelite Clone 99",
            publisher = "NoName LLC",
            platform = Platform.SWITCH_1,
            regularPriceEur = 14.99,
            discountedPriceEur = 1.99,
            discountPercent = 87,
            endsAtIso = null,
            popularityRank = 45,
            steamReviewCount = 0,
            hotScore = 10.0,
            steamTagIds = listOf(3959, 1716, 4885),
            steamTagNames = listOf("Roguelite", "Roguelike", "Inferno di proiettili"),
            gameCategories = listOf("action", "indie"),
            eshopUrl = "https://www.nintendo.com/clone",
            imageUrl = ""
        )

        val settings = FilterSettings(
            minDiscountPercent = 0,
            minRegularPriceEur = 0f,
            appPlatformFilter = PlatformFilter.ALL,
            likedGames = listOf(liked),
            recommendationWeight = 100,
            forYouOnlyMode = false
        )

        val ranked = DealFilterEngine.filterTopTen(
            rawDeals = listOf(unrelatedMainstream, unverifiedShovelware, matchingRoguelite),
            settings = settings,
            platformOverride = PlatformFilter.ALL,
            limit = 30
        )

        assertEquals("dead-cells", ranked.first().id)
        assertNotNull(ranked.first().matchReason)
        assertTrue(ranked.first().matchReason!!.contains("Hades"))

        val shovelwareResult = ranked.first { it.id == "clone-99" }
        assertNull(shovelwareResult.matchReason)
        assertTrue(ranked.first().personalScore > shovelwareResult.personalScore)
    }

    @Test
    fun `forYouOnlyMode amplifies semantic affinity boost and marks exact liked titles`() {
        val liked = LikedGame(
            id = "celeste",
            title = "Celeste",
            publisher = "Maddy Makes Games",
            developer = "Maddy Makes Games",
            gameCategories = listOf("platformer", "indie"),
            steamTagIds = listOf(1625, 3877),
            steamTagNames = listOf("Platform", "Platform di precisione")
        )

        val exactDeal = Deal(
            id = "celeste-eshop",
            nsuid = null,
            title = "Celeste – Nintendo Switch",
            publisher = "Maddy Makes Games",
            platform = Platform.SWITCH_1,
            regularPriceEur = 19.99,
            discountedPriceEur = 4.99,
            discountPercent = 75,
            endsAtIso = null,
            popularityRank = 20,
            steamReviewCount = 90000,
            hotScore = 62.0,
            steamTagIds = listOf(1625, 3877),
            steamTagNames = listOf("Platform", "Platform di precisione"),
            gameCategories = listOf("platformer", "indie"),
            eshopUrl = "https://www.nintendo.com/celeste",
            imageUrl = ""
        )

        val other = Deal(
            id = "generic-hit",
            nsuid = null,
            title = "Football Pro",
            publisher = "Sports Co",
            platform = Platform.SWITCH_1,
            regularPriceEur = 59.99,
            discountedPriceEur = 29.99,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 1,
            steamReviewCount = 15000,
            hotScore = 80.0,
            steamTagIds = listOf(701),
            steamTagNames = listOf("Sport"),
            gameCategories = listOf("sports"),
            eshopUrl = "https://www.nintendo.com/football",
            imageUrl = ""
        )

        val scored = RecommendationEngine.applyRecommendations(
            deals = listOf(other, exactDeal),
            settings = FilterSettings(
                likedGames = listOf(liked),
                recommendationWeight = 20,
                forYouOnlyMode = true
            )
        )

        val exactScored = scored.first { it.id == "celeste-eshop" }
        val otherScored = scored.first { it.id == "generic-hit" }
        assertEquals("Tra i tuoi preferiti ♥", exactScored.matchReason)
        assertTrue(exactScored.personalScore > otherScored.personalScore)
    }

    @Test
    fun `topLikedCategories prioritizes most frequent user genres`() {
        val likes = listOf(
            LikedGame(id = "1", title = "A", gameCategories = listOf("action", "indie")),
            LikedGame(id = "2", title = "B", gameCategories = listOf("rpg", "action")),
            LikedGame(id = "3", title = "C", gameCategories = listOf("action", "adventure"))
        )

        val top = RecommendationEngine.topLikedCategories(likes, limit = 2)
        assertEquals("action", top.first())
        assertEquals(2, top.size)
    }
}
