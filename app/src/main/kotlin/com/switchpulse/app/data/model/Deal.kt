package com.switchpulse.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    SWITCH_2,
    SWITCH_1;

    val shortLabel: String
        get() = when (this) {
            SWITCH_2 -> "SW2"
            SWITCH_1 -> "SW1"
        }

    val displayName: String
        get() = when (this) {
            SWITCH_2 -> "Switch 2"
            SWITCH_1 -> "Switch"
        }
}

@Serializable
enum class PlatformFilter {
    ALL,
    SWITCH_2,
    SWITCH_1;

    val label: String
        get() = when (this) {
            ALL -> "Tutti"
            SWITCH_2 -> "SW2"
            SWITCH_1 -> "SW1"
        }
}

@Serializable
enum class DealMatchSource {
    NONE,
    LOCAL,
    GEMINI
}

@Serializable
enum class DealMatchSentiment {
    NONE,
    RECOMMENDED,
    DISCOURAGED
}

@Serializable
enum class FeedSortMode(val label: String) {
    PERSONAL("Per te"),
    DISCOUNT("Sconto"),
    PRICE_ASC("Prezzo")
}

@Serializable
data class Deal(
    val id: String,
    val nsuid: String?,
    val title: String,
    val publisher: String,
    val platform: Platform,
    val regularPriceEur: Double,
    val discountedPriceEur: Double,
    val discountPercent: Int,
    val endsAtIso: String?,
    val imageUrl: String?,
    val eshopUrl: String,
    val popularityRank: Int,
    val startsAtIso: String? = null,
    val releasedAtIso: String? = null,
    val chartRank: Int? = null,
    val steamAppId: Int? = null,
    val steamReviewCount: Int = 0,
    val steamPositivePercent: Int? = null,
    val metacriticScore: Int? = null,
    val hotScore: Double = 0.0,
    val steamTagIds: List<Int> = emptyList(),
    val steamTagNames: List<String> = emptyList(),
    val gameCategories: List<String> = emptyList(),
    val developer: String? = null,
    val excerpt: String? = null,
    val aiAffinityScore: Double? = null,
    val aiReason: String? = null,
    val aiDetailedReason: String? = null,
    val personalScore: Double = 0.0,
    val matchReason: String? = null,
    val matchSource: DealMatchSource = DealMatchSource.NONE,
    val matchSentiment: DealMatchSentiment = DealMatchSentiment.NONE,
    val lowestSeenPriceEur: Double? = null,
    val isHistoricalLow: Boolean = false,
    val previousDiscountedPriceEur: Double? = null
) {
    val displayPublisher: String get() = publisher.ifBlank { "Nintendo eShop" }

    fun toLikedGame(
        rating: Int? = null,
        isOwned: Boolean = false
    ): LikedGame = LikedGame(
        id = id,
        title = title,
        publisher = publisher,
        developer = developer,
        imageUrl = imageUrl,
        eshopUrl = eshopUrl,
        gameCategories = gameCategories,
        steamTagIds = steamTagIds,
        steamTagNames = steamTagNames,
        excerpt = excerpt,
        rating = rating,
        isOwned = isOwned,
        addedAtMs = System.currentTimeMillis()
    )
}

/**
 * Entry in the user's personal Library & Taste Profile.
 * Supports independent 1-5 star ratings (attraction/repulsion) and "owned" status (backlog
 * excluded from deal feeds without requiring a rating).
 */
@Serializable
data class LikedGame(
    val id: String,
    val title: String,
    val publisher: String = "",
    val developer: String? = null,
    val imageUrl: String? = null,
    val eshopUrl: String? = null,
    val gameCategories: List<String> = emptyList(),
    val steamTagIds: List<Int> = emptyList(),
    val steamTagNames: List<String> = emptyList(),
    val excerpt: String? = null,
    val rating: Int? = 5,
    val isOwned: Boolean = false,
    val userNote: String? = null,
    val targetPriceEur: Double? = null,
    val addedAtMs: Long = 0L
) {
    val isPositiveRating: Boolean get() = (rating ?: 0) >= 4
    val isNegativeRating: Boolean get() = rating != null && rating <= 2
}

@Serializable
enum class MetacriticOrigin {
    NONE,
    METACRITIC,
    STEAM_PC,
    AI_ESTIMATE
}

