package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
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
        assertNull(insights.hltbMainHours)
        assertEquals("Steam Metacritic API", insights.sourceLabel)
    }
}
