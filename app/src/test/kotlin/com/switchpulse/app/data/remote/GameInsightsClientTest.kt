package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.MetacriticOrigin
import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GameInsightsClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleDeal(steamAppId: Int? = 1145360) = Deal(
        id = "deal-1",
        nsuid = null,
        title = "Hades",
        publisher = "Supergiant Games",
        platform = Platform.SWITCH_1,
        regularPriceEur = 24.99,
        discountedPriceEur = 9.99,
        discountPercent = 60,
        endsAtIso = null,
        popularityRank = 1,
        steamAppId = steamAppId,
        eshopUrl = "https://www.nintendo.com/hades",
        imageUrl = ""
    )

    @Test
    fun `fetchInsights returns Steam metacritic directly without AI or HLTB scraping`() = runBlocking {
        val requestPaths = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                synchronized(requestPaths) { requestPaths.add(path) }
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody(
                        """{"1145360":{"success":true,"data":{"metacritic":{"score":93,"url":"https://www.metacritic.com/game/hades/"}}}}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString()
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(),
            geminiApiKey = "AIzaShouldNotBeUsed",
            geminiModel = GeminiModels.HYDROGEN
        )

        assertEquals(93, insights.metacriticScore)
        assertEquals("https://www.metacritic.com/game/hades/", insights.metacriticUrl)
        assertEquals(MetacriticOrigin.STEAM_PC, insights.metacriticOrigin)
        assertNull(insights.hltbMainHours)
        assertFalse(insights.hltbIsEstimated)
        assertNull(insights.aiVerdict)
        assertNull(insights.summaryNote)
        assertEquals("Steam Metacritic API (PC)", insights.sourceLabel)
        assertEquals(listOf("/api/appdetails"), requestPaths)
    }

    @Test
    fun `fetchInsights parses Metacritic JSON-LD directly by slug and leaves HLTB null`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/game/ball-x-pit/") -> MockResponse().setResponseCode(200).setBody(
                        """
                        <html><head>
                        <script type="application/ld+json">{"@type":"VideoGame","aggregateRating":{"@type":"AggregateRating","name":"Metascore","ratingValue":84,"reviewCount":42}}</script>
                        </head></html>
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            metacriticBaseUrl = server.url("/").toString()
        )

        val ballXPit = sampleDeal(steamAppId = null).copy(title = "BALL x PIT – Nintendo Switch™ 2 Edition")
        val insights = client.fetchInsights(deal = ballXPit, geminiApiKey = "AIzaTest")

        assertEquals(84, insights.metacriticScore)
        assertEquals(MetacriticOrigin.METACRITIC, insights.metacriticOrigin)
        assertEquals("${server.url("/")}game/ball-x-pit/", insights.metacriticUrl)
        assertNull(insights.hltbMainHours)
        assertNull(insights.aiVerdict)
        assertEquals("Metacritic", insights.sourceLabel)
    }

    @Test
    fun `fetchInsights skips external lookups when allowExternalMetadataLookup is false`() = runBlocking {
        val requestsSeen = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestsSeen.incrementAndGet()
                return MockResponse().setResponseCode(500)
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            metacriticBaseUrl = server.url("/").toString(),
            allowExternalMetadataLookup = false
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(steamAppId = 1145360),
            geminiApiKey = ""
        )
        assertEquals(0, requestsSeen.get())
        assertNull(insights.metacriticScore)
        assertNull(insights.hltbMainHours)
        assertEquals("", insights.sourceLabel)
    }

    @Test
    fun `fetchInsights extracts Steam screenshots and Nintendo eShop 06_screenshots gallery`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody(
                        """{"1145360":{"success":true,"data":{"metacritic":{"score":93},"screenshots":[{"path_full":"https://cdn.steam/1.jpg"},{"path_full":"https://cdn.steam/2.jpg"}]}}}"""
                    )
                    path.endsWith("/wonder.html") -> MockResponse().setResponseCode(200).setBody(
                        """<img src="/eu/media/images/06_screenshots/games_5/wonder/screen_01.jpg"><img src="https://www.nintendo.com/eu/media/images/06_screenshots/games_5/wonder/screen_02.jpg">"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val steamClient = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            metacriticBaseUrl = server.url("/").toString()
        )
        val steamInsights = steamClient.fetchInsights(deal = sampleDeal(steamAppId = 1145360), geminiApiKey = "")
        assertEquals(listOf("https://cdn.steam/1.jpg", "https://cdn.steam/2.jpg"), steamInsights.screenshotUrls)

        val nintendoClient = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            steamStoreSearchUrl = server.url("/storesearch").toString(),
            metacriticBaseUrl = server.url("/").toString(),
            nintendoProductHtmlOverrideUrl = server.url("/wonder.html").toString()
        )
        val nintendoInsights = nintendoClient.fetchInsights(
            deal = sampleDeal(steamAppId = null).copy(
                title = "Super Mario Bros. Wonder",
                publisher = "Nintendo"
            ),
            geminiApiKey = ""
        )
        assertEquals(
            listOf(
                "https://www.nintendo.com/eu/media/images/06_screenshots/games_5/wonder/screen_01.jpg",
                "https://www.nintendo.com/eu/media/images/06_screenshots/games_5/wonder/screen_02.jpg"
            ),
            nintendoInsights.screenshotUrls
        )
        assertEquals("Nintendo eShop", nintendoInsights.sourceLabel)
    }

    @Test
    fun `computeProfileSignature stays stable across user profile edits for direct insights cache`() {
        val sigA = GameInsightsClient.computeProfileSignature(
            likedGames = listOf(LikedGame(id = "hk", title = "Hollow Knight", rating = 5)),
            userTastePrompt = "Metroidvania",
            provider = AiProvider.GEMINI,
            modelId = GeminiModels.FLASH_LITE
        )
        val sigB = GameInsightsClient.computeProfileSignature(
            likedGames = emptyList(),
            userTastePrompt = "RPG",
            provider = AiProvider.DEEPSEEK,
            modelId = GeminiModels.DEEPSEEK_CHAT
        )
        assertEquals(sigA, sigB)
    }
}
