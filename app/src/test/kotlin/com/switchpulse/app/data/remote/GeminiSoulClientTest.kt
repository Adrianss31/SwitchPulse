package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
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

class GeminiSoulClientTest {

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

    private fun sampleLibrary() = listOf(
        LikedGame(
            id = "g-1",
            title = "Hollow Knight",
            steamTagNames = listOf("Metroidvania", "Atmospheric"),
            rating = 5,
            userNote = "Adoro l'esplorazione libera e i boss impegnativi ma giusti",
            isOwned = true
        ),
        LikedGame(
            id = "g-2",
            title = "Octopath Traveler",
            steamTagNames = listOf("JRPG", "Turn-Based"),
            rating = 1,
            userNote = "Troppo grinding e ritmi lenti nei dialoghi",
            isOwned = false
        ),
        LikedGame(
            id = "g-3",
            title = "Celeste",
            steamTagNames = listOf("Precision Platformer"),
            rating = null,
            isOwned = true
        )
    )

    @Test
    fun `generateGamerSoul sends notes and backlog, ignores thought part, and parses GamerSoulProfile`() = runBlocking {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "thought": true,
                        "text": "Thinking about player profile..."
                      },
                      {
                        "text": "```json\n{\"archetypeTitle\":\"Esploratore Precisionista\",\"soulSummary\":\"Ami i mondi interconnessi dove la padronanza meccanica premia la curiosità, evitando dialoghi prolissi.\",\"corePillars\":[\"Esplorazione non lineare\",\"Boss fight leggibili\",\"Ritmo serrato\"],\"redFlags\":[\"Grinding ripetitivo\",\"Tutorial invasivi\"]}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val client = GeminiSoulClient(
            httpClient = OkHttpClient(),
            endpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )
        val library = sampleLibrary()
        val tastePrompt = "Preferisco giochi da 15-25 ore con gameplay immediato"

        val result = client.generateGamerSoul(
            apiKey = "AIzaTestKey",
            likedGames = library,
            modelId = GeminiModels.HYDROGEN,
            userTastePrompt = tastePrompt
        )

        val recorded = server.takeRequest()
        val requestJson = recorded.body.readUtf8()
        assertEquals("AIzaTestKey", recorded.getHeader("x-goog-api-key"))
        assertTrue(requestJson.contains("Hollow Knight"))
        assertTrue(requestJson.contains("Adoro l'esplorazione libera"))
        assertTrue(requestJson.contains("Troppo grinding"))
        assertTrue(requestJson.contains("Celeste"))
        assertTrue(requestJson.contains(tastePrompt))

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("Esploratore Precisionista", profile.archetypeTitle)
        assertTrue(profile.soulSummary.contains("mondi interconnessi"))
        assertEquals(3, profile.corePillars.size)
        assertEquals(2, profile.redFlags.size)
        assertEquals(
            client.computeSignature(GeminiModels.HYDROGEN, library, tastePrompt),
            profile.signature
        )
    }

    @Test
    fun `discoverGamesForSoul filters owned library titles and deprioritizes previous picks`() = runBlocking {
        val body = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n[{\"title\":\"Hollow Knight™\",\"pitchHook\":\"In libreria\",\"reason\":\"Già posseduto\",\"estimatedPriceHint\":\"€14,99\"},{\"title\":\"Nine Sols\",\"pitchHook\":\"Parry stile Sekiro + Metroidvania\",\"reason\":\"Perfetto per i tuoi riflessi e l'amore per i boss leggibili.\",\"estimatedPriceHint\":\"€28,99\"},{\"title\":\"Prince of Persia: The Lost Crown\",\"pitchHook\":\"Metroidvania fluido e tecnico\",\"reason\":\"Movimento agile senza tempi morti né grinding.\",\"estimatedPriceHint\":\"€39,99\"},{\"title\":\"Tunic\",\"pitchHook\":\"Segreti criptici e combattimento teso\",\"reason\":\"Premia l'osservazione pura come piace a te.\",\"estimatedPriceHint\":\"€27,99\"},{\"title\":\"Animal Well\",\"pitchHook\":\"Labirinto denso di misteri\",\"reason\":\"Zero dialoghi inutili, pura scoperta.\",\"estimatedPriceHint\":\"€24,50\"},{\"title\":\"Metroid Dread\",\"pitchHook\":\"Azione fulminea e boss memorabili\",\"reason\":\"Ritmo altissimo e zero filler.\",\"estimatedPriceHint\":\"€59,99\"}]\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val client = GeminiSoulClient(
            httpClient = OkHttpClient(),
            endpointOverride = server.url("/v1beta/models/gemini-4-hydrogen:generateContent").toString()
        )
        val soulProfile = GamerSoulProfile(
            archetypeTitle = "Esploratore Precisionista",
            soulSummary = "Ama esplorazione non lineare e sfide meccaniche.",
            corePillars = listOf("Metroidvania", "Boss fight eque"),
            redFlags = listOf("Grinding")
        )

        val result = client.discoverGamesForSoul(
            apiKey = "AIzaTestKey",
            soulProfile = soulProfile,
            likedGames = sampleLibrary(),
            modelId = GeminiModels.HYDROGEN,
            excludePreviousTitles = listOf("Nine Sols"),
            desiredCount = 5
        )

        val recorded = server.takeRequest()
        val requestJson = recorded.body.readUtf8()
        assertTrue(requestJson.contains("Esploratore Precisionista"))
        assertTrue(requestJson.contains("Nine Sols"))

        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val picks = result.getOrThrow()
        assertFalse(picks.any { it.title.contains("Hollow Knight", ignoreCase = true) })
        assertEquals(5, picks.size)
        // Fresh titles should appear before previously suggested "Nine Sols"
        assertEquals("Prince of Persia: The Lost Crown", picks.first().title)
        assertEquals("Nine Sols", picks.last().title)
        assertEquals("€28,99", picks.last().estimatedPriceHint)
    }
}
