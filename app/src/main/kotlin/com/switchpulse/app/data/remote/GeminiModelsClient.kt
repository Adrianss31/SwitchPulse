package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.GeminiModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches available text/JSON-capable Gemini models dynamically from Google's `v1beta/models`
 * endpoint so newly released models (e.g. new Free Tier Flash/Lite/Hydrogen variants) appear
 * directly in the app without requiring an APK update.
 */
class GeminiModelsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAvailableModels(apiKey: String): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Inserisci prima una API key Gemini."))
        }

        val collected = LinkedHashMap<String, GeminiModelInfo>()
        var pageToken: String? = null
        var pages = 0

        runCatching {
            do {
                pages++
                val baseUrl = (endpointOverride?.takeIf { it.isNotBlank() }
                    ?: "https://generativelanguage.googleapis.com/v1beta/models").toHttpUrl()
                val urlBuilder = baseUrl.newBuilder()
                    .addQueryParameter("pageSize", "200")
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("x-goog-api-key", cleanKey)
                    .get()
                    .build()

                val pageBody = httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiMsg = runCatching {
                            json.decodeFromString<GeminiErrorEnvelope>(raw).error?.message
                        }.getOrNull().orEmpty().trim()
                        error("HTTP ${response.code}${if (apiMsg.isNotBlank()) ": $apiMsg" else ""}")
                    }
                    raw
                }

                val decoded = json.decodeFromString<ModelsListResponseDto>(pageBody)
                decoded.models.forEach { dto ->
                    val rawId = dto.name.removePrefix("models/").trim()
                    if (isEligibleTextModel(rawId, dto.supportedGenerationMethods)) {
                        val display = dto.displayName?.trim()?.takeIf { it.isNotBlank() } ?: rawId
                        collected[rawId] = GeminiModelInfo(
                            id = rawId,
                            displayName = display,
                            description = dto.description?.trim().orEmpty().take(180),
                            inputTokenLimit = dto.inputTokenLimit,
                            outputTokenLimit = dto.outputTokenLimit
                        )
                    }
                }
                pageToken = decoded.nextPageToken?.takeIf { it.isNotBlank() }
            } while (pageToken != null && pages < 3)

            if (collected.isEmpty()) {
                error("Nessun modello Gemini compatibile restituito dall'API")
            }

            collected.values.sortedWith(MODEL_COMPARATOR)
        }
    }

    companion object {
        private val EXCLUDED_KEYWORDS = listOf(
            "embedding",
            "imagen",
            "veo",
            "tts",
            "audio",
            "image",
            "robotics",
            "computer-use",
            "live",
            "deep-research",
            "aqa",
            "bisociate"
        )

        private val VERSION_REGEX = Regex("^gemini-(\\d+)(?:\\.(\\d+))?\\b", RegexOption.IGNORE_CASE)
        private val DATED_SUFFIX_REGEX = Regex("-(?:\\d{3}|\\d{2}-\\d{2}|exp(?:\\b|-))", RegexOption.IGNORE_CASE)

        internal fun isEligibleTextModel(id: String, methods: List<String>): Boolean {
            if (id.isBlank()) return false
            if (!methods.any { it.equals("generateContent", ignoreCase = true) }) return false
            val lower = id.lowercase()
            if (!lower.startsWith("gemini-")) return false
            if (lower.startsWith("gemini-1.")) return false
            return EXCLUDED_KEYWORDS.none { lower.contains(it) }
        }

        private val MODEL_COMPARATOR = Comparator<GeminiModelInfo> { a, b ->
            val vA = parseVersionTuple(a.id)
            val vB = parseVersionTuple(b.id)
            if (vA != vB) {
                return@Comparator vB.compareTo(vA)
            }
            val datedA = DATED_SUFFIX_REGEX.containsMatchIn(a.id)
            val datedB = DATED_SUFFIX_REGEX.containsMatchIn(b.id)
            if (datedA != datedB) {
                return@Comparator if (datedA) 1 else -1
            }
            val previewA = a.id.contains("preview", ignoreCase = true)
            val previewB = b.id.contains("preview", ignoreCase = true)
            if (previewA != previewB) {
                return@Comparator if (previewA) 1 else -1
            }
            a.id.compareTo(b.id)
        }

        private fun parseVersionTuple(id: String): Int {
            val match = VERSION_REGEX.find(id) ?: return 0
            val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            return major * 100 + minor
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class ModelsListResponseDto(
    val models: List<ModelItemDto> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
private data class ModelItemDto(
    val name: String = "",
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList()
)
