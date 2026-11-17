package com.switchpulse.app.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.switchpulse.app.data.local.CachedSteamLookup
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.GeminiModelInfo
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.remote.GameInsightsClient
import com.switchpulse.app.data.remote.GeminiModels
import com.switchpulse.app.data.remote.GeminiModelsClient
import com.switchpulse.app.data.remote.GeminiReranker
import com.switchpulse.app.data.remote.GeminiSoulClient
import com.switchpulse.app.data.remote.GroundingBlockStore
import com.switchpulse.app.data.remote.NintendoEshopClient
import com.switchpulse.app.data.remote.SemanticRerankEntry
import com.switchpulse.app.data.remote.SteamLookupCache
import com.switchpulse.app.widget.CarouselWidget
import com.switchpulse.app.widget.TopCoverWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DealsRepository(
    private val context: Context,
    val sharedHttpClient: OkHttpClient = defaultSharedHttpClient(),
    val preferences: PreferencesStore = PreferencesStore(context),
    private val eshopClient: NintendoEshopClient = NintendoEshopClient(
        httpClient = sharedHttpClient,
        steamLookupCache = object : SteamLookupCache {
            override suspend fun getCached(normTitles: List<String>, nowMs: Long): Map<String, CachedSteamLookup> =
                preferences.database.getSteamLookups(normTitles, nowMs)

            override suspend fun save(entries: List<CachedSteamLookup>) =
                preferences.database.saveSteamLookups(entries)
        }
    ),
    private val geminiReranker: GeminiReranker = GeminiReranker(httpClient = sharedHttpClient),
    private val insightsClient: GameInsightsClient = GameInsightsClient(
        httpClient = sharedHttpClient,
        groundingBlockStore = object : GroundingBlockStore {
            override suspend fun isBlocked(keyFingerprint: String): Boolean =
                preferences.isGroundingBlocked(keyFingerprint)

            override suspend fun markBlocked(keyFingerprint: String, blockedUntilMs: Long) =
                preferences.markGroundingBlocked(keyFingerprint, blockedUntilMs)
        }
    ),
    private val soulClient: GeminiSoulClient = GeminiSoulClient(httpClient = sharedHttpClient),
    private val modelsClient: GeminiModelsClient = GeminiModelsClient(httpClient = sharedHttpClient),
    val coverCache: CoverBitmapCache = CoverBitmapCache(context, httpClient = sharedHttpClient)
) {
    val settingsFlow: Flow<FilterSettings> = preferences.settingsFlow
    val cachedDealsFlow: Flow<List<Deal>> = preferences.cachedDealsFlow
    private val _skippedAiKeysFlow = MutableStateFlow<Set<String>>(emptySet())

    /**
     * IDs omitted by the AI provider during the current profile session after focused retry,
     * preventing endless "N deals still need an AI score" banners on stubborn model omissions.
     */
    val skippedAiDealIdsFlow: Flow<Set<String>> = combine(
        _skippedAiKeysFlow,
        settingsFlow
    ) { skippedKeys, settings ->
        val profileSig = GeminiReranker.computeProfileSignature(
            modelId = settings.rerankTarget.modelId,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            provider = settings.rerankTarget.provider
        )
        val prefix = "$profileSig:"
        skippedKeys.mapNotNull { key ->
            if (key.startsWith(prefix)) key.removePrefix(prefix) else null
        }.toSet()
    }.distinctUntilChanged()

    private fun markAiSkipped(profileSig: String, dealIds: Collection<String>) {
        if (dealIds.isEmpty()) return
        _skippedAiKeysFlow.update { current ->
            current + dealIds.map { "$profileSig:$it" }
        }
    }

    val allEligibleDealsFlow: Flow<List<Deal>> = combine(
        cachedDealsFlow,
        settingsFlow
    ) { deals, settings ->
        DealFilterEngine.filterAllEligible(
            rawDeals = deals,
            settings = settings,
            platformOverride = settings.appPlatformFilter
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val filteredAppDealsFlow: Flow<List<Deal>> = allEligibleDealsFlow
        .combine(settingsFlow) { allEligible, _ ->
            allEligible.take(APP_FEED_LIMIT)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    suspend fun refreshDeals(
        forceAiRerank: Boolean = false,
        allowBackgroundAi: Boolean = true
    ): Result<List<Deal>> {
        return runCatching {
            val settings = preferences.getSettings()
            val cachedBefore = preferences.getCachedDeals()
            if (cachedBefore.isNotEmpty()) {
                val previousVisibleIds = DealFilterEngine
                    .filterAllEligible(cachedBefore, settings, settings.appPlatformFilter)
                    .take(APP_FEED_LIMIT)
                    .map { it.id }
                preferences.savePreviousFeedOrder(previousVisibleIds)
            }
            val prevPriceById = cachedBefore.associate { it.id to it.discountedPriceEur }

            val fetchedDeals = eshopClient.fetchPopularDeals(likedGames = settings.likedGames)
            val withPrevPrices = fetchedDeals.map { deal ->
                val prevPrice = prevPriceById[deal.id]
                    ?.takeIf { kotlin.math.abs(it - deal.discountedPriceEur) >= 0.01 }
                if (prevPrice != null) deal.copy(previousDiscountedPriceEur = prevPrice) else deal
            }
            val annotatedDeals = preferences.database.recordAndAnnotatePriceHistory(withPrevPrices)

            if (forceAiRerank) {
                _skippedAiKeysFlow.value = emptySet()
            }
            val initialAiDeals = applyAiWithPerDealCache(
                deals = annotatedDeals,
                settings = settings,
                force = forceAiRerank,
                allowAiCall = allowBackgroundAi
            )
            val freshDeals = if (allowBackgroundAi) {
                completeVisibleTopDealsAi(initialAiDeals, settings, maxPasses = 2)
            } else {
                initialAiDeals
            }

            val now = System.currentTimeMillis()
            preferences.saveCachedDeals(freshDeals, now)
            updateWidgetForSettings(freshDeals, settings, resetCarouselRank = true)
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
            if (force) {
                _skippedAiKeysFlow.value = emptySet()
            }
            val cleared = if (force) {
                cached.map { it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null) }
            } else {
                cached
            }
            val updated = applyAiWithPerDealCache(
                deals = cleared,
                settings = settings,
                force = force,
                allowAiCall = true
            )
            val converged = completeVisibleTopDealsAi(updated, settings, maxPasses = 2)
            preferences.saveCachedDeals(converged)
            updateWidgetForSettings(converged, settings)
            preferences.getSettings().geminiStatusSummary.ifBlank { "Analisi completata" }
        }
    }

    private suspend fun applyAiWithPerDealCache(
        deals: List<Deal>,
        settings: FilterSettings,
        force: Boolean,
        allowAiCall: Boolean
    ): List<Deal> {
        val ratedGames = settings.likedGames.filter { it.isPositiveRating || it.isNegativeRating }
        val hasTasteSignals = ratedGames.isNotEmpty() || settings.userTastePrompt.isNotBlank()
        val rerankTask = settings.rerankTarget
        val rerankKey = settings.apiKeyFor(rerankTask.provider)
        val useAi = rerankKey.isNotBlank() &&
            hasTasteSignals &&
            (settings.recommendationWeight > 0 || settings.forYouOnlyMode)

        if (!useAi) {
            val msg = when {
                !settings.hasAnyAiKey() -> ""
                rerankKey.isBlank() ->
                    "Chiave mancante per ${rerankTask.provider.displayName} (task Classifica) · aggiungila in Impostazioni"
                !hasTasteSignals ->
                    "Chiave salvata (${rerankTask.provider.shortLabel} · ${rerankTask.modelId}) · vota un titolo o scrivi le preferenze"
                else -> "Peso raccomandazioni a 0% · AI in pausa"
            }
            if (msg.isNotBlank() || settings.geminiStatusSummary.isNotBlank()) {
                preferences.saveGeminiRunState(msg, "")
            }
            return deals.map {
                it.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null)
            }
        }

        val profileSig = geminiReranker.computeProfileSignature(
            modelId = rerankTask.modelId,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            provider = rerankTask.provider
        )

        val cachedByNormTitle: MutableMap<String, SemanticRerankEntry> = if (force) {
            LinkedHashMap()
        } else {
            preferences.database.getAiScoresForProfile(profileSig).toMutableMap()
        }

        // Attach already-cached per-deal scores matching this taste profile signature first.
        val preAttached = deals.map { deal ->
            val cachedEntry = cachedByNormTitle[DealTitleNormalizer.normalize(deal.title)]
            if (cachedEntry != null) {
                deal.copy(
                    aiAffinityScore = cachedEntry.score,
                    aiReason = cachedEntry.reason,
                    aiDetailedReason = cachedEntry.detailedReason
                )
            } else {
                deal.copy(aiAffinityScore = null, aiReason = null, aiDetailedReason = null)
            }
        }

        val aiTargets = DealFilterEngine
            .selectCandidatesForAi(preAttached, settings, limit = GeminiReranker.MAX_CANDIDATES)
            .ifEmpty { preAttached.sortedByDescending { it.hotScore }.take(GeminiReranker.MAX_CANDIDATES) }

        val targetsNeedingAi = if (force) {
            aiTargets
        } else {
            aiTargets.filter { DealTitleNormalizer.normalize(it.title) !in cachedByNormTitle }
        }

        if (targetsNeedingAi.isEmpty()) {
            val evaluatedCount = aiTargets.count { DealTitleNormalizer.normalize(it.title) in cachedByNormTitle }
            preferences.saveGeminiRunState(
                statusSummary = "✨ Cache AI attiva (${rerankTask.provider.shortLabel} · ${rerankTask.modelId}) · $evaluatedCount/${aiTargets.size} già valutate",
                signature = profileSig
            )
            return preAttached
        }

        if (!allowAiCall) {
            val cachedCount = aiTargets.count { DealTitleNormalizer.normalize(it.title) in cachedByNormTitle }
            preferences.saveGeminiRunState(
                statusSummary = "✨ Cache parziale ($cachedCount/${aiTargets.size}) · rerank automatico in pausa su rete attuale",
                signature = profileSig
            )
            return preAttached
        }

        val outcome = geminiReranker.rerank(
            apiKey = rerankKey,
            likedGames = settings.likedGames,
            candidates = targetsNeedingAi,
            modelId = rerankTask.modelId,
            userTastePrompt = settings.userTastePrompt,
            provider = rerankTask.provider
        )

        if (outcome.scores.isNotEmpty()) {
            val newlyScoredByNorm = LinkedHashMap<String, SemanticRerankEntry>()
            targetsNeedingAi.forEach { target ->
                val entry = outcome.scores[target.id] ?: return@forEach
                val norm = DealTitleNormalizer.normalize(target.title)
                newlyScoredByNorm.putIfAbsent(norm, entry)
                cachedByNormTitle.putIfAbsent(norm, entry)
            }
            preferences.database.saveAiScoresForProfile(profileSig, newlyScoredByNorm)
        }

        val finalEvaluated = aiTargets.count { DealTitleNormalizer.normalize(it.title) in cachedByNormTitle }
        val finalStatus = if (outcome.isError && cachedByNormTitle.isNotEmpty()) {
            "${outcome.statusSummary} · $finalEvaluated/${aiTargets.size} da cache"
        } else if (!outcome.isError && targetsNeedingAi.size < aiTargets.size) {
            "✨ ${rerankTask.provider.shortLabel} (${rerankTask.modelId}) · $finalEvaluated/${aiTargets.size} (+${outcome.scores.size} nuove)"
        } else {
            outcome.statusSummary
        }

        preferences.saveGeminiRunState(
            statusSummary = finalStatus,
            signature = if (outcome.isError && cachedByNormTitle.isEmpty()) "" else profileSig
        )

        return preAttached.map { deal ->
            val entry = outcome.scores[deal.id]
                ?: cachedByNormTitle[DealTitleNormalizer.normalize(deal.title)]
            if (entry != null) {
                deal.copy(
                    aiAffinityScore = entry.score,
                    aiReason = entry.reason,
                    aiDetailedReason = entry.detailedReason
                )
            } else {
                deal
            }
        }
    }

    suspend fun rerankMissingVisibleDeals(visibleDeals: List<Deal>): Result<String> {
        return runCatching {
            val settings = preferences.getSettings()
            val rerankTask = settings.rerankTarget
            val rerankKey = settings.apiKeyFor(rerankTask.provider)
            if (rerankKey.isBlank()) {
                error("Inserisci prima una API key per ${rerankTask.provider.displayName} in Impostazioni.")
            }
            val missing = visibleDeals
                .filter { it.aiAffinityScore == null }
                .take(GeminiReranker.MAX_CANDIDATES)
            if (missing.isEmpty()) {
                return@runCatching "Tutte le offerte visibili sono già valutate"
            }

            val profileSig = geminiReranker.computeProfileSignature(
                modelId = rerankTask.modelId,
                likedGames = settings.likedGames,
                userTastePrompt = settings.userTastePrompt,
                provider = rerankTask.provider
            )

            val outcome = geminiReranker.rerank(
                apiKey = rerankKey,
                likedGames = settings.likedGames,
                candidates = missing,
                modelId = rerankTask.modelId,
                userTastePrompt = settings.userTastePrompt,
                provider = rerankTask.provider
            )
            if (outcome.scores.isEmpty()) {
                if (!outcome.isError) {
                    markAiSkipped(profileSig, missing.map { it.id })
                }
                preferences.saveGeminiRunState(outcome.statusSummary, "")
                return@runCatching outcome.statusSummary
            }

            val byNorm = LinkedHashMap<String, SemanticRerankEntry>()
            val omittedFromBatch = ArrayList<String>()
            missing.forEach { target ->
                val entry = outcome.scores[target.id]
                if (entry != null) {
                    byNorm.putIfAbsent(DealTitleNormalizer.normalize(target.title), entry)
                } else {
                    omittedFromBatch += target.id
                }
            }
            if (!outcome.isError && omittedFromBatch.isNotEmpty()) {
                markAiSkipped(profileSig, omittedFromBatch)
            }
            preferences.database.saveAiScoresForProfile(profileSig, byNorm)

            val firstPassDeals = preferences.getCachedDeals().map { deal ->
                val newlyEvaluated = outcome.scores[deal.id]
                    ?: byNorm[DealTitleNormalizer.normalize(deal.title)]
                if (newlyEvaluated != null) {
                    deal.copy(
                        aiAffinityScore = newlyEvaluated.score,
                        aiReason = newlyEvaluated.reason,
                        aiDetailedReason = newlyEvaluated.detailedReason
                    )
                } else {
                    deal
                }
            }
            val convergedDeals = completeVisibleTopDealsAi(
                deals = firstPassDeals,
                settings = settings,
                initialAttemptedIds = missing.map { it.id }.toSet(),
                maxPasses = 2
            )
            preferences.saveCachedDeals(convergedDeals)
            val totalEvaluated = convergedDeals.count { it.aiAffinityScore != null }
            val addedCount = convergedDeals.count { it.aiAffinityScore != null } -
                firstPassDeals.count { it.aiAffinityScore != null } + outcome.scores.size
            val msg = "✨ ${rerankTask.provider.shortLabel} (${rerankTask.modelId}) · +${addedCount} completate ($totalEvaluated totali valutate)"
            preferences.saveGeminiRunState(msg, profileSig)
            updateWidgetForSettings(convergedDeals, settings)
            msg
        }
    }

    /**
     * After initial AI scoring shifts discouraged items out of the Top 30, newly promoted deals
     * from ranks 41+ may lack AI scores. Performs up to [maxPasses] targeted follow-up batches so
     * every visible deal settles with either a real AI score or a session skip mark.
     */
    private suspend fun completeVisibleTopDealsAi(
        deals: List<Deal>,
        settings: FilterSettings,
        initialAttemptedIds: Set<String> = emptySet(),
        maxPasses: Int = 2
    ): List<Deal> {
        val rerankTask = settings.rerankTarget
        val rerankKey = settings.apiKeyFor(rerankTask.provider)
        val ratedGames = settings.likedGames.filter { it.isPositiveRating || it.isNegativeRating }
        val hasTasteSignals = ratedGames.isNotEmpty() || settings.userTastePrompt.isNotBlank()
        if (rerankKey.isBlank() || !hasTasteSignals) return deals

        val profileSig = GeminiReranker.computeProfileSignature(
            modelId = rerankTask.modelId,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            provider = rerankTask.provider
        )
        var workingDeals = deals
        val attemptedIds = HashSet(initialAttemptedIds)

        repeat(maxPasses) {
            val missingVisible = DealFilterEngine
                .filterAllEligible(workingDeals, settings, settings.appPlatformFilter)
                .take(APP_FEED_LIMIT)
                .filter { it.aiAffinityScore == null && it.id !in attemptedIds }
                .take(GeminiReranker.BATCH_SIZE)
            if (missingVisible.isEmpty()) return workingDeals

            missingVisible.forEach { attemptedIds += it.id }

            val outcome = geminiReranker.rerank(
                apiKey = rerankKey,
                likedGames = settings.likedGames,
                candidates = missingVisible,
                modelId = rerankTask.modelId,
                userTastePrompt = settings.userTastePrompt,
                provider = rerankTask.provider
            )
            if (outcome.scores.isEmpty()) {
                if (!outcome.isError) {
                    markAiSkipped(profileSig, missingVisible.map { it.id })
                }
                return workingDeals
            }

            val byNorm = LinkedHashMap<String, SemanticRerankEntry>()
            val omitted = ArrayList<String>()
            missingVisible.forEach { target ->
                val entry = outcome.scores[target.id]
                if (entry != null) {
                    byNorm.putIfAbsent(DealTitleNormalizer.normalize(target.title), entry)
                } else {
                    omitted += target.id
                }
            }
            if (!outcome.isError && omitted.isNotEmpty()) {
                markAiSkipped(profileSig, omitted)
            }
            preferences.database.saveAiScoresForProfile(profileSig, byNorm)

            workingDeals = workingDeals.map { deal ->
                val entry = outcome.scores[deal.id]
                    ?: byNorm[DealTitleNormalizer.normalize(deal.title)]
                if (entry != null) {
                    deal.copy(
                        aiAffinityScore = entry.score,
                        aiReason = entry.reason,
                        aiDetailedReason = entry.detailedReason
                    )
                } else {
                    deal
                }
            }
        }
        return workingDeals
    }

    fun findWishlistPriceAlerts(deals: List<Deal>, settings: FilterSettings): List<Pair<LikedGame, Deal>> {
        val watchList = settings.likedGames.filter { !it.isOwned && it.targetPriceEur != null }
        if (watchList.isEmpty() || deals.isEmpty()) return emptyList()

        val dealById = deals.associateBy { it.id }
        val dealByNorm = deals
            .sortedBy { it.discountedPriceEur }
            .associateBy { DealTitleNormalizer.normalize(it.title) }

        return watchList.mapNotNull { watched ->
            val threshold = watched.targetPriceEur ?: return@mapNotNull null
            val matchingDeal = dealById[watched.id]
                ?: dealByNorm[DealTitleNormalizer.normalize(watched.title)]
                ?: return@mapNotNull null
            if (matchingDeal.discountedPriceEur <= threshold + 0.01) {
                watched to matchingDeal
            } else {
                null
            }
        }
    }

    suspend fun testGeminiKey(): Result<String> {
        val settings = preferences.getSettings()
        return testProviderKey(settings.rerankTarget.provider)
    }

    suspend fun testProviderKey(provider: AiProvider): Result<String> {
        val settings = preferences.getSettings()
        val key = settings.apiKeyFor(provider)
        val candidateModel = listOf(settings.rerankTarget, settings.insightsTarget, settings.soulTarget)
            .firstOrNull { it.provider == provider }
            ?.modelId
            ?.takeIf { it.isNotBlank() }
            ?: GeminiModels.defaultModelFor(provider)
        val testRes = geminiReranker.testKey(
            apiKey = key,
            modelId = candidateModel,
            provider = provider
        )
        if (testRes.isSuccess) {
            refreshModelsForProvider(provider)
        }
        return testRes
    }

    suspend fun refreshModelsForProvider(provider: AiProvider): Result<List<GeminiModelInfo>> {
        val settings = preferences.getSettings()
        val result = modelsClient.fetchModelsForProvider(provider, settings.apiKeyFor(provider))
        result.onSuccess { models ->
            preferences.saveAvailableModelsForProvider(provider, models)
        }
        return result
    }

    suspend fun refreshAllAvailableModels(): Result<Int> {
        val settings = preferences.getSettings()
        val providersToFetch = (settings.configuredProviders() + AiProvider.OPENROUTER).distinct()
        var total = 0
        var anySuccess = false
        var lastError: Throwable? = null
        for (provider in providersToFetch) {
            val res = modelsClient.fetchModelsForProvider(provider, settings.apiKeyFor(provider))
            res.onSuccess { list ->
                preferences.saveAvailableModelsForProvider(provider, list)
                total += list.size
                anySuccess = true
            }.onFailure { err ->
                if (settings.hasKeyFor(provider)) {
                    lastError = err
                }
            }
        }
        return if (anySuccess) {
            Result.success(total)
        } else {
            Result.failure(lastError ?: IllegalStateException("Nessun modello caricato"))
        }
    }

    suspend fun getOrFetchGameInsights(deal: Deal, forceRefresh: Boolean = false): GameExternalInsights {
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val settings = preferences.getSettings()
        val insightsTask = settings.insightsTarget
        val currentProfileSig = GameInsightsClient.computeProfileSignature(
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            provider = insightsTask.provider,
            modelId = insightsTask.modelId
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
            geminiApiKey = settings.apiKeyFor(insightsTask.provider),
            geminiModel = insightsTask.modelId,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            provider = insightsTask.provider
        )
        val hasUsefulData = fetched.metacriticScore != null ||
            !fetched.hltbMainHours.isNullOrBlank() ||
            !fetched.summaryNote.isNullOrBlank() ||
            !fetched.aiVerdict.isNullOrBlank() ||
            fetched.screenshotUrls.isNotEmpty()
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
        val soulTask = resolveTaskTarget(settings, settings.soulTarget, forSoul = true)
        val result = soulClient.generateGamerSoul(
            apiKey = settings.apiKeyFor(soulTask.provider),
            likedGames = settings.likedGames,
            modelId = soulTask.modelId,
            userTastePrompt = settings.userTastePrompt,
            provider = soulTask.provider
        )
        result.onSuccess { profile ->
            preferences.saveGamerSoulProfile(profile)
        }
        return result
    }

    /**
     * Generates fresh game discovery recommendations outside the eShop sale feed.
     * NEVER auto-regenerates [FilterSettings.gamerSoulProfile]: the player's soul is strictly
     * updated only when the user manually refreshes it.
     */
    suspend fun discoverGamesForSoul(freshBatch: Boolean = false): Result<List<AiDiscoveryPick>> {
        val settings = preferences.getSettings()
        val soulTask = resolveTaskTarget(settings, settings.soulTarget, forSoul = true)
        val soulProfile = settings.gamerSoulProfile

        val priorHistory = if (freshBatch) preferences.getDiscoveryHistory() else emptyList()
        val currentTitles = settings.discoveryPicks.map { it.title }
        val previousTitles = (currentTitles + priorHistory).distinct().take(36)

        val picksResult = soulClient.discoverGamesForSoul(
            apiKey = settings.apiKeyFor(soulTask.provider),
            soulProfile = soulProfile,
            likedGames = settings.likedGames,
            modelId = soulTask.modelId,
            userTastePrompt = settings.userTastePrompt,
            excludePreviousTitles = previousTitles,
            desiredCount = 7,
            provider = soulTask.provider,
            dismissedTitles = settings.dismissedDiscoveryTitles
        )
        val rawPicks = picksResult.getOrElse { return Result.failure(it) }
        val enriched = runCatching { eshopClient.enrichDiscoveryPicks(rawPicks) }.getOrDefault(rawPicks)
        val dismissedKeys = settings.dismissedDiscoveryTitles.flatMap {
            listOf(DealTitleNormalizer.normalize(it), DealTitleNormalizer.canonicalKey(it))
        }.toSet()
        val finalPicks = enriched.filterNot { pick ->
            DealTitleNormalizer.matchesLibraryGame(pick.title, pick.eshopId, settings.likedGames) ||
                DealTitleNormalizer.normalize(pick.title) in dismissedKeys ||
                DealTitleNormalizer.canonicalKey(pick.title) in dismissedKeys
        }.ifEmpty {
            return Result.failure(IllegalStateException("Tutti i titoli proposti sono già presenti nella tua libreria."))
        }
        preferences.saveDiscoveryPicks(finalPicks)
        preferences.appendDiscoveryHistory(finalPicks.map { it.title })
        return Result.success(finalPicks)
    }

    private fun resolveTaskTarget(
        settings: FilterSettings,
        preferred: com.switchpulse.app.data.model.AiTaskTarget,
        forSoul: Boolean
    ): com.switchpulse.app.data.model.AiTaskTarget {
        if (settings.hasKeyFor(preferred.provider)) return preferred
        val fallbackProvider = settings.configuredProviders().firstOrNull() ?: return preferred
        return com.switchpulse.app.data.model.AiTaskTarget(
            provider = fallbackProvider,
            modelId = GeminiModels.defaultModelFor(fallbackProvider, forSoul = forSoul)
        )
    }

    suspend fun notifyWidgetChanged() {
        val deals = preferences.getCachedDeals()
        val settings = preferences.getSettings()
        updateWidgetForSettings(deals, settings)
    }

    private suspend fun updateWidgetForSettings(
        deals: List<Deal>,
        settings: FilterSettings,
        resetCarouselRank: Boolean = false
    ) {
        val widgetTop = DealFilterEngine.filterTopTen(
            rawDeals = deals,
            settings = settings,
            platformOverride = settings.widgetPlatformFilter,
            limit = WIDGET_FEED_LIMIT
        )
        runCatching { coverCache.prefetchCovers(widgetTop.map { it.id to it.imageUrl }) }
        if (resetCarouselRank) {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(CarouselWidget::class.java)
                ids.forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[CarouselWidget.KEY_ACTIVE_RANK] = 1
                        }
                    }
                }
            }
        }
        runCatching { TopCoverWidget().updateAll(context) }
        runCatching { CarouselWidget().updateAll(context) }
    }

    companion object {
        const val APP_FEED_LIMIT = 30
        const val WIDGET_FEED_LIMIT = 10

        fun defaultSharedHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
