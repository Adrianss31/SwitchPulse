package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.LikedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SemanticRerankEntry(
    val score: Double,
    val reason: String?
)

/**
 * Optional "Bring Your Own Key" Gemini semantic reranker.
 * Sends a single compact JSON batch comparing liked games vs shortlisted deals and degrades cleanly
 * if no key is provided or if the API fails.
 */
class GeminiReranker(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointBaseUrl: String = DEFAULT_ENDPOINT
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun rerank(
        apiKey: String,
        likedGames: List<LikedGame>,
        candidates: List<Deal>
    ): Map<String, SemanticRerankEntry> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || likedGames.isEmpty() || candidates.isEmpty()) {
            return@withContext emptyMap()
        }

        val topCandidates = candidates
            .sortedByDescending { it.hotScore }
            .take(55)

        val prompt = buildPrompt(likedGames.take(15), topCandidates)
        val requestBodyObj = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2
            )
        )

        val payloadJson = json.encodeToString(requestBodyObj)
        val request = Request.Builder()
            .url(endpointBaseUrl)
            .header("x-goog-api-key", cleanKey)
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyMap()
                val rawBody = response.body?.string().orEmpty()
                val envelope = json.decodeFromString<GeminiGenerateResponse>(rawBody)
                val modelText = envelope.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    .orEmpty()
                    .trim()

                parseModelScores(modelText)
            }
        }.getOrDefault(emptyMap())
    }

    internal fun parseModelScores(rawJsonText: String): Map<String, SemanticRerankEntry> {
        val cleaned = rawJsonText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.isBlank()) return emptyMap()

        return runCatching {
            val entries = json.decodeFromString<List<GeminiScoreItem>>(cleaned)
            entries.associate { item ->
                item.id to SemanticRerankEntry(
                    score = item.score.coerceIn(0.0, 100.0),
                    reason = item.reason?.trim()?.takeIf { it.isNotBlank() }?.take(48)
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun buildPrompt(liked: List<LikedGame>, candidates: List<Deal>): String {
        val likedText = liked.joinToString("\n") { game ->
            val tags = (game.steamTagNames.take(5) + game.gameCategories).distinct().joinToString(", ")
            "- ${game.title} [${tags.ifBlank { game.publisher }}]"
        }
        val candidatesText = candidates.joinToString("\n") { deal ->
            val tags = (deal.steamTagNames.take(4) + deal.gameCategories).distinct().joinToString(", ")
            val blurb = deal.excerpt?.take(90)?.replace("\n", " ").orEmpty()
            "- id=${deal.id} | ${deal.title} (${deal.publisher}) | [$tags] | -${deal.discountPercent}% | $blurb"
        }

        return """
            Sei un esperto di videogiochi Nintendo Switch. Valuta quanto ogni gioco in sconto è affine ai gusti dell'utente in base a meccaniche, ritmo, feeling e atmosfera.
            Restituisci SOLO un array JSON di oggetti: [{"id":"<id>","score":<0..100>,"reason":"<max 6 parole in italiano che citano il titolo simile>"}].
            Includi solo i titoli con score >= 45.

            GIOCHI PIACIUTI DALL'UTENTE:
            $likedText

            OFFERTE CANDIDATE:
            $candidatesText
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
internal data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiPart(
    val text: String
)

@Serializable
internal data class GeminiGenerationConfig(
    val responseMimeType: String,
    val temperature: Double
)

@Serializable
internal data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiResponseContent? = null
)

@Serializable
internal data class GeminiResponseContent(
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
internal data class GeminiScoreItem(
    val id: String,
    val score: Double = 0.0,
    val reason: String? = null
)
