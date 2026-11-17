package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiProviderAiTest {

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

    private fun sampleDeal() = Deal(
        id = "d-301",
        nsuid = null,
        title = "Hades",
        publisher = "Supergiant Games",
        platform = Platform.SWITCH_1,
        regularPriceEur = 24.99,
        discountedPriceEur = 9.99,
        discountPercent = 60,
        endsAtIso = null,
        popularityRank = 1,
        hotScore = 92.0,
        steamAppId = 1145360,
        steamTagNames = listOf("Roguelike", "Action"),
        eshopUrl = "https://www.nintendo.com/hades",
        imageUrl = ""
    )

    private fun sampleLiked() = listOf(
        LikedGame(
            id = "g-1",
            title = "Dead Cells",
            steamTagNames = listOf("Roguelike", "Metroidvania"),
            rating = 5,
            userNote = "Combat fulmineo"
        )
    )

    @Test
    fun `OpenRouter rerank sends Bearer and OpenRouter headers and strips think tags`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "<think>ragionamento interno...</think>\n```json\n[{\"id\":\"d-301\",\"score\":95,\"badge\":\"Action roguelike top\",\"analysis\":\"Perfetto se ami il combat fulmineo di Dead Cells.\"}]\n```"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val reranker = GeminiReranker(
            httpClient = OkHttpClient(),
            openAiEndpointOverride = server.url("/api/v1/chat/completions").toString()
        )

        val outcome = reranker.rerank(
            apiKey = "sk-or-v1-test",
            likedGames = sampleLiked(),
            candidates = listOf(sampleDeal()),
            modelId = GeminiModels.OPENROUTER_FREE,
            provider = AiProvider.OPENROUTER
        )

        val req = server.takeRequest()
        assertEquals("Bearer sk-or-v1-test", req.getHeader("Authorization"))
        assertEquals("SwitchPulse", req.getHeader("X-Title"))
        val body = req.body.readUtf8()
        assertFalse(body.contains("response_format"))

        assertFalse(outcome.isError)
        assertEquals(95.0, outcome.scores["d-301"]?.score)
        assertEquals("Action roguelike top", outcome.scores["d-301"]?.reason)
        assertTrue(outcome.statusSummary.contains("OpenRouter"))
    }

    @Test
    fun `DeepSeek Reasoner omits response_format and temperature while deepseek-chat includes json_object`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\"archetypeTitle\":\"Maestro del Flow\",\"soulSummary\":\"Cerchi precisione e ritmo costante senza filler.\",\"corePillars\":[\"Combat reattivo\",\"Ritmo veloce\"],\"redFlags\":[\"Grinding\"]}"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\"metacriticScore\":93,\"hltbMainHours\":\"22 ore\",\"verdict\":\"Imperdibile per te\",\"summaryNote\":\"Ottimo su Switch.\"}"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val soulClient = GeminiSoulClient(
            httpClient = OkHttpClient(),
            openAiEndpointOverride = server.url("/chat/completions").toString()
        )
        val soulRes = soulClient.generateGamerSoul(
            apiKey = "sk-ds-test",
            likedGames = sampleLiked(),
            modelId = GeminiModels.DEEPSEEK_REASONER,
            provider = AiProvider.DEEPSEEK
        )
        val soulReq = server.takeRequest()
        assertEquals("Bearer sk-ds-test", soulReq.getHeader("Authorization"))
        val soulReqJson = soulReq.body.readUtf8()
        assertFalse(soulReqJson.contains("response_format"))
        assertFalse(soulReqJson.contains("\"temperature\""))
        assertTrue(soulRes.isSuccess)
        assertEquals("Maestro del Flow", soulRes.getOrThrow().archetypeTitle)

        // Direct completion check with deepseek-chat (expects json_object)
        val aiClient = AiCompletionClient(
            httpClient = OkHttpClient(),
            deepSeekEndpointOverride = server.url("/chat/completions").toString()
        )
        val chatRes = aiClient.generate(
            provider = AiProvider.DEEPSEEK,
            apiKey = "sk-ds-test",
            modelId = GeminiModels.DEEPSEEK_CHAT,
            prompt = "Valuta in JSON",
            expectJsonObject = true
        )
        val chatReq = server.takeRequest()
        val chatJson = chatReq.body.readUtf8()
        assertTrue(chatJson.contains("\"type\":\"json_object\""))
        assertTrue(chatRes is AiCallResult.Success)
    }

    @Test
    fun `GameInsightsClient performs zero AI calls even with DeepSeek key and uses direct Steam lookup`() = runBlocking {
        var nonSteamCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when {
                    path.endsWith("/api/appdetails") -> MockResponse().setResponseCode(200).setBody(
                        """{"1145360":{"success":true,"data":{"metacritic":{"score":93,"url":"https://www.metacritic.com/game/hades/"}}}}"""
                    )
                    else -> {
                        nonSteamCalls++
                        MockResponse().setResponseCode(404)
                    }
                }
            }
        }

        val client = GameInsightsClient(
            httpClient = OkHttpClient(),
            steamAppDetailsUrl = server.url("/api/appdetails").toString(),
            metacriticBaseUrl = server.url("/").toString()
        )

        val insights = client.fetchInsights(
            deal = sampleDeal(),
            geminiApiKey = "sk-ds-test",
            geminiModel = GeminiModels.DEEPSEEK_CHAT,
            likedGames = sampleLiked(),
            provider = AiProvider.DEEPSEEK
        )

        assertEquals(0, nonSteamCalls)
        assertEquals(93, insights.metacriticScore)
        assertEquals(null, insights.hltbMainHours)
        assertEquals(null, insights.aiVerdict)
        assertTrue(insights.sourceLabel.contains("Steam"))
    }
}
