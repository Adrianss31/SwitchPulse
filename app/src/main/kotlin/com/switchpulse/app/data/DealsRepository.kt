package com.switchpulse.app.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.remote.GameInsightsClient
import com.switchpulse.app.data.remote.GeminiReranker
import com.switchpulse.app.data.remote.GeminiSoulClient
import com.switchpulse.app.data.remote.NintendoEshopClient
import com.switchpulse.app.widget.EditorialBentoWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DealsRepository(
    private val context: Context,
    val preferences: PreferencesStore = PreferencesStore(context),
    private val eshopClient: NintendoEshopClient = NintendoEshopClient(),
    private val geminiReranker: GeminiReranker = GeminiReranker(),
    private val insightsClient: GameInsightsClient = GameInsightsClient(),
    private val soulClient: GeminiSoulClient = GeminiSoulClient(),
    val coverCache: CoverBitmapCache = CoverBitmapCache(context)
) {
    val settingsFlow: Flow<FilterSettings> = preferences.settingsFlow
    val cachedDealsFlow: Flow<List<Deal>> = preferences.cachedDealsFlow

    val filteredAppDealsFlow: Flow<List<Deal>> = combine(
        cachedDealsFlow,
        settingsFlow
    ) { deals, settings ->
        DealFilterEngine.filterTopTen(
            rawDeals = deals,
            settings = settings,
            platformOverride = settings.appPlatformFilter,
            limit = APP_FEED_LIMIT
        )
    }

    suspend fun refreshDeals(forceAiRerank: Boolean = false): Result<List<Deal>> {
        return runCatching {
            val settings = preferences.getSettings()
            val cachedById = preferences.getCachedDeals().associateBy { it.id }
            val fetchedDeals = eshopClient.fetchPopularDeals(likedGames = settings.likedGames).map { deal ->
                // Preserve any prior AI score until new rerank completes or cache hits.
                val prev = cachedById[deal.id]
                if (prev != null && prev.aiAffinityScore != null) {
                    deal.copy(
                        aiAffinityScore = prev.aiAffinityScore,
                        aiReason = prev.aiReason,
                        aiDetailedReason = prev.aiDetailedReason
                    )
                } else {
                    deal
                }
            }

            val freshDeals = applyGeminiIfEligible(
                deals = fetchedDeals,
                settings = settings,
                force = forceAiRerank
            )

            val now = System.currentTimeMillis()
            preferences.saveCachedDeals(freshDeals, now)
            updateWidgetForSettings(freshDeals, settings)
            freshDeals
        }
    }

    suspend fun rerankCachedDealsWithGemini(force: Boolean = true): Result<String> {
        return runCatching {
            val settings = preferences.getSettings()
            val cached = preferences.getCachedDeals()
            if (cached.isEmpty()) {
                return@runCatching "Nessuna offerta in cache: ricarica prima il feed."
            }
            val cleared = cached.map {
                it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null)
            }
            val updated = applyGeminiIfEligible(
                deals = cleared,
                settings = settings,
                force = force
            )
            preferences.saveCachedDeals(updated)
            updateWidgetForSettings(updated, settings)
            preferences.getSettings().geminiStatusSummary.ifBlank { "Analisi completata" }
        }
    }

    private suspend fun applyGeminiIfEligible(
        deals: List<Deal>,
        settings: FilterSettings,
        force: Boolean
    ): List<Deal> {
        val ratedGames = settings.likedGames.filter { it.isPositiveRating || it.isNegativeRating }
        val useAi = settings.geminiApiKey.isNotBlank() &&
            ratedGames.isNotEmpty() &&
            (settings.recommendationWeight > 0 || settings.forYouOnlyMode)

        if (!useAi) {
            val msg = when {
                settings.geminiApiKey.isBlank() -> ""
                ratedGames.isEmpty() ->
                    "Chiave salvata (${settings.geminiModel}) · assegna almeno un voto 1–2★ o 4–5★ nella Libreria"
                else -> "Peso raccomandazioni a 0% · AI in pausa"
            }
            if (msg.isNotBlank() || settings.geminiStatusSummary.isNotBlank()) {
                preferences.saveGeminiRunState(msg, "")
            }
            return deals.map {
                it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null)
            }
        }

        val aiTargets = DealFilterEngine
            .selectCandidatesForAi(deals, settings, limit = GeminiReranker.MAX_CANDIDATES)
            .ifEmpty { deals.sortedByDescending { it.hotScore }.take(GeminiReranker.MAX_CANDIDATES) }

        val signature = geminiReranker.computeSignature(
            modelId = settings.geminiModel,
            likedGames = settings.likedGames,
            candidates = aiTargets,
            userTastePrompt = settings.userTastePrompt
        )
        val hasCachedAi = aiTargets.any { it.aiAffinityScore != null }
        if (!force && signature == settings.geminiSignature && hasCachedAi) {
            val aiCount = deals.count { it.aiAffinityScore != null }
            preferences.saveGeminiRunState(
                statusSummary = "✨ Cache AI attiva (${settings.geminiModel}) · $aiCount offerte già valutate",
                signature = signature
            )
            return deals
        }

        val outcome = geminiReranker.rerank(
            apiKey = settings.geminiApiKey,
            likedGames = settings.likedGames,
            candidates = aiTargets,
            modelId = settings.geminiModel,
            userTastePrompt = settings.userTastePrompt
        )
        preferences.saveGeminiRunState(
            statusSummary = outcome.statusSummary,
            signature = if (outcome.isError) "" else signature
        )

        if (outcome.scores.isEmpty()) {
            return if (outcome.isError) {
                deals
            } else {
                deals.map { it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null) }
            }
        }

        return deals.map { deal ->
            val entry = outcome.scores[deal.id]
            deal.copy(
                aiAffinityScore = entry?.score,
                aiReason = entry?.reason,
                aiDetailedReason = entry?.detailedReason
            )
        }
    }

    suspend fun testGeminiKey(): Result<String> {
        val settings = preferences.getSettings()
        return geminiReranker.testKey(
            apiKey = settings.geminiApiKey,
            modelId = settings.geminiModel
        )
    }

    suspend fun getOrFetchGameInsights(deal: Deal, forceRefresh: Boolean = false): GameExternalInsights {
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val settings = preferences.getSettings()
        val currentProfileSig = GameInsightsClient.computeProfileSignature(
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt
        )
        if (!forceRefresh) {
            val cached = preferences.getCachedInsight(deal.id)
            if (cached != null &&
                cached.profileSignature == currentProfileSig &&
                System.currentTimeMillis() - cached.fetchedAtMs < sevenDaysMs
            ) {
                return cached
            }
        }
        val fetched = insightsClient.fetchInsights(
            deal = deal,
            geminiApiKey = settings.geminiApiKey,
            geminiModel = settings.geminiModel,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt
        )
        val hasUsefulData = fetched.metacriticScore != null ||
            !fetched.hltbMainHours.isNullOrBlank() ||
            !fetched.summaryNote.isNullOrBlank() ||
            !fetched.aiVerdict.isNullOrBlank()
        if (hasUsefulData) {
            preferences.saveCachedInsight(fetched)
        }
        return fetched
    }

    suspend fun searchGamesForProfile(query: String): List<LikedGame> {
        return eshopClient.searchGamesForProfile(query)
    }

    suspend fun generateGamerSoul(): Result<GamerSoulProfile> {
        val settings = preferences.getSettings()
        val result = soulClient.generateGamerSoul(
            apiKey = settings.geminiApiKey,
            likedGames = settings.likedGames,
            modelId = settings.geminiModel,
            userTastePrompt = settings.userTastePrompt
        )
        result.onSuccess { profile ->
            preferences.saveGamerSoulProfile(profile)
        }
        return result
    }

    suspend fun discoverGamesForSoul(freshBatch: Boolean = false): Result<List<AiDiscoveryPick>> {
        val settings = preferences.getSettings()
        val expectedSig = soulClient.computeSignature(
            modelId = settings.geminiModel,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt
        )
        val soulProfile = if (settings.gamerSoulProfile != null &&
            settings.gamerSoulProfile.signature == expectedSig
        ) {
            settings.gamerSoulProfile
        } else {
            val generated = generateGamerSoul()
            generated.getOrElse { return Result.failure(it) }
        }

        val previousTitles = if (freshBatch) settings.discoveryPicks.map { it.title } else emptyList()
        val picksResult = soulClient.discoverGamesForSoul(
            apiKey = settings.geminiApiKey,
            soulProfile = soulProfile,
            likedGames = settings.likedGames,
            modelId = settings.geminiModel,
            userTastePrompt = settings.userTastePrompt,
            excludePreviousTitles = previousTitles
        )
        val rawPicks = picksResult.getOrElse { return Result.failure(it) }
        val enriched = runCatching { eshopClient.enrichDiscoveryPicks(rawPicks) }.getOrDefault(rawPicks)
        preferences.saveDiscoveryPicks(enriched)
        return Result.success(enriched)
    }

    suspend fun notifyWidgetChanged() {
        val deals = preferences.getCachedDeals()
        val settings = preferences.getSettings()
        updateWidgetForSettings(deals, settings)
    }

    private suspend fun updateWidgetForSettings(deals: List<Deal>, settings: FilterSettings) {
        val widgetTop = DealFilterEngine.filterTopTen(
            rawDeals = deals,
            settings = settings,
            platformOverride = settings.widgetPlatformFilter,
            limit = WIDGET_FEED_LIMIT
        )
        coverCache.prefetchCovers(widgetTop.map { it.id to it.imageUrl })
        EditorialBentoWidget().updateAll(context)
    }

    companion object {
        const val APP_FEED_LIMIT = 30
        const val WIDGET_FEED_LIMIT = 10
    }
}
