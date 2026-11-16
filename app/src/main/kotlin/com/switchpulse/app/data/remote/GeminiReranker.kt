package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.AiTaskTarget
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.GeminiModelInfo
import com.switchpulse.app.data.model.LikedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object GeminiModels {
    const val HYDROGEN = "gemini-4-hydrogen"
    const val ARGON = "gemini-4-argon"
    const val FLASH_LITE = "gemini-3.5-flash-lite"

    const val OPENROUTER_FREE = "openrouter/free"
    const val OPENROUTER_DEEPSEEK_CHAT = "deepseek/deepseek-chat"
    const val OPENROUTER_GPT4O_MINI = "openai/gpt-4o-mini"

    const val DEEPSEEK_CHAT = "deepseek-chat"
    const val DEEPSEEK_REASONER = "deepseek-reasoner"

    val OPTIONS: List<Pair<String, String>> = listOf(
        HYDROGEN to "Gemini 4 Hydrogen (potente)",
        ARGON to "Gemini 4 Argon (ragionamento)",
        FLASH_LITE to "Gemini 3.5 Flash-Lite (veloce / free tier)"
    )

    val DEFAULT_INFOS: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            id = FLASH_LITE,
            displayName = "Gemini 3.5 Flash-Lite",
            description = "Leggero e rapido, ideale per analisi brevi e free tier",
            provider = AiProvider.GEMINI
        ),
        GeminiModelInfo(
            id = HYDROGEN,
            displayName = "Gemini 4 Hydrogen",
            description = "Bilanciato ad alta qualità per profili e consigli profondi",
            provider = AiProvider.GEMINI
        ),
        GeminiModelInfo(
            id = ARGON,
            displayName = "Gemini 4 Argon",
            description = "Ragionamento esteso",
            provider = AiProvider.GEMINI
        )
    )

    val OPENROUTER_DEFAULTS: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            id = OPENROUTER_FREE,
            displayName = "OpenRouter Free Router",
            description = "Seleziona automaticamente un modello gratuito disponibile su OpenRouter",
            provider = AiProvider.OPENROUTER,
            isFree = true
        ),
        GeminiModelInfo(
            id = OPENROUTER_DEEPSEEK_CHAT,
            displayName = "DeepSeek V3 (via OpenRouter)",
            description = "Ottimo rapporto qualità/prezzo su OpenRouter",
            provider = AiProvider.OPENROUTER,
            isFree = false
        ),
        GeminiModelInfo(
            id = OPENROUTER_GPT4O_MINI,
            displayName = "GPT-4o mini (via OpenRouter)",
            description = "Veloce e compatto su OpenRouter",
            provider = AiProvider.OPENROUTER,
            isFree = false
        )
    )

    val DEEPSEEK_DEFAULTS: List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            id = DEEPSEEK_CHAT,
            displayName = "DeepSeek Chat (V3)",
            description = "Veloce ed economico, ideale per rerank e schede gioco",
            provider = AiProvider.DEEPSEEK
        ),
        GeminiModelInfo(
            id = DEEPSEEK_REASONER,
            displayName = "DeepSeek Reasoner (R1)",
            description = "Ragionamento profondo, ideale per Anima e Consigli",
            provider = AiProvider.DEEPSEEK
        )
    )

    fun defaultModelFor(provider: AiProvider, forSoul: Boolean = false): String = when (provider) {
        AiProvider.GEMINI -> if (forSoul) HYDROGEN else FLASH_LITE
        AiProvider.OPENROUTER -> OPENROUTER_FREE
        AiProvider.DEEPSEEK -> if (forSoul) DEEPSEEK_REASONER else DEEPSEEK_CHAT
    }

    fun defaultModelsFor(provider: AiProvider): List<GeminiModelInfo> = when (provider) {
        AiProvider.GEMINI -> DEFAULT_INFOS
        AiProvider.OPENROUTER -> OPENROUTER_DEFAULTS
        AiProvider.DEEPSEEK -> DEEPSEEK_DEFAULTS
    }

    fun defaultTargetFor(provider: AiProvider, forSoul: Boolean = false): AiTaskTarget =
        AiTaskTarget(provider = provider, modelId = defaultModelFor(provider, forSoul = forSoul))

    fun resolveModelsList(
        dynamicModels: List<GeminiModelInfo>,
        selectedIds: List<String> = emptyList(),
        provider: AiProvider = AiProvider.GEMINI
    ): List<GeminiModelInfo> {
        val byId = LinkedHashMap<String, GeminiModelInfo>()
        dynamicModels.forEach { info ->
            val id = info.id.trim()
            if (id.isNotBlank()) byId[id] = info.copy(id = id, provider = provider)
        }
        defaultModelsFor(provider).forEach { info ->
            byId.putIfAbsent(info.id, info)
        }
        selectedIds.forEach { rawId ->
            val id = rawId.trim()
            if (id.isNotBlank() && !byId.containsKey(id)) {
                byId[id] = GeminiModelInfo(id = id, displayName = id, provider = provider)
            }
        }
        return byId.values.toList()
    }

    private val MAJOR_VERSION_REGEX = Regex("^gemini-(\\d+)(?:\\.(\\d+))?\\b", RegexOption.IGNORE_CASE)

    fun thinkingConfigFor(modelId: String): GeminiThinkingConfig? {
        val match = MAJOR_VERSION_REGEX.find(modelId.trim()) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return if (major >= 3) {
            GeminiThinkingConfig(thinkingLevel = "low")
        } else {
            null
        }
    }
}

