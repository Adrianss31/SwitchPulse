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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object GeminiModels {
    const val HYDROGEN = "gemini-4-hydrogen"
    const val ARGON = "gemini-4-argon"
    const val FLASH_LITE = "gemini-3.5-flash-lite"

    val OPTIONS: List<Pair<String, String>> = listOf(
        HYDROGEN to "Gemini 4 Hydrogen (consigliato)",
        ARGON to "Gemini 4 Argon (ragionamento)",
        FLASH_LITE to "Gemini 3.5 Flash-Lite (rapido)"
    )

    fun thinkingConfigFor(modelId: String): GeminiThinkingConfig? {
        return if (modelId.startsWith("gemini-4") || modelId.startsWith("gemini-3")) {
            GeminiThinkingConfig(thinkingLevel = "low")
        } else {
            null
        }
    }
}

data class SemanticRerankEntry(
    val score: Double,
    val reason: String?
)

data class GeminiRerankOutcome(
    val scores: Map<String, SemanticRerankEntry>,
    val statusSummary: String,
    val isError: Boolean = false
)

/**
 * Optional "Bring Your Own Key" Gemini semantic reranker defaulting to `gemini-4-hydrogen`.
 * Evaluates both positive (4-5★) and negative (1-2★) tastes in a single compact batch,
 * and surfaces explicit diagnostic feedback so the user can see when & how AI acted.
 */
