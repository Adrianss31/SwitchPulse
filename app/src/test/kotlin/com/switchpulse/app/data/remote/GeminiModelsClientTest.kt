package com.switchpulse.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiModelsClientTest {

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

    @Test
    fun `fetchAvailableModels paginates, filters non-text models, and sorts canonical newest first`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("AIzaTestKey", request.getHeader("x-goog-api-key"))
                val token = request.requestUrl?.queryParameter("pageToken")
                return if (token == null) {
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "models": [
                            {
                              "name": "models/text-embedding-004",
                              "displayName": "Text Embedding",
                              "supportedGenerationMethods": ["embedContent"]
                            },
                            {
                              "name": "models/gemini-2.5-flash-preview-04-17",
                              "displayName": "Gemini 2.5 Flash Preview",
                              "supportedGenerationMethods": ["generateContent"]
                            },
                            {
                              "name": "models/gemini-3.5-flash-lite",
                              "displayName": "Gemini 3.5 Flash-Lite",
                              "description": "Fast free-tier friendly model",
                              "inputTokenLimit": 1048576,
                              "outputTokenLimit": 8192,
                              "supportedGenerationMethods": ["generateContent", "countTokens"]
                            },
                            {
                              "name": "models/gemini-4-hydrogen-image",
                              "displayName": "Gemini 4 Image Gen",
                              "supportedGenerationMethods": ["generateContent"]
                            }
                          ],
                          "nextPageToken": "page-2"
                        }
                        """.trimIndent()
                    )
                } else {
                    MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "models": [
                            {
                              "name": "models/gemini-4.5-pro",
                              "displayName": "Gemini 4.5 Pro",
                              "supportedGenerationMethods": ["generateContent"]
                            },
                            {
                              "name": "models/imagen-3.0-generate-002",
                              "displayName": "Imagen 3",
                              "supportedGenerationMethods": ["predict"]
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                }
            }
        }

        val client = GeminiModelsClient(
            httpClient = OkHttpClient(),
            endpointOverride = server.url("/v1beta/models").toString()
        )
        val result = client.fetchAvailableModels("AIzaTestKey")
        assertTrue("Expected success, got ${result.exceptionOrNull()}", result.isSuccess)
        val models = result.getOrThrow()

        assertEquals(listOf("gemini-4.5-pro", "gemini-3.5-flash-lite", "gemini-2.5-flash-preview-04-17"), models.map { it.id })
        assertFalse(models.any { it.id.contains("embedding") || it.id.contains("image") })
        assertEquals("Fast free-tier friendly model", models[1].description)
    }

    @Test
    fun `thinkingConfigFor enables low thinking for Gemini 3+ and omits for Gemini 2_x`() {
        assertEquals("low", GeminiModels.thinkingConfigFor("gemini-3.5-flash-lite")?.thinkingLevel)
        assertEquals("low", GeminiModels.thinkingConfigFor("gemini-4-hydrogen")?.thinkingLevel)
        assertNotNull(GeminiModels.thinkingConfigFor("gemini-5.0-flash"))
        assertNull(GeminiModels.thinkingConfigFor("gemini-2.5-flash"))
        assertNull(GeminiModels.thinkingConfigFor("gemini-2.0-flash"))

        val merged = GeminiModels.resolveModelsList(
            dynamicModels = listOf(),
            selectedIds = listOf("gemini-5-custom")
        )
        assertTrue(merged.any { it.id == "gemini-5-custom" })
        assertTrue(merged.any { it.id == GeminiModels.FLASH_LITE })
        assertTrue(merged.any { it.id == GeminiModels.HYDROGEN })
    }
}
