package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealMatchSource
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    @Test
    fun `cosine similarity boosts 5-star matching micro-tags and respects anti-shovelware guardrail`() {
        val liked = LikedGame(
            id = "hades-1",
            title = "Hades",
            publisher = "Supergiant Games",
            developer = "Supergiant Games",
            gameCategories = listOf("action", "indie"),
            steamTagIds = listOf(3959, 1716, 4885),
            steamTagNames = listOf("Roguelite", "Roguelike", "Inferno di proiettili"),
            rating = 5
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
        assertEquals(DealMatchSentiment.RECOMMENDED, ranked.first().matchSentiment)
        assertEquals(DealMatchSource.LOCAL, ranked.first().matchSource)
        assertNotNull(ranked.first().matchReason)
        assertTrue(ranked.first().matchReason!!.contains("Hades"))

        val shovelwareResult = ranked.first { it.id == "clone-99" }
        assertNull(shovelwareResult.matchReason)
        assertTrue(ranked.first().personalScore > shovelwareResult.personalScore)
    }

    @Test
    fun `1-star rating penalizes similar games, excludes them in forYouOnlyMode, and owned backlog is filtered out`() {
        val dislikedRacing = LikedGame(
            id = "bad-kart",
            title = "Bad Kart Racer",
            gameCategories = listOf("racing"),
            steamTagIds = listOf(699, 701),
            steamTagNames = listOf("Corse", "Arcade"),
            rating = 1
        )
        val ownedBacklog = LikedGame(
            id = "owned-zelda",
            title = "Zelda Echoes",
            gameCategories = listOf("adventure"),
            rating = null,
            isOwned = true
        )

        val ownedDeal = Deal(
            id = "owned-zelda",
            nsuid = null,
            title = "Zelda Echoes",
            publisher = "Nintendo",
            platform = Platform.SWITCH_1,
            regularPriceEur = 59.99,
            discountedPriceEur = 39.99,
            discountPercent = 33,
            endsAtIso = null,
            popularityRank = 1,
            steamReviewCount = 0,
            hotScore = 95.0,
            gameCategories = listOf("adventure"),
            eshopUrl = "https://www.nintendo.com/zelda",
            imageUrl = ""
        )

        val similarRacingDeal = Deal(
            id = "another-kart",
            nsuid = null,
            title = "Turbo Kart 2",
            publisher = "Racer Co",
            platform = Platform.SWITCH_1,
            regularPriceEur = 39.99,
            discountedPriceEur = 19.99,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 2,
            steamReviewCount = 5000,
            hotScore = 72.0,
            steamTagIds = listOf(699, 701),
            steamTagNames = listOf("Corse", "Arcade"),
            gameCategories = listOf("racing"),
            eshopUrl = "https://www.nintendo.com/turbo",
            imageUrl = ""
        )

        val neutralDeal = Deal(
            id = "neutral-rpg",
            nsuid = null,
            title = "Octo Story",
            publisher = "Square Enix",
            platform = Platform.SWITCH_1,
            regularPriceEur = 49.99,
            discountedPriceEur = 24.99,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 6,
            steamReviewCount = 8000,
            hotScore = 70.0,
            steamTagIds = listOf(122, 4434),
            steamTagNames = listOf("GDR", "JRPG"),
            gameCategories = listOf("rpg"),
            eshopUrl = "https://www.nintendo.com/octo",
            imageUrl = ""
        )

        val normalFeed = DealFilterEngine.filterTopTen(
            rawDeals = listOf(ownedDeal, similarRacingDeal, neutralDeal),
            settings = FilterSettings(
                minDiscountPercent = 0,
                minRegularPriceEur = 0f,
                likedGames = listOf(dislikedRacing, ownedBacklog),
                recommendationWeight = 100,
                forYouOnlyMode = false
            ),
            platformOverride = PlatformFilter.ALL,
            limit = 30
        )

        // Owned unplayed game must be excluded completely from deals feed
        assertFalse(normalFeed.any { it.id == "owned-zelda" })
        // Neutral RPG must outrank discouraged racing deal despite lower base hotScore (70 < 72)
        assertEquals("neutral-rpg", normalFeed.first().id)
        val discouraged = normalFeed.first { it.id == "another-kart" }
        assertEquals(DealMatchSentiment.DISCOURAGED, discouraged.matchSentiment)
        assertTrue(discouraged.matchReason!!.contains("1★"))

        // For You mode hides strongly discouraged games
        val forYouFeed = DealFilterEngine.filterTopTen(
            rawDeals = listOf(ownedDeal, similarRacingDeal, neutralDeal),
            settings = FilterSettings(
                minDiscountPercent = 0,
                minRegularPriceEur = 0f,
                likedGames = listOf(dislikedRacing, ownedBacklog),
                recommendationWeight = 100,
                forYouOnlyMode = true
            ),
            platformOverride = PlatformFilter.ALL,
            limit = 30
        )
        assertFalse(forYouFeed.any { it.id == "another-kart" })
        assertEquals(listOf("neutral-rpg"), forYouFeed.map { it.id })
    }

    @Test
    fun `topLikedCategories prioritizes 4-5 star games and ignores 1-2 star dislikes`() {
        val likes = listOf(
            LikedGame(id = "1", title = "A", gameCategories = listOf("action", "indie"), rating = 5),
            LikedGame(id = "2", title = "B", gameCategories = listOf("rpg", "action"), rating = 4),
            LikedGame(id = "3", title = "C", gameCategories = listOf("racing", "sports"), rating = 1),
            LikedGame(id = "4", title = "D", gameCategories = listOf("racing"), rating = 1),
            LikedGame(id = "5", title = "E", gameCategories = listOf("racing"), rating = null, isOwned = true)
        )

        val top = RecommendationEngine.topLikedCategories(likes, limit = 3)
        assertEquals("action", top.first())
        assertFalse("racing" in top)
    }

    @Test
    fun `computeTasteProfile aggregates rated genre bars ignoring generic meta tags and unrated backlog`() {
        val library = listOf(
            LikedGame(
                id = "hades",
                title = "Hades",
                steamTagNames = listOf("Singleplayer", "Roguelite", "Azione"),
                rating = 5
            ),
            LikedGame(
                id = "dead-cells",
                title = "Dead Cells",
                steamTagNames = listOf("Roguelite", "Metroidvania", "Controller"),
                rating = 5
            ),
            LikedGame(
                id = "hollow-knight",
                title = "Hollow Knight",
                steamTagNames = listOf("Metroidvania", "Indie"),
                rating = 4
            ),
            LikedGame(
                id = "unrated-backlog",
                title = "Zelda Backlog",
                steamTagNames = listOf("Avventura"),
                rating = null,
                isOwned = true
            )
        )

        val bars = RecommendationEngine.computeTasteProfile(library, limit = 3)
        assertTrue(bars.isNotEmpty())
        assertEquals("Roguelite", bars.first().label)
        assertEquals(100, bars.first().percent)
        assertTrue(bars.first().isPositive)
        assertFalse(bars.any { it.label == "Singleplayer" || it.label == "Controller" || it.label == "Avventura" })
    }

    @Test
    fun `neutral AI evaluated deal retains NONE sentiment and GEMINI source label`() {
        val liked = LikedGame(id = "g1", title = "Hades", steamTagNames = listOf("Roguelite"), rating = 5)
        val neutralAiDeal = Deal(
            id = "d-neutral",
            nsuid = null,
            title = "Solid Platformer",
            publisher = "Indie Pub",
            platform = Platform.SWITCH_1,
            regularPriceEur = 24.99,
            discountedPriceEur = 12.49,
            discountPercent = 50,
            endsAtIso = null,
            popularityRank = 10,
            hotScore = 70.0,
            steamReviewCount = 1200,
            steamTagNames = listOf("Platform"),
            eshopUrl = "https://www.nintendo.com/solid",
            imageUrl = "",
            aiAffinityScore = 52.0,
            aiReason = "Action lineare ma fluido"
        )

        val evaluated = RecommendationEngine.applyRecommendations(
            deals = listOf(neutralAiDeal),
            settings = FilterSettings(likedGames = listOf(liked), recommendationWeight = 50)
        ).first()

        assertEquals(DealMatchSentiment.NONE, evaluated.matchSentiment)
        assertEquals(DealMatchSource.GEMINI, evaluated.matchSource)
        val reason = evaluated.matchReason.orEmpty()
        assertTrue(reason.contains("52/100"))
        assertTrue(reason.contains("Action lineare ma fluido"))
    }
}