class GeminiReranker(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointBaseUrl: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun resolveEndpoint(modelId: String): String {
        val override = endpointBaseUrl?.takeIf { it.isNotBlank() }
        if (override != null) return override
        val model = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    }

    fun computeSignature(
        modelId: String,
        likedGames: List<LikedGame>,
        candidates: List<Deal>
    ): String {
        val ratedSig = likedGames
            .filter { it.isPositiveRating || it.isNegativeRating }
            .sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.rating}" }
        val candSig = candidates
            .sortedByDescending { it.hotScore }
            .take(55)
            .joinToString(",") { it.id }
        val raw = "$modelId#$ratedSig#$candSig"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun rerank(
        apiKey: String,
        likedGames: List<LikedGame>,
        candidates: List<Deal>,
        modelId: String = GeminiModels.HYDROGEN
    ): GeminiRerankOutcome = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        val rated = likedGames.filter { it.isPositiveRating || it.isNegativeRating }

        if (cleanKey.isBlank()) {
            return@withContext GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "Nessuna API key configurata · attivo solo motore locale",
                isError = false
            )
        }
        if (rated.isEmpty()) {
            return@withContext GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "Chiave salvata ($effectiveModel) · vota almeno un gioco (1–2★ o 4–5★) per attivare il rerank AI",
                isError = false
            )
        }
        if (candidates.isEmpty()) {
            return@withContext GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "Nessun candidato da valutare",
                isError = false
            )
        }

        val topCandidates = candidates
            .sortedByDescending { it.hotScore }
            .take(55)

        val prompt = buildPrompt(rated.take(20), topCandidates)
        val requestBodyObj = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2,
                thinkingConfig = GeminiModels.thinkingConfigFor(effectiveModel)
            )
        )

        val payloadJson = json.encodeToString(requestBodyObj)
        val request = Request.Builder()
            .url(resolveEndpoint(effectiveModel))
            .header("x-goog-api-key", cleanKey)
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date())

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiErr = extractErrorMessage(rawBody)
                    return@use GeminiRerankOutcome(
                        scores = emptyMap(),
                        statusSummary = "⚠️ Errore $effectiveModel HTTP ${response.code}: ${apiErr.ifBlank { "richiesta rifiutata" }} ($timeStr)",
                        isError = true
                    )
                }

                val envelope = json.decodeFromString<GeminiGenerateResponse>(rawBody)
                val modelText = envelope.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull { !it.text.isNullOrBlank() }
                    ?.text
                    .orEmpty()
                    .trim()

                val parsed = parseModelScores(modelText)
                if (parsed.isEmpty()) {
                    GeminiRerankOutcome(
                        scores = emptyMap(),
                        statusSummary = "⚠️ $effectiveModel ha risposto alle $timeStr ma nessun punteggio JSON era leggibile (fallback locale)",
                        isError = true
                    )
                } else {
                    val posCount = parsed.values.count { it.score >= 60.0 }
                    val negCount = parsed.values.count { it.score <= 38.0 }
                    GeminiRerankOutcome(
                        scores = parsed,
                        statusSummary = "✨ $effectiveModel attivo ($timeStr) · ${topCandidates.size} offerte analizzate: $posCount consigliate, $negCount sconsigliate",
                        isError = false
                    )
                }
            }
        }.getOrElse { err ->
            GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "⚠️ Timeout/errore rete $effectiveModel: ${err.message ?: "sconosciuto"} ($timeStr)",
                isError = true
            )
        }
    }

    suspend fun testKey(
        apiKey: String,
        modelId: String = GeminiModels.HYDROGEN
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Inserisci prima una API key Gemini."))
        }

        val requestBodyObj = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = "Rispondi esattamente con: OK")))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.0,
                maxOutputTokens = 16,
                thinkingConfig = GeminiModels.thinkingConfigFor(effectiveModel)
            )
        )
        val payloadJson = json.encodeToString(requestBodyObj)
        val request = Request.Builder()
            .url(resolveEndpoint(effectiveModel))
            .header("x-goog-api-key", cleanKey)
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startMs = System.currentTimeMillis()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                if (!response.isSuccessful) {
                    val msg = extractErrorMessage(rawBody).ifBlank { "HTTP ${response.code}" }
                    error("HTTP ${response.code}: $msg")
                }
                val envelope = json.decodeFromString<GeminiGenerateResponse>(rawBody)
                val text = envelope.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty().trim()
                "✨ Connessione riuscita a $effectiveModel (${elapsed} ms${if (text.isNotBlank()) " · \"${text.take(24)}\"" else ""})"
            }
        }
    }

    internal fun parseModelScores(rawJsonText: String): Map<String, SemanticRerankEntry> {
        val cleaned = rawJsonText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.isBlank()) return emptyMap()

        val arrayCandidate = extractJsonArray(cleaned) ?: cleaned
        return runCatching {
            val entries = json.decodeFromString<List<GeminiScoreItem>>(arrayCandidate)
            entries.associate { item ->
                item.id to SemanticRerankEntry(
                    score = item.score.coerceIn(0.0, 100.0),
                    reason = item.reason?.trim()?.takeIf { it.isNotBlank() }?.take(56)
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun extractErrorMessage(rawBody: String): String {
        val decoded = runCatching {
            json.decodeFromString<GeminiErrorEnvelope>(rawBody).error?.message
        }.getOrNull()
        return decoded?.take(120).orEmpty()
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun buildPrompt(ratedGames: List<LikedGame>, candidates: List<Deal>): String {
        val positives = ratedGames.filter { it.isPositiveRating }.sortedByDescending { it.rating ?: 0 }
        val negatives = ratedGames.filter { it.isNegativeRating }.sortedBy { it.rating ?: 0 }

        val posText = if (positives.isEmpty()) "(nessuno)" else positives.joinToString("\n") { game ->
            val tags = (game.steamTagNames.take(5) + game.gameCategories).distinct().joinToString(", ")
            "- [${game.rating}★] ${game.title} [${tags.ifBlank { game.publisher }}]"
        }
        val negText = if (negatives.isEmpty()) "(nessuno)" else negatives.joinToString("\n") { game ->
            val tags = (game.steamTagNames.take(5) + game.gameCategories).distinct().joinToString(", ")
            "- [${game.rating}★] ${game.title} [${tags.ifBlank { game.publisher }}]"
        }
        val candidatesText = candidates.joinToString("\n") { deal ->
            val tags = (deal.steamTagNames.take(4) + deal.gameCategories).distinct().joinToString(", ")
            val blurb = deal.excerpt?.take(90)?.replace("\n", " ").orEmpty()
            "- id=${deal.id} | ${deal.title} (${deal.publisher}) | [$tags] | -${deal.discountPercent}% | $blurb"
        }

        return """
            Sei un curatore esperto di videogiochi Nintendo Switch. Valuta quanto ogni gioco in sconto è affine ai gusti dell'utente confrontando meccaniche, ritmo, struttura e atmosfera.
            Scala score 0..100:
            - 60..100: consigliato/affine ai giochi 4-5★
            - 40..59: neutro (puoi ometterlo)
            - 0..38: sconsigliato perché molto simile ai giochi 1-2★ o distante dai gusti
            Restituisci SOLO un array JSON valido di oggetti (omitti i neutri 40..59):
            [{"id":"<id>","score":<0..100>,"reason":"<max 7 parole in italiano che citano il titolo/genere>"}]

            GIOCHI APPREZZATI (4-5★):
            $posText

            GIOCHI SCONSIGLIATI / NON PIACIUTI (1-2★):
            $negText

            OFFERTE CANDIDATE:
            $candidatesText
        """.trimIndent()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(24, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
data class GeminiThinkingConfig(
    val thinkingLevel: String
)

@Serializable
internal data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
internal data class GeminiTool(
    val google_search: Map<String, String>
)

@Serializable
internal data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
internal data class GeminiPart(
    val text: String? = null
)

@Serializable
internal data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: GeminiThinkingConfig? = null
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

@Serializable
internal data class GeminiErrorEnvelope(
    val error: GeminiErrorDetail? = null
)

@Serializable
internal data class GeminiErrorDetail(
    val code: Int? = null,
    val message: String? = null
)