@Serializable
data class GameExternalInsights(
    val dealId: String,
    val metacriticScore: Int? = null,
    val metacriticUrl: String? = null,
    val metacriticOrigin: MetacriticOrigin = MetacriticOrigin.NONE,
    val hltbMainHours: String? = null,
    val hltbMainExtraHours: String? = null,
    val hltbCompletionistHours: String? = null,
    val hltbIsEstimated: Boolean = false,
    val aiVerdict: String? = null,
    val summaryNote: String? = null,
    val sourceLabel: String = "",
    val profileSignature: String = "",
    val fetchedAtMs: Long = 0L
)

/**
 * Shared title normalizer for cross-edition deduplication (SW1 vs SW2) and Steam matching.
 */
object DealTitleNormalizer {
    private val TRADEMARKS = Regex("[™®©:]")
    private val SWITCH_SUFFIX = Regex("\\s*[–\\-]\\s*nintendo switch.*$", RegexOption.IGNORE_CASE)
    private val SWITCH_EDITION = Regex("\\s*nintendo switch.*edition.*$", RegexOption.IGNORE_CASE)
    private val NON_ALNUM = Regex("[^a-z0-9 ]+")
    private val MULTI_SPACE = Regex("\\s+")
    private val STEAM_EXCLUDED_WORDS = Regex("\\b(?:soundtrack|ost|artbook|demo)\\b", RegexOption.IGNORE_CASE)

    fun normalize(title: String): String {
        val cleaned = title.lowercase()
            .replace(TRADEMARKS, "")
            .replace(SWITCH_SUFFIX, "")
            .replace(SWITCH_EDITION, "")
            .replace(NON_ALNUM, " ")
            .replace(MULTI_SPACE, " ")
            .trim()
        return cleaned.ifEmpty { title.lowercase().trim() }
    }

    fun isSteamSupplementaryItem(rawName: String): Boolean =
        STEAM_EXCLUDED_WORDS.containsMatchIn(rawName)

    fun matchesSteamTitle(rawCandidateName: String, normalizedTarget: String): Boolean {
        if (rawCandidateName.isBlank() || normalizedTarget.isBlank()) return false
        if (isSteamSupplementaryItem(rawCandidateName)) return false
        val candidateNorm = normalize(rawCandidateName)
        return candidateNorm == normalizedTarget ||
            (normalizedTarget.length >= 6 &&
                (candidateNorm.startsWith("$normalizedTarget ") || normalizedTarget.startsWith("$candidateNorm ")))
    }

    fun resolveNintendoEshopUrl(rawUrl: String?): String {
        val trimmed = rawUrl.orEmpty().trim()
        return when {
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> "https://www.nintendo.com$trimmed"
            else -> "https://www.nintendo.com/it-it/"
        }
    }
}

@Serializable
data class GamerSoulProfile(
    val archetypeTitle: String = "",
    val soulSummary: String = "",
    val corePillars: List<String> = emptyList(),
    val redFlags: List<String> = emptyList(),
    val signature: String = "",
    val generatedAtMs: Long = 0L
)

@Serializable
data class AiDiscoveryPick(
    val title: String,
    val pitchHook: String = "",
    val reason: String = "",
    val estimatedPriceHint: String = "",
    val eshopId: String? = null,
    val publisher: String = "",
    val imageUrl: String? = null,
    val eshopUrl: String? = null,
    val gameCategories: List<String> = emptyList(),
    val steamTagIds: List<Int> = emptyList(),
    val steamTagNames: List<String> = emptyList(),
    val currentPriceEur: Double? = null,
    val regularPriceEur: Double? = null,
    val discountPercent: Int = 0
) {
    fun toLikedGame(rating: Int? = null, isOwned: Boolean = false): LikedGame {
        val resolvedId = eshopId?.takeIf { it.isNotBlank() }
            ?: "discovery_${DealTitleNormalizer.normalize(title).replace(' ', '_')}"
        return LikedGame(
            id = resolvedId,
            title = title,
            publisher = publisher,
            imageUrl = imageUrl,
            eshopUrl = eshopUrl,
            gameCategories = gameCategories,
            steamTagIds = steamTagIds,
            steamTagNames = steamTagNames,
            excerpt = reason.takeIf { it.isNotBlank() },
            rating = rating,
            isOwned = isOwned
        )
    }
}

