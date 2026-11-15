package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GamerSoulProfile
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
import java.util.concurrent.TimeUnit

/**
 * Uses Gemini BYOK to extrapolate a player's deeper "Gamer Soul" (archetype, taste pillars,
 * red flags, psychological preferences) from their rated games, personal comments, backlog and
 * free-text preferences, and to discover 5-10 Switch games outside the discounts feed.
 */
class GeminiSoulClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun resolveEndpoint(modelId: String): String {
        val override = endpointOverride?.takeIf { it.isNotBlank() }
        if (override != null) return override
        val model = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    }

    fun computeSignature(
        modelId: String,
        likedGames: List<LikedGame>,
        userTastePrompt: String
    ): String {
        val libSig = likedGames
            .sortedBy { it.id }
            .joinToString("|") {
                "${it.id}:${it.rating ?: 0}:${if (it.isOwned) 1 else 0}:${it.userNote.orEmpty().trim()}"
            }
        val raw = "$modelId#$libSig#${userTastePrompt.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun generateGamerSoul(
        apiKey: String,
        likedGames: List<LikedGame>,
        modelId: String = GeminiModels.HYDROGEN,
        userTastePrompt: String = ""
    ): Result<GamerSoulProfile> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Inserisci prima la tua chiave API Gemini in Filtri & IA."))
        }
        if (likedGames.isEmpty() && userTastePrompt.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Aggiungi o vota almeno un gioco in Libreria (o scrivi le preferenze IA)."))
        }

        val prompt = buildSoulPrompt(likedGames, userTastePrompt.trim())
        val rawResponse = executeGeminiJsonCall(cleanKey, effectiveModel, prompt)
            .getOrElse { return@withContext Result.failure(it) }

        val objSlice = extractJsonObject(rawResponse)
            ?: return@withContext Result.failure(IllegalStateException("Risposta profilo AI non leggibile"))

        val parsed = runCatching {
            json.decodeFromString<SoulPayloadDto>(objSlice)
        }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Formato profilo AI non valido"))
        }

        val archetype = parsed.archetypeTitle.orEmpty().trim()
        val summary = parsed.soulSummary.orEmpty().trim()
        if (archetype.isBlank() || summary.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Profilo AI incompleto"))
        }

        Result.success(
            GamerSoulProfile(
                archetypeTitle = archetype.take(64),
                soulSummary = summary.take(700),
                corePillars = parsed.corePillars.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.take(5),
                redFlags = parsed.redFlags.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.take(4),
                signature = computeSignature(effectiveModel, likedGames, userTastePrompt),
                generatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun discoverGamesForSoul(
        apiKey: String,
        soulProfile: GamerSoulProfile,
        likedGames: List<LikedGame>,
        modelId: String = GeminiModels.HYDROGEN,
        userTastePrompt: String = "",
        excludePreviousTitles: List<String> = emptyList(),
        desiredCount: Int = 7
    ): Result<List<AiDiscoveryPick>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Inserisci prima la tua chiave API Gemini in Filtri & IA."))
        }

        val count = desiredCount.coerceIn(5, 10)
        val prompt = buildDiscoveryPrompt(
            soulProfile = soulProfile,
            likedGames = likedGames,
            userTastePrompt = userTastePrompt.trim(),
            excludePreviousTitles = excludePreviousTitles,
            count = count
        )
        val rawResponse = executeGeminiJsonCall(cleanKey, effectiveModel, prompt)
            .getOrElse { return@withContext Result.failure(it) }

        val arrSlice = extractJsonArray(rawResponse)
            ?: return@withContext Result.failure(IllegalStateException("Lista consigli AI non leggibile"))

        val parsedList = runCatching {
            json.decodeFromString<List<DiscoveryItemDto>>(arrSlice)
        }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Formato consigli AI non valido"))
        }

        val libraryNorms = likedGames.map { DealTitleNormalizer.normalize(it.title) }.toSet()
        val previousNorms = excludePreviousTitles.map { DealTitleNormalizer.normalize(it) }.toSet()

        val cleaned = parsedList
            .mapNotNull { dto ->
                val title = dto.title.orEmpty().trim()
                if (title.isBlank()) return@mapNotNull null
                val norm = DealTitleNormalizer.normalize(title)
                if (norm in libraryNorms) return@mapNotNull null
                AiDiscoveryPick(
                    title = title,
                    pitchHook = dto.pitchHook.orEmpty().trim().take(72),
                    reason = dto.reason.orEmpty().trim().take(360),
                    estimatedPriceHint = dto.estimatedPriceHint.orEmpty().trim().take(32)
                )
            }
            .distinctBy { DealTitleNormalizer.normalize(it.title) }
            .sortedBy { DealTitleNormalizer.normalize(it.title) in previousNorms }
            .take(10)

        if (cleaned.isEmpty()) {
            Result.failure(IllegalStateException("Nessun titolo nuovo trovato fuori dalla tua libreria"))
        } else {
            Result.success(cleaned)
        }
    }

    private fun executeGeminiJsonCall(
        apiKey: String,
        modelId: String,
        prompt: String
    ): Result<String> = runCatching {
        val reqObj = GeminiGenerateRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.45,
                maxOutputTokens = 4096,
                thinkingConfig = GeminiModels.thinkingConfigFor(modelId)
            )
        )
        val request = Request.Builder()
            .url(resolveEndpoint(modelId))
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(reqObj).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching {
                    json.decodeFromString<GeminiErrorEnvelope>(raw).error?.message
                }.getOrNull().orEmpty().trim()
                throw IllegalStateException("HTTP ${response.code}${if (err.isNotBlank()) ": ${err.take(160)}" else ""}")
            }
            val envelope = json.decodeFromString<GeminiGenerateResponse>(raw)
            val text = envelope.candidates
                .firstOrNull()
                ?.content
                ?.parts
                .orEmpty()
                .filter { it.thought != true && !it.text.isNullOrBlank() }
                .joinToString("\n") { it.text.orEmpty() }
                .trim()
            if (text.isBlank()) throw IllegalStateException("Risposta vuota da Gemini")
            text
        }
    }

    private fun buildSoulPrompt(
        likedGames: List<LikedGame>,
        userTastePrompt: String
    ): String {
        val libraryBlock = formatLibraryBlock(likedGames)
        val extraPrefs = userTastePrompt.ifBlank { "(nessuna preferenza extra)" }

        return """
            Sei uno psicologo del game design e curatore videoludico.
            Analizza TUTTI i giochi nella libreria dell'utente, i suoi voti 1-5★, i commenti personali in cui spiega perché ha amato o scartato un titolo e le sue preferenze testuali.
            Devi estrapolare l'ANIMA profonda di questo videogiocatore: non limitarti a elencare i generi, ma cogli che tipo di soddisfazione ludica cerca (es. flow meccanico, rigore tattico, immediatezza arcade, senso di scoperta, ritmo, tolleranza alla difficoltà, insofferenza per dialoghi o grinding).

            PREFERENZE TESTUALI:
            $extraPrefs

            LIBRERIA E COMMENTI UTENTE:
            $libraryBlock

            Rispondi ESCLUSIVAMENTE con un singolo oggetto JSON valido:
            {"archetypeTitle":"<nome evocativo breve di 2-5 parole>","soulSummary":"<3-4 frasi in italiano incisive e personali che descrivono la tua anima da videogiocatore e le ragioni profonde dei tuoi gusti>","corePillars":["<pilastro 1>","<pilastro 2>","<pilastro 3>","<pilastro 4>"],"redFlags":["<cosa ti fa abbandonare un gioco 1>","<cosa ti allontana 2>"]}
        """.trimIndent()
    }

    private fun buildDiscoveryPrompt(
        soulProfile: GamerSoulProfile,
        likedGames: List<LikedGame>,
        userTastePrompt: String,
        excludePreviousTitles: List<String>,
        count: Int
    ): String {
        val libraryTitles = if (likedGames.isEmpty()) "(nessuno)" else likedGames.joinToString(", ") { it.title }
        val prevBlock = if (excludePreviousTitles.isEmpty()) "" else {
            "\nEVITA SE POSSIBILE ANCHE QUESTI TITOLI GIÀ PROPOSTI IN PRECEDENZA:\n${excludePreviousTitles.joinToString(", ")}\n"
        }
        val notesBlock = likedGames
            .filter { !it.userNote.isNullOrBlank() || it.rating != null }
            .take(15)
            .joinToString("\n") { g ->
                val r = g.rating?.let { "${it}★" } ?: "Posseduto"
                val n = g.userNote?.trim()?.takeIf { it.isNotEmpty() }?.let { " — \"$it\"" }.orEmpty()
                "- [$r] ${g.title}$n"
            }.ifBlank { "(nessuno)" }

        return """
            Sei un curatore videoludico esperto di Nintendo Switch e Nintendo Switch 2.
            Sulla base dell'ANIMA DA VIDEOGIOCATORE già estratta per l'utente, consiglia esattamente $count videogiochi disponibili su Nintendo Switch / Switch 2 da provare.

            REGOLE TASSATIVE:
            1. I giochi NON devono essere necessariamente in sconto ora: puoi consigliare capolavori a prezzo pieno, gemme indie, cult o classici purché perfetti per questa anima.
            2. DIVIETO ASSOLUTO di consigliare giochi già presenti in `GIÀ IN LIBRERIA` (controlla con precisione ogni titolo ed edizione).
            3. Dai varietà ma resta fedelissimo allo spirito del profilo e alle note sui voti.

            ANIMA DEL GIOCATORE:
            Archetipo: ${soulProfile.archetypeTitle}
            Ritratto: ${soulProfile.soulSummary}
            Pilastri chiave: ${soulProfile.corePillars.joinToString(" | ")}
            Cosa evita: ${soulProfile.redFlags.joinToString(" | ")}
            Preferenze testuali: ${userTastePrompt.ifBlank { "(nessuna)" }}

            DETTAGLIO VOTI & COMMENTI:
            $notesBlock

            GIÀ IN LIBRERIA (DA ESCLUDERE TASSATIVAMENTE):
            $libraryTitles
            $prevBlock
            Rispondi ESCLUSIVAMENTE con un array JSON valido di $count elementi:
            [{"title":"Nome esatto del gioco su Switch","pitchHook":"<gancio sintetico 4-7 parole>","reason":"<2 frasi in italiano: perché incarna esattamente la tua anima da giocatore e in cosa evita le tue red flag>","estimatedPriceHint":"<es. ~19,99 €>"}]
        """.trimIndent()
    }

    private fun formatLibraryBlock(likedGames: List<LikedGame>): String {
        if (likedGames.isEmpty()) return "(nessun gioco ancora in libreria)"
        return likedGames.take(30).joinToString("\n") { g ->
            val tags = (g.steamTagNames.take(4) + g.gameCategories).distinct().joinToString(", ")
            val ratingLabel = g.rating?.let { "${it}★" } ?: if (g.isOwned) "Posseduto" else "Salvato"
            val ownedTag = if (g.isOwned && g.rating != null) " · posseduto" else ""
            val note = g.userNote?.trim()?.takeIf { it.isNotEmpty() }?.let { " — Nota: \"$it\"" }.orEmpty()
            "- [$ratingLabel$ownedTag] ${g.title} [${tags.ifBlank { g.publisher }}]$note"
        }
    }

    private fun extractJsonObject(text: String): String? {
        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val end = cleaned.lastIndexOf('}')
        if (end <= 0) return null
        val starts = cleaned.indices.filter { cleaned[it] == '{' && it < end }
        for (start in starts.asReversed()) {
            val slice = cleaned.substring(start, end + 1)
            if (runCatching { json.parseToJsonElement(slice) }.isSuccess) return slice
        }
        return null
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class SoulPayloadDto(
    val archetypeTitle: String? = null,
    val soulSummary: String? = null,
    val corePillars: List<String> = emptyList(),
    val redFlags: List<String> = emptyList()
)

@Serializable
private data class DiscoveryItemDto(
    val title: String? = null,
    val pitchHook: String? = null,
    val reason: String? = null,
    val estimatedPriceHint: String? = null
)
