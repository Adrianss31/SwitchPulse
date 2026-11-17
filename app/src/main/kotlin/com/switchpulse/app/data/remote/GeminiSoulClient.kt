package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Uses Gemini, OpenRouter or DeepSeek BYOK to extrapolate a player's deeper "Gamer Soul"
 * (archetype, taste pillars, red flags, psychological preferences) from their rated games,
 * personal comments, backlog and free-text preferences, and to discover 5-10 Switch games
 * outside the discounts feed.
 */
class GeminiSoulClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointOverride: String? = null,
    private val openAiEndpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val aiClient = AiCompletionClient(
        httpClient = httpClient,
        geminiEndpointOverride = endpointOverride,
        openRouterEndpointOverride = openAiEndpointOverride,
        deepSeekEndpointOverride = openAiEndpointOverride
    )

    fun computeSignature(
        modelId: String,
        likedGames: List<LikedGame>,
        userTastePrompt: String,
        provider: AiProvider = AiProvider.GEMINI
    ): String {
        val libSig = likedGames
            .sortedBy { it.id }
            .joinToString("|") {
                "${it.id}:${it.rating ?: 0}:${if (it.isOwned) 1 else 0}:${it.userNote.orEmpty().trim()}"
            }
        val raw = "${provider.id}:$modelId#$libSig#${userTastePrompt.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun generateGamerSoul(
        apiKey: String,
        likedGames: List<LikedGame>,
        modelId: String = GeminiModels.HYDROGEN,
        userTastePrompt: String = "",
        provider: AiProvider = AiProvider.GEMINI
    ): Result<GamerSoulProfile> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider, forSoul = true) }
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Inserisci prima la tua API key per ${provider.displayName} in Impostazioni."))
        }
        if (likedGames.isEmpty() && userTastePrompt.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Aggiungi o vota almeno un gioco in Libreria (o scrivi le preferenze IA)."))
        }

        val prompt = buildSoulPrompt(likedGames, userTastePrompt.trim())
        val rawResponse = when (
            val call = aiClient.generate(
                provider = provider,
                apiKey = cleanKey,
                modelId = effectiveModel,
                prompt = prompt,
                temperature = 0.45,
                maxOutputTokens = 4096,
                expectJsonObject = true,
                enableGeminiGoogleSearch = false
            )
        ) {
            is AiCallResult.Failure -> return@withContext Result.failure(IllegalStateException(call.message))
            is AiCallResult.Success -> call.text
        }

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
                signature = computeSignature(effectiveModel, likedGames, userTastePrompt, provider),
                generatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun discoverGamesForSoul(
        apiKey: String,
        soulProfile: GamerSoulProfile? = null,
        likedGames: List<LikedGame>,
        modelId: String = GeminiModels.HYDROGEN,
        userTastePrompt: String = "",
        excludePreviousTitles: List<String> = emptyList(),
        desiredCount: Int = 8,
        provider: AiProvider = AiProvider.GEMINI,
        dismissedTitles: List<String> = emptyList()
    ): Result<List<AiDiscoveryPick>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider, forSoul = true) }
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Inserisci prima la tua API key per ${provider.displayName} in Impostazioni."))
        }
        if (soulProfile == null && likedGames.isEmpty() && userTastePrompt.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Aggiungi o vota almeno un gioco in Libreria (o scrivi le preferenze IA)."))
        }

        val count = desiredCount.coerceIn(5, 10)
        val combinedExcluded = (excludePreviousTitles + dismissedTitles).distinct()
        val prompt = buildDiscoveryPrompt(
            soulProfile = soulProfile,
            likedGames = likedGames,
            userTastePrompt = userTastePrompt.trim(),
            excludePreviousTitles = combinedExcluded,
            count = count
        )
        val rawResponse = when (
            val call = aiClient.generate(
                provider = provider,
                apiKey = cleanKey,
                modelId = effectiveModel,
                prompt = prompt,
                temperature = if (combinedExcluded.isNotEmpty()) 0.85 else 0.5,
                maxOutputTokens = 4096,
                expectJsonObject = false,
                enableGeminiGoogleSearch = false
            )
        ) {
            is AiCallResult.Failure -> return@withContext Result.failure(IllegalStateException(call.message))
            is AiCallResult.Success -> call.text
        }

        val arrSlice = extractJsonArray(rawResponse)
            ?: return@withContext Result.failure(IllegalStateException("Lista consigli AI non leggibile"))

        val parsedList = runCatching {
            json.decodeFromString<List<DiscoveryItemDto>>(arrSlice)
        }.getOrElse {
            return@withContext Result.failure(IllegalStateException("Formato consigli AI non valido"))
        }

        val dismissedKeys = dismissedTitles.flatMap {
            listOf(DealTitleNormalizer.normalize(it), DealTitleNormalizer.canonicalKey(it))
        }.toSet()
        val previousKeys = excludePreviousTitles.flatMap {
            listOf(DealTitleNormalizer.normalize(it), DealTitleNormalizer.canonicalKey(it))
        }.toSet()

        val eligible = parsedList
            .mapNotNull { dto ->
                val title = dto.title.orEmpty().trim()
                if (title.isBlank()) return@mapNotNull null
                val norm = DealTitleNormalizer.normalize(title)
                val canonical = DealTitleNormalizer.canonicalKey(title)
                if (norm in dismissedKeys || canonical in dismissedKeys) return@mapNotNull null
                if (DealTitleNormalizer.matchesLibraryGame(title, null, likedGames)) return@mapNotNull null
                AiDiscoveryPick(
                    title = title,
                    pitchHook = dto.pitchHook.orEmpty().trim().take(72),
                    reason = dto.reason.orEmpty().trim().take(360),
                    estimatedPriceHint = dto.estimatedPriceHint.orEmpty().trim().take(32)
                )
            }
            .distinctBy { DealTitleNormalizer.canonicalKey(it.title) }

        val freshOnly = eligible.filterNot { pick ->
            DealTitleNormalizer.normalize(pick.title) in previousKeys ||
                DealTitleNormalizer.canonicalKey(pick.title) in previousKeys
        }
        val cleaned = if (freshOnly.size >= count || eligible.size == freshOnly.size) {
            freshOnly.take(10)
        } else {
            (freshOnly + eligible.filterNot { it in freshOnly }).take(count)
        }

        if (cleaned.isEmpty()) {
            Result.failure(IllegalStateException("Nessun titolo nuovo trovato fuori dalla tua libreria"))
        } else {
            Result.success(cleaned)
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
        soulProfile: GamerSoulProfile?,
        likedGames: List<LikedGame>,
        userTastePrompt: String,
        excludePreviousTitles: List<String>,
        count: Int
    ): String {
        val libraryTitles = if (likedGames.isEmpty()) "(nessuno)" else likedGames.joinToString(", ") { it.title }
        val prevBlock = if (excludePreviousTitles.isEmpty()) "" else {
            "\nTITOLI GIÀ PROPOSTI IN PRECEDENZA — NON RIPETERLI PER NESSUN MOTIVO (scegli SOLO giochi diversi e freschi):\n${excludePreviousTitles.joinToString(", ")}\n"
        }
        val notesBlock = likedGames
            .filter { !it.userNote.isNullOrBlank() || it.rating != null || it.isOwned }
            .take(25)
            .joinToString("\n") { g ->
                val r = g.rating?.let { "${it}★" } ?: if (g.isOwned) "Posseduto" else "Salvato"
                val ownedSuffix = if (g.isOwned && g.rating != null) " · posseduto" else ""
                val n = g.userNote?.trim()?.takeIf { it.isNotEmpty() }?.let { " — \"$it\"" }.orEmpty()
                "- [$r$ownedSuffix] ${g.title}$n"
            }.ifBlank { "(nessuno)" }

        val soulSection = if (soulProfile != null) {
            """
            ANIMA DEL GIOCATORE:
            Archetipo: ${soulProfile.archetypeTitle}
            Ritratto: ${soulProfile.soulSummary}
            Pilastri chiave: ${soulProfile.corePillars.joinToString(" | ")}
            Cosa evita: ${soulProfile.redFlags.joinToString(" | ")}
            """.trimIndent()
        } else {
            "ANIMA DEL GIOCATORE: (non ancora generata — basati con precisione su voti, note e preferenze testuali)"
        }

        return """
            Sei un curatore videoludico esperto di Nintendo Switch e Nintendo Switch 2.
            Consiglia esattamente $count videogiochi disponibili su Nintendo Switch / Switch 2 da scoprire, perfettamente coerenti con il profilo dell'utente.

            REGOLE TASSATIVE:
            1. I giochi NON devono essere necessariamente in sconto ora: puoi consigliare capolavori a prezzo pieno, gemme indie, cult o classici perfetti per i suoi gusti.
            2. DIVIETO ASSOLUTO di consigliare qualsiasi titolo già presente in `GIÀ IN LIBRERIA / GIÀ VOTATI / POSSEDUTI` (incluse edizioni speciali, deluxe o remastered dello stesso gioco).
            3. Dai varietà reale ed evita qualsiasi titolo presente nella lista `TITOLI GIÀ PROPOSTI IN PRECEDENZA`.

            $soulSection
            Preferenze testuali: ${userTastePrompt.ifBlank { "(nessuna)" }}

            DETTAGLIO VOTI, POSSEDUTI & COMMENTI:
            $notesBlock

            GIÀ IN LIBRERIA / GIÀ VOTATI / POSSEDUTI (ESCLUSI TASSATIVAMENTE):
            $libraryTitles
            $prevBlock
            Rispondi ESCLUSIVAMENTE con un array JSON valido di $count elementi:
            [{"title":"Nome esatto del gioco su Switch","pitchHook":"<gancio sintetico 4-7 parole>","reason":"<2 frasi in italiano: perché è perfetto per i tuoi gusti e cosa evita delle tue red flag>","estimatedPriceHint":"<es. ~19,99 €>"}]
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
        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val ends = cleaned.indices.filter { cleaned[it] == ']' }.asReversed()
        val starts = cleaned.indices.filter { cleaned[it] == '[' }
        for (end in ends) {
            for (start in starts) {
                if (start >= end) continue
                val slice = cleaned.substring(start, end + 1)
                if (runCatching { json.decodeFromString<List<DiscoveryItemDto>>(slice) }.isSuccess) {
                    return slice
                }
            }
        }
        return null
    }

    companion object {
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
