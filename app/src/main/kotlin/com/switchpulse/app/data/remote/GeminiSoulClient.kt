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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Uses Gemini, OpenRouter or DeepSeek BYOK to extrapolate a player's deeper "Gamer Soul"
 * (archetype, taste pillars, red flags, psychological preferences) from their rated games,
 * personal comments, backlog and free-text preferences, and to discover Switch games
 * outside the discounts feed (5 tailored + 2 hidden gems).
 */
class GeminiSoulClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointOverride: String? = null,
    private val openAiEndpointOverride: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
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
        desiredCount: Int = 7,
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
                maxOutputTokens = 8192,
                expectJsonObject = false,
                enableGeminiGoogleSearch = false
            )
        ) {
            is AiCallResult.Failure -> return@withContext Result.failure(IllegalStateException(call.message))
            is AiCallResult.Success -> call.text
        }

        val parsedList = parseDiscoveryItems(rawResponse)
        if (parsedList.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Lista consigli AI non leggibile"))
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
                    estimatedPriceHint = dto.estimatedPriceHint.orEmpty().trim().take(32),
                    isHiddenGem = dto.isHiddenGem
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

        val splitRule = if (count >= 6) {
            val gemCount = 2
            val coreCount = count - gemCount
            """
            COMPOZIONE ESATTA DEI $count GIOCHI:
            - Primi $coreCount giochi ("isHiddenGem": false): consigli principali su misura per la tua anima (anche titoli cult o apprezzati, non necessariamente in sconto).
            - Ultimi $gemCount giochi ("isHiddenGem": true): 2 vere GEMME NASCOSTE su Nintendo Switch / Switch 2 — titoli indie, di nicchia o sottovalutati poco conosciuti dal grande pubblico ma sorprendentemente in linea coi tuoi pilastri.
            """.trimIndent()
        } else {
            "Consiglia esattamente $count videogiochi disponibili su Nintendo Switch / Switch 2 da scoprire (\"isHiddenGem\": false)."
        }

        return """
            Sei un curatore videoludico esperto di Nintendo Switch e Nintendo Switch 2.
            $splitRule

            REGOLE TASSATIVE:
            1. I giochi NON devono essere necessariamente in sconto ora: puoi consigliare capolavori a prezzo pieno, gemme indie, cult o classici perfetti per i suoi gusti.
            2. DIVIETO ASSOLUTO di consigliare qualsiasi titolo già presente in `GIÀ IN LIBRERIA / GIÀ VOTATI / POSSEDUTI` (incluse edizioni speciali, deluxe o remastered dello stesso gioco).
            3. Dai varietà reale ed evita qualsiasi titolo presente nella lista `TITOLI GIÀ PROPOSTI IN PRECEDENZA`.
            4. Mantieni `reason` conciso (1-2 frasi brevi in italiano, 15-30 parole) così l'array JSON resti compatto e valido.

            $soulSection
            Preferenze testuali: ${userTastePrompt.ifBlank { "(nessuna)" }}

            DETTAGLIO VOTI, POSSEDUTI & COMMENTI:
            $notesBlock

            GIÀ IN LIBRERIA / GIÀ VOTATI / POSSEDUTI (ESCLUSI TASSATIVAMENTE):
            $libraryTitles
            $prevBlock
            Rispondi ESCLUSIVAMENTE con un array JSON valido di $count elementi (senza testo prima o dopo):
            [{"title":"Nome esatto su Switch","pitchHook":"<gancio 4-7 parole>","reason":"<1-2 frasi concise in italiano>","estimatedPriceHint":"<es. ~19,99 €>","isHiddenGem":false}]
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
        val cleaned = stripMarkdownFences(text)
        val end = cleaned.lastIndexOf('}')
        if (end <= 0) return null
        val starts = cleaned.indices.filter { cleaned[it] == '{' && it < end }
        for (start in starts.asReversed()) {
            val slice = cleaned.substring(start, end + 1)
            if (runCatching { json.parseToJsonElement(slice) }.isSuccess) return slice
        }
        return null
    }

    /**
     * Multi-stage lenient parser supporting:
     * 1. Clean top-level JSON arrays (`[...]`)
     * 2. Wrapped JSON objects (`{"picks":[...]}`, `{"core":[...], "hiddenGems":[...]}`)
     * 3. Lenient primitive coercion (numeric prices, string booleans)
     * 4. Object-by-object salvage if output was truncated before the closing `]`
     */
    internal fun parseDiscoveryItems(rawText: String): List<DiscoveryItemDto> {
        val cleaned = stripMarkdownFences(rawText)
        if (cleaned.isBlank()) return emptyList()

        // Stage 1: Parse a full valid JSON array or wrapper object containing arrays.
        val ends = cleaned.indices.filter { cleaned[it] == ']' }.asReversed()
        val starts = cleaned.indices.filter { cleaned[it] == '[' }
        for (end in ends) {
            for (start in starts) {
                if (start >= end) continue
                val slice = cleaned.substring(start, end + 1)
                val parsedEl = runCatching { json.parseToJsonElement(slice) }.getOrNull()
                if (parsedEl is JsonArray) {
                    val items = parsedEl.mapNotNull { el ->
                        (el as? JsonObject)?.let { parseDiscoveryObject(it, defaultHiddenGem = false) }
                    }
                    if (items.isNotEmpty()) return items
                }
            }
        }

        // Stage 2: Check top-level JSON wrapper objects containing arrays (e.g. {"games":[...], "hiddenGems":[...]}).
        val objEnd = cleaned.lastIndexOf('}')
        if (objEnd > 0) {
            for (objStart in cleaned.indices.filter { cleaned[it] == '{' && it < objEnd }) {
                val slice = cleaned.substring(objStart, objEnd + 1)
                val parsedObj = runCatching { json.parseToJsonElement(slice) }.getOrNull() as? JsonObject
                    ?: continue
                val collected = ArrayList<DiscoveryItemDto>()
                parsedObj.forEach { (key, value) ->
                    if (value is JsonArray) {
                        val keyLooksGem = key.contains("gem", ignoreCase = true) ||
                            key.contains("nascost", ignoreCase = true)
                        value.forEach { itemEl ->
                            (itemEl as? JsonObject)?.let { itemObj ->
                                parseDiscoveryObject(itemObj, defaultHiddenGem = keyLooksGem)?.let(collected::add)
                            }
                        }
                    }
                }
                if (collected.isNotEmpty()) return collected
            }
        }

        // Stage 3: Object-by-object salvage (handles truncated arrays missing `]`, NDJSON, or single object).
        return extractBalancedJsonObjects(cleaned).mapNotNull { objStr ->
            val obj = runCatching { json.parseToJsonElement(objStr).jsonObject }.getOrNull() ?: return@mapNotNull null
            parseDiscoveryObject(obj, defaultHiddenGem = false)
        }
    }

    private fun parseDiscoveryObject(
        obj: JsonObject,
        defaultHiddenGem: Boolean
    ): DiscoveryItemDto? {
        val title = firstString(obj, "title", "name", "gioco", "titolo")?.takeIf { it.isNotBlank() }
            ?: return null
        val pitchHook = firstString(obj, "pitchHook", "hook", "pitch", "tagline", "sottotitolo")
        val reason = firstString(obj, "reason", "why", "motivo", "descrizione")
        val priceHint = firstString(obj, "estimatedPriceHint", "priceHint", "price", "prezzo")
        val flag = firstBool(obj, "isHiddenGem", "hiddenGem", "is_hidden_gem", "gem", "gemma")
            ?: run {
                val cat = firstString(obj, "category", "type", "categoria").orEmpty()
                if (cat.contains("gem", ignoreCase = true) || cat.contains("nascost", ignoreCase = true)) true
                else defaultHiddenGem
            }
        return DiscoveryItemDto(
            title = title,
            pitchHook = pitchHook,
            reason = reason,
            estimatedPriceHint = priceHint,
            isHiddenGem = flag
        )
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val p = obj[k] as? JsonPrimitive ?: continue
            val v = p.contentOrNull?.trim()
            if (!v.isNullOrEmpty() && !v.equals("null", ignoreCase = true)) return v
        }
        return null
    }

    private fun firstBool(obj: JsonObject, vararg keys: String): Boolean? {
        for (k in keys) {
            val p = obj[k] as? JsonPrimitive ?: continue
            p.booleanOrNull?.let { return it }
            val text = p.contentOrNull?.trim()?.lowercase()
            if (text == "true" || text == "1" || text == "yes" || text == "si" || text == "sì") return true
            if (text == "false" || text == "0" || text == "no") return false
        }
        return null
    }

    private fun stripMarkdownFences(text: String): String =
        text.replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "").trim()

    private fun extractBalancedJsonObjects(text: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var objStart = -1
        for (i in text.indices) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inString) {
                when (ch) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && objStart >= 0) {
                            out += text.substring(objStart, i + 1)
                            objStart = -1
                        }
                    }
                }
            }
        }
        return out
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
internal data class DiscoveryItemDto(
    val title: String? = null,
    val pitchHook: String? = null,
    val reason: String? = null,
    val estimatedPriceHint: String? = null,
    val isHiddenGem: Boolean = false
)
