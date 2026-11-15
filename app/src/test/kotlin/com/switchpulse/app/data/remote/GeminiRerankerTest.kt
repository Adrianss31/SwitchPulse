package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiRerankerTest {

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

    private fun sampleDeal(id: String = "301") = Deal(
        id = id,
        nsuid = null,
        title = "Hollow Knight",
        publisher = "Team Cherry",
        platform = Platform.SWITCH_1,
        regularPriceEur = 14.99,
        discountedPriceEur = 7.49,
        discountPercent = 50,
        endsAtIso = null,
        popularityRank = 1,
        hotScore = 88.0,
        steamTagNames = listOf("Metroidvania", "Souls-like"),
        eshopUrl = "https://www.nintendo.com/hk",
        imageUrl = ""
    )

    private fun sampleRatedGames() = listOf(
        LikedGame(
            id = "like-1",
            title = "Ori and the Blind Forest",
            steamTagNames = listOf("Metroidvania", "Platform"),
            rating = 5
        ),
        LikedGame(
            id = "dislike-1",
            title = "Kart 99",
            steamTagNames = listOf("Corse"),
            rating = 1
        )
    )

    @Test
    fun `rerank sends thinkingLevel low, includes 1-star dislikes, and returns diagnostic outcome`() = runBlocking {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n[{\"id\":\"301\",\"score\":94,\"reason\":\"Metroidvania oscuro come Ori\"}]\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            endpointBaseUrl = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )

        val outcome = reranker.rerank(
            apiKey = "AIzaTestKey",
            likedGames = sampleRatedGames(),
            candidates = listOf(sampleDeal()),
            modelId = GeminiModels.HYDROGEN
        )

        val recorded = server.takeRequest()
        val requestJson = recorded.body.readUtf8()
        assertEquals("AIzaTestKey", recorded.getHeader("x-goog-api-key"))
        assertTrue(requestJson.contains("\"thinkingLevel\":\"low\""))
        assertTrue(requestJson.contains("[5★] Ori and the Blind Forest"))
        assertTrue(requestJson.contains("[1★] Kart 99"))
        assertFalse(outcome.isError)
        assertTrue(outcome.statusSummary.contains("gemini-4-hydrogen"))
        assertEquals(94.0, outcome.scores.getValue("301").score, 0.001)
        assertEquals("Metroidvania oscuro come Ori", outcome.scores.getValue("301").reason)
    }

    @Test
    fun `rerank surfaces explicit error diagnostic on HTTP 401 and testKey works`() = runBlocking {
        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            endpointBaseUrl = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )

        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":{"code":401,"message":"API key not valid."}}""")
        )
        val errOutcome = reranker.rerank(
            apiKey = "bad-key",
            likedGames = sampleRatedGames(),
            candidates = listOf(sampleDeal()),
            modelId = GeminiModels.HYDROGEN
        )
        assertTrue(errOutcome.isError)
        assertTrue(errOutcome.scores.isEmpty())
        assertTrue(errOutcome.statusSummary.contains("HTTP 401"))
        assertTrue(errOutcome.statusSummary.contains("API key not valid."))

        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}""")
        )
        val ping = reranker.testKey(apiKey = "AIzaTestKey", modelId = GeminiModels.HYDROGEN)
        assertTrue(ping.isSuccess)
        assertTrue(ping.getOrThrow().contains("gemini-4-hydrogen"))
    }
}
