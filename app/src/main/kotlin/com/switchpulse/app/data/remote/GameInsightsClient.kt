package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GameExternalInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Fetches on-demand external critic/duration metadata (Metacritic & HowLongToBeat) for a single
 * game when the user opens its Detail Sheet.
 * - Reads Steam `appdetails` when `steamAppId` is available for official Metacritic score.
 * - Uses Gemini 4 Hydrogen with Google Search Grounding (`google_search`) when a Gemini API key
 *   is configured to look up Nintendo Switch Metacritic + HLTB hours in one grounded call.
 */
class GameInsightsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val steamAppDetailsUrl: String = "https://store.steampowered.com/api/appdetails",
    private val geminiEndpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchInsights(
        deal: Deal,
        geminiApiKey: String,
        geminiModel: String = GeminiModels.HYDROGEN
    ): GameExternalInsights = withContext(Dispatchers.IO) {
        val steamMeta = deal.steamAppId?.let { fetchSteamMetacritic(it) }
        val cleanKey = geminiApiKey.trim()

        val grounded = if (cleanKey.isNotBlank()) {
            fetchGroundedWithGemini(
                deal = deal,
                apiKey = cleanKey,
                modelId = geminiModel.trim().ifBlank { GeminiModels.HYDROGEN }
            )
        } else {
            null
        }

        val metacriticScore = grounded?.metacriticScore ?: steamMeta?.score ?: deal.metacriticScore
        val metacriticUrl = grounded?.metacriticUrl?.takeIf { it.startsWith("http") }
            ?: steamMeta?.url?.takeIf { it.startsWith("http") }

        val sourceLabel = when {
            grounded != null && steamMeta != null -> "Steam + ✨ ${geminiModel.ifBlank { GeminiModels.HYDROGEN }} (Search)"
            grounded != null -> "✨ ${geminiModel.ifBlank { GeminiModels.HYDROGEN }} (Google Search)"
            steamMeta != null -> "Steam Metacritic API"
            cleanKey.isNotBlank() -> "Lookup AI non disponibile · link manuali"
            else -> "Aggiungi una chiave Gemini per HLTB e Metacritic Switch"
        }

        GameExternalInsights(
            dealId = deal.id,
            metacriticScore = metacriticScore,
            metacriticUrl = metacriticUrl,
            hltbMainHours = grounded?.hltbMainHours,
            hltbMainExtraHours = grounded?.hltbMainExtraHours,
            hltbCompletionistHours = grounded?.hltbCompletionistHours,
            summaryNote = grounded?.summaryNote,
            sourceLabel = sourceLabel,
            fetchedAtMs = System.currentTimeMillis()
        )
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

    private fun fetchGroundedWithGemini(
        deal: Deal,
        apiKey: String,
        modelId: String
    ): GroundedInsightsPayload? = runCatching {
        val cleanTitle = DealTitleNormalizer.normalize(deal.title)
        val platformLabel = deal.platform.displayName
        val endpoint = geminiEndpointOverride?.takeIf { it.isNotBlank() }
            ?: "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"

        val prompt = """
            Cerca su Google informazioni accurate e reali per il videogioco "${deal.title}" ($cleanTitle, su $platformLabel / Switch, publisher ${deal.publisher}):
            1. Metascore su Metacritic (preferibilmente versione Switch o generale).
            2. Durata su HowLongToBeat (Main Story, Main + Extras, Completionist).
            3. Una nota sintetica in italiano (massimo 18 parole) su pregi/difetti tecnici su Switch o longevità.
            Rispondi ESCLUSIVAMENTE con un singolo oggetto JSON valido, senza testo extra:
            {"metacriticScore":85,"metacriticUrl":"https://www.metacritic.com/...","hltbMainHours":"18 ore","hltbMainExtraHours":"30 ore","hltbCompletionistHours":"52 ore","summaryNote":"..."}
            Se un dato non è verificabile, usa null per quel campo.
        """.trimIndent()

        val reqObj = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            tools = listOf(GeminiTool(emptyMap())),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1,
                thinkingConfig = GeminiModels.thinkingConfigFor(modelId)
            )
        )

        val request = Request.Builder()
            .url(endpoint)
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(reqObj).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string().orEmpty()
            val envelope = json.decodeFromString<GeminiGenerateResponse>(raw)
            val allText = envelope.candidates
                .firstOrNull()
                ?.content
                ?.parts
                .orEmpty()
                .mapNotNull { it.text }
                .joinToString("\n")
                .trim()

            parseGroundedPayload(allText)
        }
    }.getOrNull()

    internal fun parseGroundedPayload(rawText: String): GroundedInsightsPayload? {
        val cleaned = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objStart = cleaned.indexOf('{')
        val objEnd = cleaned.lastIndexOf('}')
        if (objStart < 0 || objEnd <= objStart) return null
        val jsonSlice = cleaned.substring(objStart, objEnd + 1)
        return runCatching {
            json.decodeFromString<GroundedInsightsPayload>(jsonSlice)
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(28, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
internal data class GroundedInsightsPayload(
    val metacriticScore: Int? = null,
    val metacriticUrl: String? = null,
    val hltbMainHours: String? = null,
    val hltbMainExtraHours: String? = null,
    val hltbCompletionistHours: String? = null,
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
