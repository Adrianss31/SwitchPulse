package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.Platform
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    @Test
    fun `rerank requests all candidates including neutral ones and parses full badge plus multi-sentence analysis`() = runBlocking {
        val longAnalysis = "Combina combattimenti tattici automatici in tempo reale con una progressione roguelike ad alto ritmo. Rispetto a Ori privilegia la gestione della squadra e build rapide invece dell'esplorazione platform."
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"301\",\"score\":72,\"badge\":\"Roguelike tattico ad alto ritmo\",\"analysis\":\"$longAnalysis\"}]"
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

        val requestJson = server.takeRequest().body.readUtf8()
        assertFalse(requestJson.contains("omitti i neutri"))
        assertTrue(requestJson.contains("NON omettere alcun"))

        val entry = outcome.scores.getValue("301")
        assertEquals(72.0, entry.score, 0.001)
        assertEquals("Roguelike tattico ad alto ritmo", entry.reason)
        assertEquals(longAnalysis, entry.detailedReason)
    }

    @Test
    fun `rerank includes userNote, backlog owned titles and userTastePrompt in prompt and signature`() = runBlocking {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"id\":\"301\",\"score\":89,\"badge\":\"Boss fight e esplorazione\",\"analysis\":\"Molto affine ad Ori.\"}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val notedGames = listOf(
            LikedGame(
                id = "like-1",
                title = "Ori and the Blind Forest",
                steamTagNames = listOf("Metroidvania"),
                rating = 5,
                userNote = "Adoro i platform fluidi ma odio il grinding"
            ),
            LikedGame(
                id = "owned-2",
                title = "Dead Cells",
                isOwned = true,
                rating = null
            )
        )

        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            endpointBaseUrl = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )
        val outcome = reranker.rerank(
            apiKey = "AIzaTestKey",
            likedGames = notedGames,
            candidates = listOf(sampleDeal()),
            modelId = GeminiModels.HYDROGEN,
            userTastePrompt = "Preferisco giochi impegnativi ma rispettosi del tempo"
        )

        val requestJson = server.takeRequest().body.readUtf8()
        assertTrue(requestJson.contains("Adoro i platform fluidi ma odio il grinding"))
        assertTrue(requestJson.contains("Dead Cells"))
        assertTrue(requestJson.contains("Preferisco giochi impegnativi ma rispettosi del tempo"))
        assertEquals(89.0, outcome.scores.getValue("301").score, 0.001)

        val sig1 = reranker.computeSignature(
            GeminiModels.HYDROGEN,
            notedGames,
            listOf(sampleDeal()),
            userTastePrompt = "A"
        )
        val sig2 = reranker.computeSignature(
            GeminiModels.HYDROGEN,
            notedGames,
            listOf(sampleDeal()),
            userTastePrompt = "B"
        )
        val sig3 = reranker.computeSignature(
            GeminiModels.HYDROGEN,
            listOf(notedGames[0].copy(userNote = "Nota diversa"), notedGames[1]),
            listOf(sampleDeal()),
            userTastePrompt = "A"
        )
        assertFalse(sig1 == sig2)
        assertFalse(sig1 == sig3)
    }

    @Test
    fun `rerank chunks large lists into 20-item batches and recovers omitted top-30 ids`() = runBlocking {
        val batch1Json = (1..19).joinToString(",") { idx ->
            "{\"id\":\"$idx\",\"score\":75,\"badge\":\"Ok $idx\",\"analysis\":\"Valid $idx.\"}"
        }
        val batch2Json = (21..25).joinToString(",") { idx ->
            "{\"id\":\"$idx\",\"score\":70,\"badge\":\"Ok $idx\",\"analysis\":\"Valid $idx.\"}"
        }
        val recoveryJson = "[{\"id\":\"20\",\"score\":82,\"badge\":\"Recuperato 20\",\"analysis\":\"Recuperato dal fallback.\"}]"

        fun wrapGemini(jsonText: String) = MockResponse().setResponseCode(200).setBody(
            """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(jsonText)}}]}}]}"""
        )

        server.enqueue(wrapGemini("[$batch1Json]")) // omits id=20 intentionally
        server.enqueue(wrapGemini("[$batch2Json]"))
        server.enqueue(wrapGemini(recoveryJson))

        val candidates = (1..25).map { idx ->
            sampleDeal(id = idx.toString()).copy(title = "Game $idx", popularityRank = idx)
        }

        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            endpointBaseUrl = server.url("/v1beta/models/gemini-3.5-flash-lite:generateContent").toString()
        )
        val outcome = reranker.rerank(
            apiKey = "AIzaTestKey",
            likedGames = sampleRatedGames(),
            candidates = candidates,
            modelId = GeminiModels.FLASH_LITE
        )

        assertFalse(outcome.isError)
        assertEquals(25, outcome.scores.size)
        assertEquals(82.0, outcome.scores.getValue("20").score, 0.001)
        assertTrue(outcome.statusSummary.contains("25/25 offerte analizzate"))
        assertEquals(3, server.requestCount)
    }
}