@Serializable
enum class AiProvider(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val keyPlaceholder: String
) {
    GEMINI("gemini", "Google Gemini", "Gemini", "AIza..."),
    OPENROUTER("openrouter", "OpenRouter", "OpenRouter", "sk-or-v1-..."),
    DEEPSEEK("deepseek", "DeepSeek", "DeepSeek", "sk-...");

    companion object {
        fun fromId(raw: String?): AiProvider =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: GEMINI
    }
}

@Serializable
data class AiTaskTarget(
    val provider: AiProvider = AiProvider.GEMINI,
    val modelId: String = "gemini-3.5-flash-lite"
)

@Serializable
data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val description: String = "",
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val provider: AiProvider = AiProvider.GEMINI,
    val isFree: Boolean = false
)

@Serializable
enum class BackgroundRerankPolicy(val label: String) {
    WIFI_ONLY("Solo Wi-Fi"),
    ALWAYS("Sempre"),
    OFF("Mai");

    companion object {
        fun fromName(raw: String?): BackgroundRerankPolicy =
            entries.firstOrNull { it.name == raw } ?: WIFI_ONLY
    }
}

data class FilterSettings(
    val minDiscountPercent: Int = 30,
    val minRegularPriceEur: Float = 10f,
    val appPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val widgetPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val feedSortMode: FeedSortMode = FeedSortMode.PERSONAL,
    val expiringSoonOnly: Boolean = false,
    val previousFeedOrder: List<String> = emptyList(),
    val syncIntervalHours: Int = 12,
    val backgroundRerankPolicy: BackgroundRerankPolicy = BackgroundRerankPolicy.WIFI_ONLY,
    val blacklistIds: Set<String> = emptySet(),
    val blacklistTitles: Map<String, String> = emptyMap(),
    val likedGames: List<LikedGame> = emptyList(),
    val recommendationWeight: Int = 50,
    val forYouOnlyMode: Boolean = false,
    val geminiApiKey: String = "",
    val openRouterApiKey: String = "",
    val deepSeekApiKey: String = "",
    val geminiModel: String = "gemini-4-hydrogen",
    val rerankTarget: AiTaskTarget = AiTaskTarget(AiProvider.GEMINI, "gemini-3.5-flash-lite"),
    val insightsTarget: AiTaskTarget = AiTaskTarget(AiProvider.GEMINI, "gemini-3.5-flash-lite"),
    val soulTarget: AiTaskTarget = AiTaskTarget(AiProvider.GEMINI, "gemini-4-hydrogen"),
    val availableGeminiModels: List<GeminiModelInfo> = emptyList(),
    val availableOpenRouterModels: List<GeminiModelInfo> = emptyList(),
    val availableDeepSeekModels: List<GeminiModelInfo> = emptyList(),
    val geminiModelsUpdatedAtMs: Long = 0L,
    val geminiStatusSummary: String = "",
    val geminiSignature: String = "",
    val userTastePrompt: String = "",
    val gamerSoulProfile: GamerSoulProfile? = null,
    val discoveryPicks: List<AiDiscoveryPick> = emptyList(),
    val dismissedDiscoveryTitles: List<String> = emptyList(),
    val githubRepo: String = "",
    val autoCheckUpdates: Boolean = true,
    val lastSyncEpochMs: Long = 0L
) {
    fun apiKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> geminiApiKey.trim()
        AiProvider.OPENROUTER -> openRouterApiKey.trim()
        AiProvider.DEEPSEEK -> deepSeekApiKey.trim()
    }

    fun hasKeyFor(provider: AiProvider): Boolean = apiKeyFor(provider).isNotBlank()

    fun hasAnyAiKey(): Boolean = AiProvider.entries.any { hasKeyFor(it) }

    fun configuredProviders(): List<AiProvider> = AiProvider.entries.filter { hasKeyFor(it) }

    fun availableModelsFor(provider: AiProvider): List<GeminiModelInfo> = when (provider) {
        AiProvider.GEMINI -> availableGeminiModels
        AiProvider.OPENROUTER -> availableOpenRouterModels
        AiProvider.DEEPSEEK -> availableDeepSeekModels
    }

    fun hasNonDefaultFilters(): Boolean =
        minDiscountPercent != 30 ||
            minRegularPriceEur != 10f ||
            recommendationWeight != 50 ||
            forYouOnlyMode ||
            expiringSoonOnly ||
            feedSortMode != FeedSortMode.PERSONAL
}
