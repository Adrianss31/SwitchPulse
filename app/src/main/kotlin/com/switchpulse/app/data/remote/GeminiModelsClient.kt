package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiProvider
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
 * Fetches available text/JSON-capable AI models dynamically from Google Gemini, OpenRouter,
 * and DeepSeek endpoints so newly released Free Tier and flagship models appear in the app
 * without requiring an APK update.
 */
class GeminiModelsClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointOverride: String? = null,
    private val openRouterEndpointOverride: String? = null,
    private val deepSeekEndpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchModelsForProvider(
        provider: AiProvider,
        apiKey: String
    ): Result<List<GeminiModelInfo>> = when (provider) {
        AiProvider.GEMINI -> fetchAvailableModels(apiKey)
        AiProvider.OPENROUTER -> fetchOpenRouterModels(apiKey)
        AiProvider.DEEPSEEK -> fetchDeepSeekModels(apiKey)
    }

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
                            outputTokenLimit = dto.outputTokenLimit,
                            provider = AiProvider.GEMINI,
                            isFree = false
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

    private suspend fun fetchOpenRouterModels(apiKey: String): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        runCatching {
            val baseUrl = (openRouterEndpointOverride?.takeIf { it.isNotBlank() }
                ?: "https://openrouter.ai/api/v1/models").toHttpUrl()
            val url = baseUrl.newBuilder()
                .addQueryParameter("output_modalities", "text")
                .build()

            val builder = Request.Builder()
                .url(url)
                .header("HTTP-Referer", "https://github.com/Adrianss31/SwitchPulse")
                .header("X-Title", "SwitchPulse")
                .get()
            if (cleanKey.isNotBlank()) {
                builder.header("Authorization", "Bearer $cleanKey")
            }

            val rawBody = httpClient.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("OpenRouter HTTP ${response.code}")
                }
                raw
            }

            val decoded = json.decodeFromString<OpenRouterModelsEnvelope>(rawBody)
            val mapped = decoded.data.mapNotNull { dto ->
                val id = dto.id.trim()
                if (id.isBlank()) return@mapNotNull null
                val modalities = dto.architecture?.output_modalities.orEmpty()
                if (modalities.isNotEmpty() && modalities.none { it.equals("text", ignoreCase = true) }) {
                    return@mapNotNull null
                }
                val lower = id.lowercase()
                if (EXCLUDED_KEYWORDS.any { lower.contains(it) }) return@mapNotNull null
                val isFree = id == GeminiModels.OPENROUTER_FREE ||
                    id.endsWith(":free", ignoreCase = true) ||
                    (isZeroCost(dto.pricing?.prompt) && isZeroCost(dto.pricing?.completion))
                GeminiModelInfo(
                    id = id,
                    displayName = dto.name?.trim()?.takeIf { it.isNotBlank() } ?: id,
                    description = dto.description?.trim().orEmpty().take(180),
                    inputTokenLimit = dto.context_length ?: 0,
                    provider = AiProvider.OPENROUTER,
                    isFree = isFree
                )
            }.distinctBy { it.id }

            if (mapped.isEmpty()) {
                error("Nessun modello OpenRouter compatibile trovato")
            }

            mapped.sortedWith(
                compareByDescending<GeminiModelInfo> { it.id == GeminiModels.OPENROUTER_FREE }
                    .thenByDescending { it.isFree }
                    .thenBy { openRouterVendorRank(it.id) }
                    .thenBy { it.id }
            )
        }
    }

    private suspend fun fetchDeepSeekModels(apiKey: String): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Inserisci prima una API key DeepSeek."))
        }
        runCatching {
            val url = deepSeekEndpointOverride?.takeIf { it.isNotBlank() }
                ?: "https://api.deepseek.com/models"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            val rawBody = httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("DeepSeek HTTP ${response.code}")
                }
                raw
            }

            val decoded = json.decodeFromString<DeepSeekModelsEnvelope>(rawBody)
            val mapped = decoded.data.mapNotNull { dto ->
                val id = dto.id.trim()
                if (id.isBlank()) return@mapNotNull null
                val (display, desc) = when (id) {
                    GeminiModels.DEEPSEEK_CHAT -> "DeepSeek Chat (V3)" to "Veloce ed economico, ideale per rerank e schede gioco"
                    GeminiModels.DEEPSEEK_REASONER -> "DeepSeek Reasoner (R1)" to "Ragionamento profondo, ideale per Anima e Consigli"
                    else -> id to "Modello DeepSeek"
                }
                GeminiModelInfo(
                    id = id,
                    displayName = display,
                    description = desc,
                    provider = AiProvider.DEEPSEEK,
                    isFree = false
                )
            }.distinctBy { it.id }

            if (mapped.isEmpty()) {
                error("Nessun modello DeepSeek restituito dall'API")
            }

            mapped.sortedBy { if (it.id == GeminiModels.DEEPSEEK_CHAT) 0 else if (it.id == GeminiModels.DEEPSEEK_REASONER) 1 else 2 }
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

        private fun isZeroCost(raw: String?): Boolean {
            val d = raw?.trim()?.toDoubleOrNull() ?: return false
            return d == 0.0
        }

        private fun openRouterVendorRank(id: String): Int = when {
            id.startsWith("deepseek/") -> 0
            id.startsWith("google/") -> 1
            id.startsWith("qwen/") -> 2
            id.startsWith("meta-llama/") -> 3
            id.startsWith("anthropic/") -> 4
            id.startsWith("openai/") -> 5
            else -> 6
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

@Serializable
private data class OpenRouterModelsEnvelope(
    val data: List<OpenRouterModelDto> = emptyList()
)

@Serializable
private data class OpenRouterModelDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val context_length: Int? = null,
    val pricing: OpenRouterPricingDto? = null,
    val architecture: OpenRouterArchitectureDto? = null
)

@Serializable
private data class OpenRouterPricingDto(
    val prompt: String? = null,
    val completion: String? = null
)

@Serializable
private data class OpenRouterArchitectureDto(
    val output_modalities: List<String> = emptyList()
)

@Serializable
private data class DeepSeekModelsEnvelope(
    val data: List<DeepSeekModelDto> = emptyList()
)

@Serializable
private data class DeepSeekModelDto(
    val id: String = ""
)
