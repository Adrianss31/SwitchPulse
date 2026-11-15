package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches on-demand external critic/duration metadata (Metacritic & HowLongToBeat) and a deep
 * personalized AI analysis for a single game when the user opens its Detail Sheet.
 * - Resolves Steam `appdetails` (including on-demand Steam search if `steamAppId == null`) for Metascore.
 * - Tries Gemini with Google Search Grounding (`google_search`) first; if Search Grounding is rejected
 *   (e.g. Free Tier keys on Gemini 3+/4) or returns empty data, automatically falls back to direct
 *   structured Gemini reasoning (without `tools`) combining Metacritic/HLTB knowledge and personalized verdict.
 */
class GameInsightsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val steamAppDetailsUrl: String = "https://store.steampowered.com/api/appdetails",
    private val geminiEndpointOverride: String? = null,
    private val steamStoreSearchUrl: String? = null,
    private val metacriticBaseUrl: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchInsights(
        deal: Deal,
        geminiApiKey: String,
        geminiModel: String = GeminiModels.HYDROGEN,
        likedGames: List<LikedGame> = emptyList(),
        userTastePrompt: String = ""
    ): GameExternalInsights = withContext(Dispatchers.IO) {
        val resolvedAppId = deal.steamAppId ?: resolveSteamAppIdOnDemand(deal)
        val steamMeta = resolvedAppId?.let { fetchSteamMetacritic(it) }
        val directMeta = if (steamMeta == null) fetchDirectMetacritic(deal.title) else null
        val cleanKey = geminiApiKey.trim()
        val effectiveModel = geminiModel.trim().ifBlank { GeminiModels.HYDROGEN }
        val profileSig = computeProfileSignature(likedGames, userTastePrompt)

        val aiResult = if (cleanKey.isNotBlank()) {
            fetchWithGeminiAndFallback(
                deal = deal,
                apiKey = cleanKey,
                modelId = effectiveModel,
                likedGames = likedGames,
                userTastePrompt = userTastePrompt
            )
        } else {
            GeminiInsightsResult(payload = null, mode = AiMode.NONE, errorMessage = null)
        }

        val payload = aiResult.payload
        val officialMetaScore = steamMeta?.score ?: directMeta?.score
        val officialMetaUrl = steamMeta?.url ?: directMeta?.url
        val metacriticScore = officialMetaScore
            ?: payload?.metacriticScore?.takeIf { it in 1..100 }
            ?: deal.metacriticScore
        val metacriticUrl = officialMetaUrl?.takeIf { it.startsWith("http") }
            ?: payload?.metacriticUrl?.takeIf { it.startsWith("http") }

        val metaOrigin = when {
            steamMeta?.score != null -> "Steam"
            directMeta?.score != null -> "Metacritic"
            else -> null
        }

        val sourceLabel = when {
            payload != null && metaOrigin != null && aiResult.mode == AiMode.GROUNDED_SEARCH ->
                "$metaOrigin + ✨ $effectiveModel (Google Search)"
            payload != null && metaOrigin != null ->
                "$metaOrigin + ✨ $effectiveModel (Analisi AI & HLTB)"
            payload != null && aiResult.mode == AiMode.GROUNDED_SEARCH ->
                "✨ $effectiveModel (Google Search)"
            payload != null ->
                "✨ $effectiveModel (Stima critica & Analisi AI)"
            metaOrigin != null && aiResult.errorMessage != null ->
                "$metaOrigin · ${aiResult.errorMessage}"
            steamMeta != null ->
                "Steam Metacritic API"
            directMeta != null ->
                "Metacritic"
            aiResult.errorMessage != null ->
                "⚠️ ${aiResult.errorMessage}"
            cleanKey.isNotBlank() ->
                "Lookup AI non disponibile · usa i link rapidi"
            else ->
                "Aggiungi una chiave Gemini per analisi personalizzata e stime HLTB"
        }

        GameExternalInsights(
            dealId = deal.id,
            metacriticScore = metacriticScore,
            metacriticUrl = metacriticUrl,
            hltbMainHours = payload?.hltbMainHours?.takeIf { it.isNotBlank() },
            hltbMainExtraHours = payload?.hltbMainExtraHours?.takeIf { it.isNotBlank() },
            hltbCompletionistHours = payload?.hltbCompletionistHours?.takeIf { it.isNotBlank() },
            aiVerdict = payload?.verdict?.trim()?.takeIf { it.isNotBlank() },
            summaryNote = payload?.summaryNote?.trim()?.takeIf { it.isNotBlank() },
            sourceLabel = sourceLabel,
            profileSignature = profileSig,
            fetchedAtMs = System.currentTimeMillis()
        )
    }

    private fun resolveSteamAppIdOnDemand(deal: Deal): Int? {
        if (deal.publisher.lowercase().contains("nintendo")) return null
        val searchEndpoint = steamStoreSearchUrl?.takeIf { it.isNotBlank() }
            ?: if (geminiEndpointOverride == null) "https://store.steampowered.com/api/storesearch/" else return null
        return runCatching {
            val cleanTitle = DealTitleNormalizer.normalize(deal.title)
            if (cleanTitle.length < 2) return@runCatching null
            val url = searchEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("term", cleanTitle)
                .addQueryParameter("l", "english")
                .addQueryParameter("cc", "IT")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                val decoded = json.decodeFromString<SteamStoreSearchResponse>(raw)
                decoded.items.firstOrNull { item ->
                    val nameLower = item.name.orEmpty().lowercase()
                    !nameLower.contains("soundtrack") &&
                        !nameLower.contains("ost") &&
                        !nameLower.contains("artbook") &&
                        !nameLower.contains("demo")
                }?.id
            }
        }.getOrNull()
    }

    private fun fetchSteamMetacritic(appId: Int): SteamMetacriticBlock? = runCatching {
        val url = steamAppDetailsUrl.toHttpUrl().newBuilder()
            .addQueryParameter("appids", appId.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string().orEmpty()
            val rootObj = json.parseToJsonElement(raw).jsonObject
            val appEl: JsonElement = rootObj[appId.toString()] ?: return null
            val wrapper = json.decodeFromJsonElement(SteamAppDetailsWrapper.serializer(), appEl)
            if (wrapper.success == true) wrapper.data?.metacritic else null
        }
    }.getOrNull()

    internal fun fetchDirectMetacritic(rawTitle: String): SteamMetacriticBlock? {
        val baseUrl = metacriticBaseUrl?.takeIf { it.isNotBlank() }
            ?: if (geminiEndpointOverride == null) "https://www.metacritic.com" else return null
        val slug = buildMetacriticSlug(rawTitle)
        if (slug.length < 2) return null

        val targetUrl = "${baseUrl.trimEnd('/')}/game/$slug/"
        return runCatching {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string().orEmpty()
                val match = LD_METASCORE_REGEX.find(html)
                val score = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..100 }
                    ?: return@use null
                SteamMetacriticBlock(score = score, url = targetUrl)
            }
        }.getOrNull()
    }

    internal fun buildMetacriticSlug(rawTitle: String): String {
        val normalized = DealTitleNormalizer.normalize(
            rawTitle
                .replace("'", "")
                .replace("’", "")
                .replace("&", " and ")
        )
        return normalized
            .trim()
            .replace(Regex("\\s+"), "-")
            .trim('-')
    }

    private fun fetchWithGeminiAndFallback(
        deal: Deal,
        apiKey: String,
        modelId: String,
        likedGames: List<LikedGame>,
        userTastePrompt: String
    ): GeminiInsightsResult {
        val groundedAttempt = executeGeminiInsightsCall(
            deal = deal,
            apiKey = apiKey,
            modelId = modelId,
            likedGames = likedGames,
            userTastePrompt = userTastePrompt,
            useGoogleSearch = true
        )
        if (groundedAttempt.payload != null) {
            return groundedAttempt.copy(mode = AiMode.GROUNDED_SEARCH)
        }

        // Fallback for Free Tier keys / unavailable Search Grounding on Gemini 3+/4.
        val directAttempt = executeGeminiInsightsCall(
            deal = deal,
            apiKey = apiKey,
            modelId = modelId,
            likedGames = likedGames,
            userTastePrompt = userTastePrompt,
            useGoogleSearch = false
        )
        if (directAttempt.payload != null) {
            return directAttempt.copy(mode = AiMode.DIRECT_KNOWLEDGE)
        }

        val combinedError = directAttempt.errorMessage ?: groundedAttempt.errorMessage
        return GeminiInsightsResult(
            payload = null,
            mode = AiMode.NONE,
            errorMessage = combinedError
        )
    }

    private fun executeGeminiInsightsCall(
        deal: Deal,
        apiKey: String,
        modelId: String,
        likedGames: List<LikedGame>,
        userTastePrompt: String,
        useGoogleSearch: Boolean
    ): GeminiInsightsResult = runCatching {
        val endpoint = geminiEndpointOverride?.takeIf { it.isNotBlank() }
            ?: "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"

        val prompt = buildDeepPrompt(
            deal = deal,
            likedGames = likedGames,
            userTastePrompt = userTastePrompt,
            groundedSearch = useGoogleSearch
        )

        val thinking = GeminiModels.thinkingConfigFor(modelId)

        val reqObj = if (useGoogleSearch) {
            GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                tools = listOf(GeminiTool(emptyMap())),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.15,
                    maxOutputTokens = 4096,
                    thinkingConfig = thinking
                )
            )
        } else {
            GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.25,
                    maxOutputTokens = 4096,
                    thinkingConfig = thinking
                )
            )
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(reqObj).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching {
                    json.decodeFromString<GeminiErrorEnvelope>(raw).error?.message
                }.getOrNull().orEmpty().trim()
                val shortErr = "HTTP ${response.code}${if (err.isNotBlank()) ": ${err.take(120)}" else ""}"
                return@use GeminiInsightsResult(payload = null, mode = AiMode.NONE, errorMessage = shortErr)
            }

            val envelope = json.decodeFromString<GeminiGenerateResponse>(raw)
            val candidate = envelope.candidates.firstOrNull()
            val allText = candidate
                ?.content
                ?.parts
                .orEmpty()
                .filter { it.thought != true && !it.text.isNullOrBlank() }
                .mapNotNull { it.text }
                .joinToString("\n")
                .trim()

            val payload = parseGroundedPayload(allText)
            if (payload == null) {
                val finish = candidate?.finishReason?.takeIf { it.isNotBlank() } ?: "empty"
                val snippet = allText.replace("\n", " ").take(80)
                GeminiInsightsResult(
                    payload = null,
                    mode = AiMode.NONE,
                    errorMessage = "Risposta AI non interpretabile ($finish)${if (snippet.isNotBlank()) ": $snippet" else ""}"
                )
            } else {
                GeminiInsightsResult(payload = payload, mode = AiMode.DIRECT_KNOWLEDGE, errorMessage = null)
            }
        }
    }.getOrElse { err ->
        GeminiInsightsResult(
            payload = null,
            mode = AiMode.NONE,
            errorMessage = err.localizedMessage?.take(100) ?: "Errore di rete AI"
        )
    }

    private fun buildDeepPrompt(
        deal: Deal,
        likedGames: List<LikedGame>,
        userTastePrompt: String,
        groundedSearch: Boolean
    ): String {
        val cleanTitle = DealTitleNormalizer.normalize(deal.title)
        val platformLabel = deal.platform.displayName
        val tags = (deal.steamTagNames.take(6) + deal.gameCategories).distinct().joinToString(", ")

        val loved = likedGames.filter { it.isPositiveRating }.sortedByDescending { it.rating ?: 0 }.take(10)
        val disliked = likedGames.filter { it.isNegativeRating }.sortedBy { it.rating ?: 0 }.take(6)
        val owned = likedGames.filter { it.isOwned && it.rating == null }.take(8)

        fun fmt(g: LikedGame): String {
            val note = g.userNote?.trim()?.takeIf { it.isNotEmpty() }?.let { " (nota: \"$it\")" }.orEmpty()
            val ratingPrefix = g.rating?.let { "${it}★ " }.orEmpty()
            return "- $ratingPrefix${g.title}$note"
        }

        val profileSection = buildString {
            if (userTastePrompt.isNotBlank()) {
                appendLine("Preferenze dichiarate utente: ${userTastePrompt.trim()}")
            }
            if (loved.isNotEmpty()) {
                appendLine("Giochi apprezzati (4-5★):")
                loved.forEach { appendLine(fmt(it)) }
            }
            if (disliked.isNotEmpty()) {
                appendLine("Giochi non piaciuti (1-2★):")
                disliked.forEach { appendLine(fmt(it)) }
            }
            if (owned.isNotEmpty()) {
                appendLine("Altri titoli in libreria/backlog: ${owned.joinToString(", ") { it.title }}")
            }
        }.trim().ifBlank { "Nessun profilo salvato: fornisci un'analisi critica bilanciata del gioco." }

        val dataSourceInstruction = if (groundedSearch) {
            "Cerca su Google Metascore reale su Metacritic (Switch o generale) e ore reali su HowLongToBeat."
        } else {
            "Usa la tua conoscenza storica per Metascore Metacritic (intero 1..100 se noto con buon grado di certezza, altrimenti null) e durata tipica HowLongToBeat (es. \"~18 ore\", \"~30 ore\", \"~50 ore\"). Non inventare URL specifici se non li sai con certezza (usa null per metacriticUrl se incerto)."
        }

        return """
            Sei un critico videoludico esperto di Nintendo Switch e console portatili.
            Analizza il gioco: "${deal.title}" ($cleanTitle, su $platformLabel, publisher ${deal.publisher}, generi/tag: [${tags.ifBlank { "n/d" }}], prezzo attuale €${"%.2f".format(deal.discountedPriceEur)} invece di €${"%.2f".format(deal.regularPriceEur)}, -${deal.discountPercent}%).

            $dataSourceInstruction

            PROFILO GUSTI UTENTE:
            $profileSection

            Rispondi ESCLUSIVAMENTE con un singolo oggetto JSON valido, senza testo extra:
            {"metacriticScore":85,"metacriticUrl":"https://www.metacritic.com/...","hltbMainHours":"18 ore","hltbMainExtraHours":"30 ore","hltbCompletionistHours":"52 ore","verdict":"Consigliatissimo","summaryNote":"• Confronto gusti: ...\\n• Punti forti/rischi: ...\\n• Su Switch & prezzo: ..."}

            Regole campi:
            - `verdict`: UNA tra queste etichette brevi: "Imperdibile per te", "Consigliatissimo", "Buon affare", "Neutro / Misto", "Lascia perdere".
            - `summaryNote`: 3 righe concise in italiano (60-110 parole totali) separate da `\n`:
              1) `• Confronto gusti:` cita esplicitamente somiglianze o differenze con i giochi votati e le note dell'utente.
              2) `• Gameplay & rischi:` cosa funziona davvero nel loop di gioco e possibili difetti o frustrazioni.
              3) `• Su Switch & prezzo:` portabilità, resa tecnica su Switch/Switch 2 e rapporto qualità/prezzo a €${"%.2f".format(deal.discountedPriceEur)}.
        """.trimIndent()
    }

    internal fun parseGroundedPayload(rawText: String): GroundedInsightsPayload? {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objEnd = cleaned.lastIndexOf('}')
        if (objEnd <= 0) return null

        // Scan `{` positions from left to right (and right-most standalone objects) so prefixed text cannot spoil JSON slicing.
        val openPositions = cleaned.indices.filter { cleaned[it] == '{' && it < objEnd }
        for (start in openPositions.asReversed()) {
            val slice = cleaned.substring(start, objEnd + 1)
            val parsedObj = runCatching {
                json.parseToJsonElement(slice) as? JsonObject
            }.getOrNull() ?: continue
            return mapJsonObjectToPayload(parsedObj)
        }
        return null
    }

    private fun mapJsonObjectToPayload(obj: JsonObject): GroundedInsightsPayload {
        fun cleanStr(key: String): String? {
            val raw = obj[key]?.let { it as? JsonPrimitive }?.contentOrNull?.trim() ?: return null
            if (raw.isEmpty() || raw.equals("null", ignoreCase = true) || raw.equals("n/a", ignoreCase = true) || raw == "--") {
                return null
            }
            return raw
        }

        val metaRaw = obj["metacriticScore"] as? JsonPrimitive
        val metaScore = (metaRaw?.intOrNull ?: metaRaw?.contentOrNull?.trim()?.toDoubleOrNull()?.toInt())
            ?.takeIf { it in 1..100 }

        return GroundedInsightsPayload(
            metacriticScore = metaScore,
            metacriticUrl = cleanStr("metacriticUrl"),
            hltbMainHours = cleanStr("hltbMainHours"),
            hltbMainExtraHours = cleanStr("hltbMainExtraHours"),
            hltbCompletionistHours = cleanStr("hltbCompletionistHours"),
            verdict = cleanStr("verdict"),
            summaryNote = cleanStr("summaryNote")
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val LD_METASCORE_REGEX = Regex(
            "\"@type\"\\s*:\\s*\"AggregateRating\"[^}]*?\"ratingValue\"\\s*:\\s*(\\d{1,3})",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        fun computeProfileSignature(
            likedGames: List<LikedGame>,
            userTastePrompt: String
        ): String {
            val lib = likedGames
                .filter { it.rating != null || !it.userNote.isNullOrBlank() || it.isOwned }
                .sortedBy { it.id }
                .joinToString("|") {
                    "${it.id}:${it.rating ?: 0}:${if (it.isOwned) 1 else 0}:${it.userNote.orEmpty().trim()}"
                }
            val raw = "$lib#${userTastePrompt.trim()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .build()
    }
}

private enum class AiMode {
    GROUNDED_SEARCH,
    DIRECT_KNOWLEDGE,
    NONE
}

private data class GeminiInsightsResult(
    val payload: GroundedInsightsPayload?,
    val mode: AiMode,
    val errorMessage: String?
)

@Serializable
internal data class GroundedInsightsPayload(
    val metacriticScore: Int? = null,
    val metacriticUrl: String? = null,
    val hltbMainHours: String? = null,
    val hltbMainExtraHours: String? = null,
    val hltbCompletionistHours: String? = null,
    val verdict: String? = null,
    val summaryNote: String? = null
)

@Serializable
internal data class SteamAppDetailsWrapper(
    val success: Boolean? = null,
    val data: SteamAppDetailsData? = null
)

@Serializable
internal data class SteamAppDetailsData(
    val metacritic: SteamMetacriticBlock? = null
)

@Serializable
internal data class SteamMetacriticBlock(
    val score: Int? = null,
    val url: String? = null
)