data class SemanticRerankEntry(
    val score: Double,
    val reason: String?,
    val detailedReason: String? = null
)

data class GeminiRerankOutcome(
    val scores: Map<String, SemanticRerankEntry>,
    val statusSummary: String,
    val isError: Boolean = false
)

/**
 * Semantic reranker supporting Google Gemini, OpenRouter and DeepSeek (BYOK).
 * Evaluates both positive (4-5★) and negative (1-2★) tastes in a single compact batch,
 * and surfaces explicit diagnostic feedback so the user can see when & how AI acted.
 */
class GeminiReranker(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val endpointBaseUrl: String? = null,
    private val openAiEndpointOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val aiClient = AiCompletionClient(
        httpClient = httpClient,
        geminiEndpointOverride = endpointBaseUrl,
        openRouterEndpointOverride = openAiEndpointOverride,
        deepSeekEndpointOverride = openAiEndpointOverride
    )

    fun computeSignature(
        modelId: String,
        likedGames: List<LikedGame>,
        candidates: List<Deal>,
        userTastePrompt: String = "",
        provider: AiProvider = AiProvider.GEMINI
    ): String {
        val librarySig = likedGames
            .filter { it.rating != null || !it.userNote.isNullOrBlank() || it.isOwned }
            .sortedBy { it.id }
            .joinToString("|") {
                "${it.id}:${it.rating ?: 0}:${if (it.isOwned) 1 else 0}:${it.userNote.orEmpty().trim()}"
            }
        val candSig = candidates
            .take(MAX_CANDIDATES)
            .joinToString(",") { it.id }
        val promptSig = userTastePrompt.trim()
        val raw = "${provider.id}:$modelId#$librarySig#$promptSig#$candSig"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun rerank(
        apiKey: String,
        likedGames: List<LikedGame>,
        candidates: List<Deal>,
        modelId: String = GeminiModels.FLASH_LITE,
        userTastePrompt: String = "",
        provider: AiProvider = AiProvider.GEMINI
    ): GeminiRerankOutcome = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider) }
        val rated = likedGames.filter { it.isPositiveRating || it.isNegativeRating }
        val cleanTastePrompt = userTastePrompt.trim()

        if (cleanKey.isBlank()) {
            return@withContext GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "Nessuna API key configurata per ${provider.displayName} · attivo solo motore locale",
                isError = false
            )
        }
        if (rated.isEmpty() && cleanTastePrompt.isBlank()) {
            return@withContext GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "Chiave salvata (${provider.shortLabel} · $effectiveModel) · vota un gioco o scrivi le tue preferenze",
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

        val topCandidates = candidates.take(MAX_CANDIDATES)
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date())
        val combinedScores = LinkedHashMap<String, SemanticRerankEntry>()
        var lastError: String? = null

        val batches = topCandidates.chunked(BATCH_SIZE)
        for (batch in batches) {
            val prompt = buildPrompt(
                likedGames = likedGames,
                candidates = batch,
                userTastePrompt = cleanTastePrompt
            )
            when (
                val result = aiClient.generate(
                    provider = provider,
                    apiKey = cleanKey,
                    modelId = effectiveModel,
                    prompt = prompt,
                    temperature = 0.25,
                    maxOutputTokens = 6144,
                    expectJsonObject = false,
                    enableGeminiGoogleSearch = false
                )
            ) {
                is AiCallResult.Failure -> {
                    lastError = result.message
                }
                is AiCallResult.Success -> {
                    val parsed = parseModelScores(result.text)
                    if (parsed.isEmpty()) {
                        lastError = "JSON non leggibile"
                    } else {
                        combinedScores.putAll(parsed)
                    }
                }
            }
        }

        // Single lightweight recovery pass if the model omitted any visible Top-30 item(s).
        val missingPrimary = topCandidates.take(30).filter { it.id !in combinedScores }
        if (lastError == null && combinedScores.isNotEmpty() && missingPrimary.isNotEmpty()) {
            val recoveryPrompt = buildPrompt(
                likedGames = likedGames,
                candidates = missingPrimary.take(BATCH_SIZE),
                userTastePrompt = cleanTastePrompt
            )
            when (
                val recovery = aiClient.generate(
                    provider = provider,
                    apiKey = cleanKey,
                    modelId = effectiveModel,
                    prompt = recoveryPrompt,
                    temperature = 0.2,
                    maxOutputTokens = 4096,
                    expectJsonObject = false,
                    enableGeminiGoogleSearch = false
                )
            ) {
                is AiCallResult.Success -> {
                    combinedScores.putAll(parseModelScores(recovery.text))
                }
                is AiCallResult.Failure -> Unit
            }
        }

        if (combinedScores.isEmpty()) {
            val errDetail = lastError ?: "nessun punteggio JSON leggibile"
            GeminiRerankOutcome(
                scores = emptyMap(),
                statusSummary = "⚠️ Errore ${provider.shortLabel} ($effectiveModel) $errDetail ($timeStr)",
                isError = true
            )
        } else {
            val evaluatedCount = topCandidates.count { it.id in combinedScores }
            val posCount = combinedScores.values.count { it.score >= 60.0 }
            val negCount = combinedScores.values.count { it.score <= 38.0 }
            val partialSuffix = if (lastError != null) " · parziale ($lastError)" else ""
            GeminiRerankOutcome(
                scores = combinedScores,
                statusSummary = "✨ ${provider.shortLabel} ($effectiveModel) attivo ($timeStr) · $evaluatedCount/${topCandidates.size} offerte analizzate: $posCount consigliate, $negCount sconsigliate$partialSuffix",
                isError = false
            )
        }
    }

    suspend fun testKey(
        apiKey: String,
        modelId: String = GeminiModels.FLASH_LITE,
        provider: AiProvider = AiProvider.GEMINI
    ): Result<String> = aiClient.testConnection(
        provider = provider,
        apiKey = apiKey,
        modelId = modelId
    )

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
                val shortBadge = (item.badge ?: item.reason)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(64)
                val detailed = (item.analysis ?: item.reason)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(420)
                item.id to SemanticRerankEntry(
                    score = item.score.coerceIn(0.0, 100.0),
                    reason = shortBadge,
                    detailedReason = detailed
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun extractErrorMessage(rawBody: String): String {
        val decoded = runCatching {
            json.decodeFromString<GeminiErrorEnvelope>(rawBody).error?.message
        }.getOrNull()
        return decoded?.take(300).orEmpty()
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun formatLibraryEntry(game: LikedGame): String {
        val tags = (game.steamTagNames.take(5) + game.gameCategories).distinct().joinToString(", ")
        val ratingLabel = game.rating?.let { "${it}★" } ?: "Posseduto"
        val ownedSuffix = if (game.isOwned && game.rating != null) " · in libreria" else ""
        val noteSuffix = game.userNote?.trim()?.takeIf { it.isNotEmpty() }?.let { " — Nota utente: \"$it\"" }.orEmpty()
        return "- [$ratingLabel$ownedSuffix] ${game.title} [${tags.ifBlank { game.publisher }}]$noteSuffix"
    }

    private fun buildPrompt(
        likedGames: List<LikedGame>,
        candidates: List<Deal>,
        userTastePrompt: String
    ): String {
        val positives = likedGames.filter { it.isPositiveRating }.sortedByDescending { it.rating ?: 0 }.take(15)
        val negatives = likedGames.filter { it.isNegativeRating }.sortedBy { it.rating ?: 0 }.take(10)
        val neutralsAndBacklog = likedGames
            .filter { !it.isPositiveRating && !it.isNegativeRating && (it.rating == 3 || it.isOwned || !it.userNote.isNullOrBlank()) }
            .take(15)

        val posText = if (positives.isEmpty()) "(nessuno)" else positives.joinToString("\n", transform = ::formatLibraryEntry)
        val negText = if (negatives.isEmpty()) "(nessuno)" else negatives.joinToString("\n", transform = ::formatLibraryEntry)
        val backlogText = if (neutralsAndBacklog.isEmpty()) "(nessuno)" else neutralsAndBacklog.joinToString("\n", transform = ::formatLibraryEntry)
        val tasteSection = if (userTastePrompt.isBlank()) {
            "(nessuna preferenza testuale extra)"
        } else {
            userTastePrompt
        }

        val candidatesText = candidates.joinToString("\n") { deal ->
            val tags = (deal.steamTagNames.take(5) + deal.gameCategories).distinct().joinToString(", ")
            val acclaim = if (deal.steamReviewCount > 0 && deal.steamPositivePercent != null) {
                " | Steam ${deal.steamPositivePercent}% pos (${deal.steamReviewCount} rec)"
            } else {
                ""
            }
            val studio = listOfNotNull(
                deal.developer?.takeIf { it.isNotBlank() },
                deal.publisher.takeIf { it.isNotBlank() }
            ).distinct().joinToString(" / ")
            val blurb = deal.excerpt?.take(120)?.replace("\n", " ").orEmpty()
            "- id=${deal.id} | ${deal.title} ($studio) | [$tags]$acclaim | -${deal.discountPercent}% | $blurb"
        }

        return """
            Sei un critico e curatore esperto di videogiochi Nintendo Switch. Valuta OGNI SINGOLO gioco in `OFFERTE CANDIDATE` confrontando gameplay loop, ritmo, profondità tattica, accessibilità e atmosfera rispetto ai gusti dell'utente, alle sue note personali sui voti e al suo backlog.
            Scala score 0..100:
            - 65..100: molto affine ai titoli 4-5★ e alle preferenze dichiarate
            - 40..64: neutro, misto o interessante ma con compromessi
            - 0..39: distante dai gusti o troppo vicino ai titoli 1-2★ sconsigliati (dai attenzione alle motivazioni nelle note utente)

            REGOLA FONDAMENTALE: restituisci un oggetto JSON per TUTTI e ${candidates.size} i titoli elencati (inclusi quelli neutri 40..64, NON omettere alcun `id`).
            Restituisci SOLO un array JSON valido con questo schema esatto:
            [{"id":"<id>","score":<0..100>,"badge":"<3-6 parole sintetiche per lista>","analysis":"<2 frasi specifiche in italiano (25-45 parole): spiega le meccaniche reali del titolo, in cosa ricorda o differisce dai giochi/note dell'utente e a chi/perché conviene su Switch>"}]

            PREFERENZE TESTUALI UTENTE:
            $tasteSection

            GIOCHI APPREZZATI (4-5★):
            $posText

            GIOCHI SCONSIGLIATI / NON PIACIUTI (1-2★):
            $negText

            ALTRI GIOCHI IN LIBRERIA / BACKLOG / 3★ (evita ridondanze o ripetitività rispetto a ciò che ha già):
            $backlogText

            OFFERTE CANDIDATE (${candidates.size} titoli):
            $candidatesText
        """.trimIndent()
    }

    companion object {
        const val MAX_CANDIDATES = 40
        const val BATCH_SIZE = 20

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
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
    val text: String? = null,
    val thought: Boolean? = null
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
    val content: GeminiResponseContent? = null,
    val finishReason: String? = null
)

@Serializable
internal data class GeminiResponseContent(
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
internal data class GeminiScoreItem(
    val id: String,
    val score: Double = 0.0,
    val badge: String? = null,
    val analysis: String? = null,
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
