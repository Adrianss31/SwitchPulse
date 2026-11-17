package com.switchpulse.app.data.remote

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun `fetchInsights merges Steam metacritic and Gemini Search grounded HLTB hours`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody(
                        """{"1145360":{"success":true,"data":{"metacritic":{"score":93,"url":"https://www.metacritic.com/game/hades/"}}}}"""
                    )
                    path.endsWith(":generateContent") -> {
                        val reqBody = request.body.readUtf8()
                        assertTrue(reqBody.contains("google_search"))
                        assertTrue(reqBody.contains("\"thinkingLevel\":\"low\""))
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "candidates": [
                                {
                                  "content": {
                                    "parts": [
                                      {
                                        "text": "{\"metacriticScore\":93,\"metacriticUrl\":\"https://www.metacritic.com/game/hades/\",\"hltbMainHours\":\"22 ore\",\"hltbMainExtraHours\":\"48 ore\",\"hltbCompletionistHours\":\"95 ore\",\"summaryNote\":\"60 fps fluidissimi su Switch, replay value altissimo.\"}"
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(),
            geminiApiKey = "AIzaKey",
            geminiModel = GeminiModels.HYDROGEN
        )

        assertEquals(93, insights.metacriticScore)
        assertEquals("https://www.metacritic.com/game/hades/", insights.metacriticUrl)
        assertEquals("22 ore", insights.hltbMainHours)
        assertEquals("48 ore", insights.hltbMainExtraHours)
        assertEquals("95 ore", insights.hltbCompletionistHours)
        assertTrue(insights.summaryNote!!.contains("60 fps"))
        assertTrue(insights.sourceLabel.contains("gemini-4-hydrogen"))
    }

    @Test
    fun `fetchInsights returns Steam metacritic gracefully without Gemini key`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200).setBody(
                    """{"1145360":{"success":true,"data":{"metacritic":{"score":93,"url":"https://www.metacritic.com/game/hades/"}}}}"""
                )
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )

        val insights = client.fetchInsights(deal = sampleDeal(), geminiApiKey = "")
        assertEquals(93, insights.metacriticScore)
        assertEquals(MetacriticOrigin.STEAM_PC, insights.metacriticOrigin)
        assertNull(insights.hltbMainHours)
        assertEquals("Steam Metacritic API (PC)", insights.sourceLabel)
    }

    @Test
    fun `fetchInsights falls back to direct Gemini when google_search is blocked on Free Tier and includes user notes`() = runBlocking {
        var geminiCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody(
                        """{"1145360":{"success":true,"data":{}}}"""
                    )
                    path.endsWith(":generateContent") -> {
                        geminiCalls++
                        val reqBody = request.body.readUtf8()
                        assertTrue(reqBody.contains("Adoro i combat veloci"))
                        assertTrue(reqBody.contains("Preferisco run brevi"))
                        if (geminiCalls == 1) {
                            assertTrue(reqBody.contains("google_search"))
                            MockResponse().setResponseCode(403).setBody(
                                """{"error":{"code":403,"message":"Search Grounding is not enabled on Free Tier for Gemini 4."}}"""
                            )
                        } else {
                            assertTrue(!reqBody.contains("google_search"))
                            assertTrue(reqBody.contains("\"thinkingLevel\":\"low\""))
                            assertTrue(reqBody.contains("\"maxOutputTokens\":4096"))
                            MockResponse().setResponseCode(200).setBody(
                                """
                                {
                                  "candidates": [
                                    {
                                      "content": {
                                        "parts": [
                                          {
                                            "thought": true,
                                            "text": "Thinking about {draft}..."
                                          },
                                          {
                                            "text": "{\"metacriticScore\":93,\"metacriticUrl\":null,\"hltbMainHours\":\"~22 ore\",\"hltbMainExtraHours\":\"~48 ore\",\"hltbCompletionistHours\":\"~95 ore\",\"verdict\":\"Imperdibile per te\",\"summaryNote\":\"Perfetto per i tuoi gusti dopo Hollow Knight.\"}"
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.trimIndent()
                            )
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(),
            geminiApiKey = "AIzaFreeTierKey",
            geminiModel = GeminiModels.HYDROGEN,
            likedGames = listOf(
                LikedGame(
                    id = "hk",
                    title = "Hollow Knight",
                    rating = 5,
                    userNote = "Adoro i combat veloci"
                )
            ),
            userTastePrompt = "Preferisco run brevi"
        )

        assertEquals(2, geminiCalls)
        assertEquals(93, insights.metacriticScore)
        assertEquals("~22 ore", insights.hltbMainHours)
        assertEquals("~48 ore", insights.hltbMainExtraHours)
        assertEquals("~95 ore", insights.hltbCompletionistHours)
        assertEquals("Imperdibile per te", insights.aiVerdict)
        assertEquals(MetacriticOrigin.AI_ESTIMATE, insights.metacriticOrigin)
        assertTrue(insights.hltbIsEstimated)
        assertTrue(insights.summaryNote!!.contains("Hollow Knight"))
        assertTrue(insights.sourceLabel.contains("Stima critica"))
    }

    @Test
    fun `fetchInsights parses Metacritic JSON-LD directly by slug and handles string numbers in JSON`() = runBlocking {
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
                    path.endsWith(":generateContent") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "[{\"metacriticScore\":\"84\",\"metacriticUrl\":\"N/A\",\"hltbMainHours\":\"12 ore\",\"hltbMainExtraHours\":\"24 ore\",\"hltbCompletionistHours\":\"40 ore\",\"verdict\":\"Buon affare\",\"summaryNote\":\"Ottimo roguelite arcade.\"}]"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString(),
            metacriticBaseUrl = server.url("/").toString()
        )

        val ballXPit = sampleDeal(steamAppId = null).copy(title = "BALL x PIT – Nintendo Switch™ 2 Edition")
        val insights = client.fetchInsights(deal = ballXPit, geminiApiKey = "AIzaTest")

        assertEquals(84, insights.metacriticScore)
        assertTrue(insights.metacriticUrl!!.endsWith("/game/ball-x-pit/"))
        assertEquals("12 ore", insights.hltbMainHours)
        assertEquals("24 ore", insights.hltbMainExtraHours)
        assertEquals("40 ore", insights.hltbCompletionistHours)
        assertEquals("Buon affare", insights.aiVerdict)
        assertTrue(insights.sourceLabel.contains("Metacritic"))
    }

    @Test
    fun `fetchInsights skips google_search on subsequent calls once Free Tier returns 429 for grounding`() = runBlocking {
        var groundingAttempts = 0
        var directAttempts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody("""{"1145360":{"success":true,"data":{}}}""")
                    path.endsWith(":generateContent") -> {
                        val reqBody = request.body.readUtf8()
                        if (reqBody.contains("google_search")) {
                            groundingAttempts++
                            MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"message":"Search grounding quota exceeded on Free Tier"}}""")
                        } else {
                            directAttempts++
                            MockResponse().setResponseCode(200).setBody(
                                """{"candidates":[{"content":{"parts":[{"text":"{\"metacriticScore\":90,\"hltbMainHours\":\"20 ore\",\"verdict\":\"Consigliatissimo\",\"summaryNote\":\"Ottimo.\"}"}]}}]}"""
                            )
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-3.5-flash-lite:generateContent").toString()
        )

        val first = client.fetchInsights(deal = sampleDeal(), geminiApiKey = "AIzaFreeTierKey", geminiModel = GeminiModels.FLASH_LITE)
        val second = client.fetchInsights(deal = sampleDeal().copy(id = "deal-2", title = "Dead Cells"), geminiApiKey = "AIzaFreeTierKey", geminiModel = GeminiModels.FLASH_LITE)

        assertEquals("Consigliatissimo", first.aiVerdict)
        assertEquals("Consigliatissimo", second.aiVerdict)
        assertEquals(1, groundingAttempts)
        assertEquals(2, directAttempts)
    }

    @Test
    fun `fetchInsights skips external lookups when allowExternalMetadataLookup is false`() = runBlocking {
        var requestsSeen = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestsSeen++
                return MockResponse().setResponseCode(500)
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            geminiEndpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString(),
            metacriticBaseUrl = server.url("/").toString(),
            allowExternalMetadataLookup = false
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(steamAppId = 1145360),
            geminiApiKey = ""
        )
        assertEquals(0, requestsSeen)
        assertNull(insights.metacriticScore)
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
            steamAppDetailsUrl = server.url("/api/appdetails").toString()
        )
        val steamInsights = steamClient.fetchInsights(deal = sampleDeal(steamAppId = 1145360), geminiApiKey = "")
        assertEquals(listOf("https://cdn.steam/1.jpg", "https://cdn.steam/2.jpg"), steamInsights.screenshotUrls)

        val nintendoClient = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamStoreSearchUrl = server.url("/storesearch").toString(),
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
    }
}
