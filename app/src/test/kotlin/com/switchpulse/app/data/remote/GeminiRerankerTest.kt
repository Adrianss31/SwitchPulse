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

    private fun sampleLiked() = LikedGame(
        id = "like-1",
        title = "Ori and the Blind Forest",
        steamTagNames = listOf("Metroidvania", "Platform")
    )

    @Test
    fun `rerank parses structured json response including markdown fences`() = runBlocking {
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
            endpointBaseUrl = server.url("/v1beta/models/gemini-2.5-flash-lite:generateContent").toString()
        )

        val result = reranker.rerank(
            apiKey = "AIzaTestKey",
            likedGames = listOf(sampleLiked()),
            candidates = listOf(sampleDeal())
        )

        val recorded = server.takeRequest()
        assertEquals("AIzaTestKey", recorded.getHeader("x-goog-api-key"))
        assertEquals(94.0, result.getValue("301").score, 0.001)
        assertEquals("Metroidvania oscuro come Ori", result.getValue("301").reason)
    }

    @Test
    fun `rerank degrades cleanly on blank key or unauthorized http error`() = runBlocking {
        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            endpointBaseUrl = server.url("/v1beta/models/gemini-2.5-flash-lite:generateContent").toString()
        )

        val emptyWhenNoKey = reranker.rerank(
            apiKey = "   ",
            likedGames = listOf(sampleLiked()),
            candidates = listOf(sampleDeal())
        )
        assertTrue(emptyWhenNoKey.isEmpty())
        assertEquals(0, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        val emptyOnError = reranker.rerank(
            apiKey = "bad-key",
            likedGames = listOf(sampleLiked()),
            candidates = listOf(sampleDeal())
        )
        assertTrue(emptyOnError.isEmpty())
    }
}
