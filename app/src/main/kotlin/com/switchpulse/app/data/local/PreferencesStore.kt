package com.switchpulse.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.AiTaskTarget
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FeedSortMode
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.GeminiModelInfo
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.data.remote.GeminiModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "switchpulse_prefs")

class PreferencesStore(
    private val context: Context,
    val database: PulseDatabase = PulseDatabase(context)
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val migrationMutex = Mutex()
    @Volatile
    private var migrated = false

    val settingsFlow: Flow<FilterSettings> = combine(
        context.dataStore.data.distinctUntilChanged(),
        database.libraryFlow.distinctUntilChanged()
    ) { prefs, dbLibrary ->
        val titlesMapJson = prefs[KEY_BLACKLIST_TITLES].orEmpty()
        val titlesMap = runCatching {
            if (titlesMapJson.isBlank()) emptyMap()
            else json.decodeFromString<Map<String, String>>(titlesMapJson)
        }.getOrDefault(emptyMap())

        // Fallback if migration hasn't run yet during first cold emission.
        val likedGames = dbLibrary.ifEmpty {
            readLikedList(prefs[KEY_LIKED_GAMES_JSON])
        }

        val gamerSoul = prefs[KEY_GAMER_SOUL_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<GamerSoulProfile>(raw) }.getOrNull()
        }
        val discoveryPicks = prefs[KEY_AI_DISCOVERY_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<List<AiDiscoveryPick>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()
        val dismissedTitles = prefs[KEY_DISMISSED_DISCOVERY_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()

        val previousFeedOrder = prefs[KEY_PREVIOUS_FEED_ORDER_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        }.orEmpty()

        val feedSortMode = runCatching {
            FeedSortMode.valueOf(prefs[KEY_FEED_SORT_MODE].orEmpty())
        }.getOrDefault(FeedSortMode.PERSONAL)

        val availableModels = readModelsList(prefs[KEY_AVAILABLE_GEMINI_MODELS_JSON])
        val openRouterModels = readModelsList(prefs[KEY_AVAILABLE_OPENROUTER_MODELS_JSON])
        val deepSeekModels = readModelsList(prefs[KEY_AVAILABLE_DEEPSEEK_MODELS_JSON])

        val rerankProv = AiProvider.fromId(prefs[KEY_RERANK_PROVIDER])
        val insightsProv = AiProvider.fromId(prefs[KEY_INSIGHTS_PROVIDER])
        val soulProv = AiProvider.fromId(prefs[KEY_SOUL_PROVIDER])

        FilterSettings(
            minDiscountPercent = prefs[KEY_MIN_DISCOUNT] ?: 30,
            minRegularPriceEur = prefs[KEY_MIN_REGULAR_EUR] ?: 10f,
            appPlatformFilter = parsePlatformFilter(prefs[KEY_APP_PLATFORM]),
            widgetPlatformFilter = parsePlatformFilter(prefs[KEY_WIDGET_PLATFORM]),
            feedSortMode = feedSortMode,
            expiringSoonOnly = prefs[KEY_EXPIRING_SOON_ONLY] ?: false,
            previousFeedOrder = previousFeedOrder,
            syncIntervalHours = prefs[KEY_SYNC_INTERVAL_HOURS] ?: 12,
            backgroundRerankPolicy = BackgroundRerankPolicy.fromName(prefs[KEY_BACKGROUND_RERANK_POLICY]),
            blacklistIds = prefs[KEY_BLACKLIST_IDS] ?: emptySet(),
            blacklistTitles = titlesMap,
            likedGames = likedGames,
            recommendationWeight = prefs[KEY_RECOMMENDATION_WEIGHT] ?: 50,
            forYouOnlyMode = prefs[KEY_FOR_YOU_MODE] ?: false,
            geminiApiKey = SecureKeyStore.decrypt(prefs[KEY_GEMINI_API_KEY]),
            openRouterApiKey = SecureKeyStore.decrypt(prefs[KEY_OPENROUTER_API_KEY]),
            deepSeekApiKey = SecureKeyStore.decrypt(prefs[KEY_DEEPSEEK_API_KEY]),
            geminiModel = prefs[KEY_GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: GeminiModels.HYDROGEN,
            rerankTarget = AiTaskTarget(
                provider = rerankProv,
                modelId = prefs[KEY_GEMINI_MODEL_RERANK]?.takeIf { it.isNotBlank() }
                    ?: GeminiModels.defaultModelFor(rerankProv)
            ),
            insightsTarget = AiTaskTarget(
                provider = insightsProv,
                modelId = prefs[KEY_GEMINI_MODEL_INSIGHTS]?.takeIf { it.isNotBlank() }
                    ?: GeminiModels.defaultModelFor(insightsProv)
            ),
            soulTarget = AiTaskTarget(
                provider = soulProv,
                modelId = prefs[KEY_GEMINI_MODEL_SOUL]?.takeIf { it.isNotBlank() }
                    ?: GeminiModels.defaultModelFor(soulProv, forSoul = true)
            ),
            availableGeminiModels = availableModels,
            availableOpenRouterModels = openRouterModels,
            availableDeepSeekModels = deepSeekModels,
            geminiModelsUpdatedAtMs = prefs[KEY_GEMINI_MODELS_UPDATED_MS] ?: 0L,
            geminiStatusSummary = prefs[KEY_GEMINI_STATUS_SUMMARY] ?: "",
            geminiSignature = prefs[KEY_GEMINI_SIGNATURE] ?: "",
            userTastePrompt = prefs[KEY_USER_TASTE_PROMPT] ?: "",
            gamerSoulProfile = gamerSoul,
            discoveryPicks = discoveryPicks,
            dismissedDiscoveryTitles = dismissedTitles,
            githubRepo = prefs[KEY_GITHUB_REPO] ?: BuildConfig.DEFAULT_GITHUB_REPO,
            autoCheckUpdates = prefs[KEY_AUTO_CHECK_UPDATES] ?: true,
            lastSyncEpochMs = prefs[KEY_LAST_SYNC_MS] ?: 0L
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val cachedDealsFlow: Flow<List<Deal>> = database.cachedDealsFlow

    /**
     * One-time migration moving heavy catalog/library/insights blobs out of DataStore into SQLite
     * and encrypting any legacy plaintext BYOK API keys at rest via Android KeyStore.
     */
    suspend fun migrateLegacyDataIfNeeded() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            importPortableAiKeysIfPresent()
            val prefs = context.dataStore.data.first()
            val alreadyMigrated = prefs[KEY_DB_MIGRATED_V1] == true
            if (!alreadyMigrated) {
                val legacyLibrary = readLikedList(prefs[KEY_LIKED_GAMES_JSON])
                if (legacyLibrary.isNotEmpty() && database.getLibraryGames().isEmpty()) {
                    database.saveLibraryGames(legacyLibrary)
                }
                val legacyDealsRaw = prefs[KEY_CACHED_DEALS_JSON].orEmpty()
                if (legacyDealsRaw.isNotBlank() && database.getCachedDeals().isEmpty()) {
                    val legacyDeals = runCatching {
                        json.decodeFromString<List<Deal>>(legacyDealsRaw)
                    }.getOrDefault(emptyList())
                    if (legacyDeals.isNotEmpty()) {
                        database.saveCachedDeals(legacyDeals)
                    }
                }
                val legacyInsightsRaw = prefs[KEY_INSIGHTS_CACHE_JSON].orEmpty()
                if (legacyInsightsRaw.isNotBlank()) {
                    val map = runCatching {
                        json.decodeFromString<Map<String, GameExternalInsights>>(legacyInsightsRaw)
                    }.getOrDefault(emptyMap())
                    map.values.forEach { database.saveCachedInsight(it) }
                }

                context.dataStore.edit { edit ->
                    edit.remove(KEY_LIKED_GAMES_JSON)
                    edit.remove(KEY_CACHED_DEALS_JSON)
                    edit.remove(KEY_INSIGHTS_CACHE_JSON)
                    // Encrypt any legacy plaintext API keys on disk.
                    listOf(KEY_GEMINI_API_KEY, KEY_OPENROUTER_API_KEY, KEY_DEEPSEEK_API_KEY).forEach { key ->
                        val rawVal = edit[key].orEmpty()
                        if (rawVal.isNotBlank() && !SecureKeyStore.isEncrypted(rawVal)) {
                            edit[key] = SecureKeyStore.encrypt(rawVal)
                        }
                    }
                    edit[KEY_DB_MIGRATED_V1] = true
                }
            }
            migrated = true
        }
    }

    private suspend fun importPortableAiKeysIfPresent() {
        val file = File(context.filesDir, PORTABLE_AI_KEYS_FILE)
        if (!file.isFile) return
        runCatching {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val gemini = obj.optString("gemini", "").trim()
            val openrouter = obj.optString("openrouter", "").trim()
            val deepseek = obj.optString("deepseek", "").trim()
            if (gemini.isNotEmpty() || openrouter.isNotEmpty() || deepseek.isNotEmpty()) {
                context.dataStore.edit { edit ->
                    if (gemini.isNotEmpty()) edit[KEY_GEMINI_API_KEY] = SecureKeyStore.encrypt(gemini)
                    if (openrouter.isNotEmpty()) edit[KEY_OPENROUTER_API_KEY] = SecureKeyStore.encrypt(openrouter)
                    if (deepseek.isNotEmpty()) edit[KEY_DEEPSEEK_API_KEY] = SecureKeyStore.encrypt(deepseek)
                    edit[KEY_GEMINI_SIGNATURE] = ""
                }
            }
        }
        runCatching { file.delete() }
    }

    suspend fun getSettings(): FilterSettings {
        migrateLegacyDataIfNeeded()
        return settingsFlow.first()
    }

    suspend fun getCachedDeals(): List<Deal> {
        migrateLegacyDataIfNeeded()
        return database.getCachedDeals()
    }

    suspend fun saveCachedDeals(deals: List<Deal>, syncEpochMs: Long? = null) {
        migrateLegacyDataIfNeeded()
        database.saveCachedDeals(deals)
        if (syncEpochMs != null) {
            context.dataStore.edit { prefs ->
                prefs[KEY_LAST_SYNC_MS] = syncEpochMs
            }
        }
    }

    suspend fun updateMinDiscount(percent: Int) {
        context.dataStore.edit { it[KEY_MIN_DISCOUNT] = percent.coerceIn(0, 80) }
    }

    suspend fun updateMinRegularPrice(eur: Float) {
        context.dataStore.edit { it[KEY_MIN_REGULAR_EUR] = eur.coerceIn(0f, 50f) }
    }

    suspend fun updateAppPlatformFilter(filter: PlatformFilter) {
        context.dataStore.edit { it[KEY_APP_PLATFORM] = filter.name }
    }

    suspend fun updateWidgetPlatformFilter(filter: PlatformFilter) {
        context.dataStore.edit { it[KEY_WIDGET_PLATFORM] = filter.name }
    }

    suspend fun updateFeedSortMode(sortMode: FeedSortMode) {
        context.dataStore.edit { it[KEY_FEED_SORT_MODE] = sortMode.name }
    }

    suspend fun updateExpiringSoonOnly(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EXPIRING_SOON_ONLY] = enabled }
    }

    suspend fun savePreviousFeedOrder(ids: List<String>) {
        context.dataStore.edit {
            it[KEY_PREVIOUS_FEED_ORDER_JSON] = json.encodeToString(ids.take(40))
        }
    }

    suspend fun updateSyncIntervalHours(hours: Int) {
        context.dataStore.edit { it[KEY_SYNC_INTERVAL_HOURS] = hours }
    }

    suspend fun updateBackgroundRerankPolicy(policy: BackgroundRerankPolicy) {
        context.dataStore.edit { it[KEY_BACKGROUND_RERANK_POLICY] = policy.name }
    }

    suspend fun updateGithubRepo(repo: String) {
        context.dataStore.edit { it[KEY_GITHUB_REPO] = repo.trim() }
    }

    suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun hideDeal(dealId: String, title: String) {
        context.dataStore.edit { prefs ->
            val ids = (prefs[KEY_BLACKLIST_IDS] ?: emptySet()) + dealId
            val currentMap = runCatching {
                val raw = prefs[KEY_BLACKLIST_TITLES].orEmpty()
                if (raw.isBlank()) emptyMap() else json.decodeFromString<Map<String, String>>(raw)
            }.getOrDefault(emptyMap()).toMutableMap()
            currentMap[dealId] = title
            prefs[KEY_BLACKLIST_IDS] = ids
            prefs[KEY_BLACKLIST_TITLES] = json.encodeToString(currentMap)
        }
    }

    suspend fun unhideDeal(dealId: String) {
        context.dataStore.edit { prefs ->
            val ids = (prefs[KEY_BLACKLIST_IDS] ?: emptySet()) - dealId
            val currentMap = runCatching {
                val raw = prefs[KEY_BLACKLIST_TITLES].orEmpty()
                if (raw.isBlank()) emptyMap() else json.decodeFromString<Map<String, String>>(raw)
            }.getOrDefault(emptyMap()).toMutableMap()
            currentMap.remove(dealId)
            prefs[KEY_BLACKLIST_IDS] = ids
            prefs[KEY_BLACKLIST_TITLES] = json.encodeToString(currentMap)
        }
    }

    suspend fun setGameRating(game: LikedGame, rating: Int?) {
        val cleanRating = rating?.coerceIn(1, 5)
        mutateLibraryGame(game) { existing ->
            val nextOwned = existing?.isOwned ?: game.isOwned
            val resolved = mergeGameMetadata(existing, game).copy(
                rating = cleanRating,
                isOwned = nextOwned
            )
            if (isEmptyLibraryRecord(resolved)) null else resolved
        }
    }

    suspend fun setGameOwned(game: LikedGame, owned: Boolean) {
        mutateLibraryGame(game) { existing ->
            val nextRating = existing?.rating ?: game.rating
            val resolved = mergeGameMetadata(existing, game).copy(
                rating = nextRating,
                isOwned = owned
            )
            if (isEmptyLibraryRecord(resolved)) null else resolved
        }
    }

    suspend fun setGameNote(game: LikedGame, note: String?) {
        val cleanNote = note?.trim()?.take(240)?.takeIf { it.isNotEmpty() }
        mutateLibraryGame(game) { existing ->
            val nextRating = existing?.rating ?: game.rating
            val nextOwned = existing?.isOwned ?: game.isOwned
            val resolved = mergeGameMetadata(existing, game).copy(
                rating = nextRating,
                isOwned = nextOwned,
                userNote = cleanNote
            )
            if (isEmptyLibraryRecord(resolved)) null else resolved
        }
    }

    suspend fun setGameTargetPrice(game: LikedGame, targetPriceEur: Double?) {
        val cleanTarget = targetPriceEur?.takeIf { it > 0.0 }
        mutateLibraryGame(game) { existing ->
            val nextRating = existing?.rating ?: game.rating
            val nextOwned = existing?.isOwned ?: game.isOwned
            val resolved = mergeGameMetadata(existing, game).copy(
                rating = nextRating,
                isOwned = nextOwned,
                targetPriceEur = cleanTarget
            )
            if (isEmptyLibraryRecord(resolved)) null else resolved
        }
    }

    suspend fun removeLikedGame(gameId: String) {
        migrateLegacyDataIfNeeded()
        val updated = database.getLibraryGames().filterNot { it.id == gameId }
        database.saveLibraryGames(updated)
        context.dataStore.edit { it[KEY_GEMINI_SIGNATURE] = "" }
    }

    private fun isEmptyLibraryRecord(game: LikedGame): Boolean =
        game.rating == null && !game.isOwned && game.userNote.isNullOrBlank() && game.targetPriceEur == null

    private suspend fun mutateLibraryGame(
        candidate: LikedGame,
        transform: (LikedGame?) -> LikedGame?
    ) {
        migrateLegacyDataIfNeeded()
        val current = database.getLibraryGames().toMutableList()
        val targetNorm = DealTitleNormalizer.normalize(candidate.title)
        val existingIdx = current.indexOfFirst {
            it.id == candidate.id || DealTitleNormalizer.normalize(it.title) == targetNorm
        }
        val existing = existingIdx.takeIf { it >= 0 }?.let { current[it] }
        val updated = transform(existing)

        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
        }
        if (updated != null) {
            current.add(0, updated)
        }
        database.saveLibraryGames(current)
        val aiRelevantChanged = existing?.rating != updated?.rating ||
            existing?.userNote != updated?.userNote ||
            existing?.isOwned != updated?.isOwned
        if (aiRelevantChanged) {
            context.dataStore.edit { prefs -> prefs[KEY_GEMINI_SIGNATURE] = "" }
        }
    }

    private fun mergeGameMetadata(existing: LikedGame?, incoming: LikedGame): LikedGame {
        if (existing == null) {
            return incoming.copy(
                userNote = incoming.userNote?.trim()?.takeIf { it.isNotEmpty() },
                addedAtMs = if (incoming.addedAtMs > 0L) incoming.addedAtMs else System.currentTimeMillis()
            )
        }
        return existing.copy(
            title = existing.title.ifBlank { incoming.title },
            publisher = existing.publisher.ifBlank { incoming.publisher },
            developer = existing.developer ?: incoming.developer,
            imageUrl = existing.imageUrl?.takeIf { it.isNotBlank() } ?: incoming.imageUrl?.takeIf { it.isNotBlank() },
            eshopUrl = existing.eshopUrl ?: incoming.eshopUrl,
            gameCategories = existing.gameCategories.ifEmpty { incoming.gameCategories },
            steamTagIds = existing.steamTagIds.ifEmpty { incoming.steamTagIds },
            steamTagNames = existing.steamTagNames.ifEmpty { incoming.steamTagNames },
            excerpt = existing.excerpt ?: incoming.excerpt,
            userNote = existing.userNote?.takeIf { it.isNotBlank() }
                ?: incoming.userNote?.takeIf { it.isNotBlank() },
            targetPriceEur = existing.targetPriceEur ?: incoming.targetPriceEur,
            addedAtMs = if (existing.addedAtMs > 0L) existing.addedAtMs else System.currentTimeMillis()
        )
    }

    suspend fun updateRecommendationWeight(weight: Int) {
        context.dataStore.edit { it[KEY_RECOMMENDATION_WEIGHT] = weight.coerceIn(0, 100) }
    }

    suspend fun updateForYouMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FOR_YOU_MODE] = enabled }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        updateProviderApiKey(AiProvider.GEMINI, apiKey)
    }

    suspend fun updateProviderApiKey(provider: AiProvider, apiKey: String) {
        val clean = apiKey.trim()
        val encrypted = if (clean.isBlank()) "" else SecureKeyStore.encrypt(clean)
        context.dataStore.edit { prefs ->
            when (provider) {
                AiProvider.GEMINI -> {
                    prefs[KEY_GEMINI_API_KEY] = encrypted
                    if (clean.isBlank()) prefs.remove(KEY_AVAILABLE_GEMINI_MODELS_JSON)
                }
                AiProvider.OPENROUTER -> {
                    prefs[KEY_OPENROUTER_API_KEY] = encrypted
                    if (clean.isBlank()) prefs.remove(KEY_AVAILABLE_OPENROUTER_MODELS_JSON)
                }
                AiProvider.DEEPSEEK -> {
                    prefs[KEY_DEEPSEEK_API_KEY] = encrypted
                    if (clean.isBlank()) prefs.remove(KEY_AVAILABLE_DEEPSEEK_MODELS_JSON)
                }
            }
            prefs[KEY_GEMINI_SIGNATURE] = ""

            if (clean.isNotBlank()) {
                val hadOtherKeys = AiProvider.entries
                    .filter { it != provider }
                    .any { other ->
                        val stored = when (other) {
                            AiProvider.GEMINI -> prefs[KEY_GEMINI_API_KEY]
                            AiProvider.OPENROUTER -> prefs[KEY_OPENROUTER_API_KEY]
                            AiProvider.DEEPSEEK -> prefs[KEY_DEEPSEEK_API_KEY]
                        }
                        SecureKeyStore.decrypt(stored).isNotBlank()
                    }
                if (!hadOtherKeys) {
                    prefs[KEY_RERANK_PROVIDER] = provider.id
                    prefs[KEY_GEMINI_MODEL_RERANK] = GeminiModels.defaultModelFor(provider, forSoul = false)
                    prefs[KEY_INSIGHTS_PROVIDER] = provider.id
                    prefs[KEY_GEMINI_MODEL_INSIGHTS] = GeminiModels.defaultModelFor(provider, forSoul = false)
                    prefs[KEY_SOUL_PROVIDER] = provider.id
                    prefs[KEY_GEMINI_MODEL_SOUL] = GeminiModels.defaultModelFor(provider, forSoul = true)
                }
            } else {
                val anyRemaining = listOf(
                    prefs[KEY_GEMINI_API_KEY],
                    prefs[KEY_OPENROUTER_API_KEY],
                    prefs[KEY_DEEPSEEK_API_KEY]
                ).any { SecureKeyStore.decrypt(it).isNotBlank() }
                if (!anyRemaining) {
                    prefs[KEY_GEMINI_STATUS_SUMMARY] = ""
                    prefs.remove(KEY_GEMINI_MODELS_UPDATED_MS)
                }
            }
        }
    }

    suspend fun updateRerankTarget(provider: AiProvider, modelId: String) {
        val clean = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider) }
        context.dataStore.edit {
            it[KEY_RERANK_PROVIDER] = provider.id
            it[KEY_GEMINI_MODEL_RERANK] = clean
            it[KEY_GEMINI_MODEL] = clean
            it[KEY_GEMINI_SIGNATURE] = ""
        }
    }

    suspend fun updateInsightsTarget(provider: AiProvider, modelId: String) {
        val clean = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider) }
        context.dataStore.edit {
            it[KEY_INSIGHTS_PROVIDER] = provider.id
            it[KEY_GEMINI_MODEL_INSIGHTS] = clean
        }
    }

    suspend fun updateSoulTarget(provider: AiProvider, modelId: String) {
        val clean = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider, forSoul = true) }
        context.dataStore.edit {
            it[KEY_SOUL_PROVIDER] = provider.id
            it[KEY_GEMINI_MODEL_SOUL] = clean
        }
    }

    suspend fun saveAvailableModelsForProvider(
        provider: AiProvider,
        models: List<GeminiModelInfo>,
        updatedAtMs: Long = System.currentTimeMillis()
    ) {
        val targetKey = when (provider) {
            AiProvider.GEMINI -> KEY_AVAILABLE_GEMINI_MODELS_JSON
            AiProvider.OPENROUTER -> KEY_AVAILABLE_OPENROUTER_MODELS_JSON
            AiProvider.DEEPSEEK -> KEY_AVAILABLE_DEEPSEEK_MODELS_JSON
        }
        context.dataStore.edit {
            if (models.isEmpty()) {
                it.remove(targetKey)
            } else {
                it[targetKey] = json.encodeToString(models)
                it[KEY_GEMINI_MODELS_UPDATED_MS] = updatedAtMs
            }
        }
    }

    suspend fun saveGeminiRunState(statusSummary: String, signature: String) {
        context.dataStore.edit {
            it[KEY_GEMINI_STATUS_SUMMARY] = statusSummary
            it[KEY_GEMINI_SIGNATURE] = signature
        }
    }

    suspend fun updateUserTastePrompt(prompt: String) {
        val clean = prompt.trim().take(600)
        context.dataStore.edit {
            it[KEY_USER_TASTE_PROMPT] = clean
            it[KEY_GEMINI_SIGNATURE] = ""
        }
    }

    suspend fun saveGamerSoulProfile(profile: GamerSoulProfile?) {
        context.dataStore.edit {
            if (profile == null) {
                it.remove(KEY_GAMER_SOUL_JSON)
            } else {
                it[KEY_GAMER_SOUL_JSON] = json.encodeToString(profile)
            }
        }
    }

    suspend fun saveDiscoveryPicks(picks: List<AiDiscoveryPick>) {
        context.dataStore.edit {
            if (picks.isEmpty()) {
                it.remove(KEY_AI_DISCOVERY_JSON)
            } else {
                it[KEY_AI_DISCOVERY_JSON] = json.encodeToString(picks)
            }
        }
    }

    suspend fun getDiscoveryHistory(): List<String> {
        val raw = context.dataStore.data.first()[KEY_DISCOVERY_HISTORY_JSON].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun appendDiscoveryHistory(titles: List<String>, maxKeep: Int = 36) {
        val cleanNew = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanNew.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISCOVERY_HISTORY_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
            val seen = HashSet<String>()
            val merged = (cleanNew + current).filter { seen.add(DealTitleNormalizer.normalize(it)) }.take(maxKeep)
            prefs[KEY_DISCOVERY_HISTORY_JSON] = json.encodeToString(merged)
        }
    }

    suspend fun clearDiscoveryHistory() {
        context.dataStore.edit { it.remove(KEY_DISCOVERY_HISTORY_JSON) }
    }

    suspend fun dismissDiscoveryPick(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        val normTarget = DealTitleNormalizer.normalize(clean)
        context.dataStore.edit { prefs ->
            val currentDismissed = prefs[KEY_DISMISSED_DISCOVERY_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            }.orEmpty().toMutableList()
            if (currentDismissed.none { DealTitleNormalizer.normalize(it) == normTarget }) {
                currentDismissed.add(0, clean)
            }
            prefs[KEY_DISMISSED_DISCOVERY_JSON] = json.encodeToString(currentDismissed.take(80))

            val currentPicks = prefs[KEY_AI_DISCOVERY_JSON]?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching { json.decodeFromString<List<AiDiscoveryPick>>(raw) }.getOrDefault(emptyList())
            }.orEmpty().filterNot { DealTitleNormalizer.normalize(it.title) == normTarget }
            prefs[KEY_AI_DISCOVERY_JSON] = json.encodeToString(currentPicks)
        }
    }

    suspend fun getCachedInsight(dealId: String): GameExternalInsights? {
        migrateLegacyDataIfNeeded()
        return database.getCachedInsight(dealId)
    }

    suspend fun saveCachedInsight(insight: GameExternalInsights) {
        migrateLegacyDataIfNeeded()
        database.saveCachedInsight(insight)
    }

    suspend fun isGroundingBlocked(keyFingerprint: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val raw = context.dataStore.data.first()[KEY_GROUNDING_BLOCKED_JSON].orEmpty()
        if (raw.isBlank()) return false
        val map = runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
        val until = map[keyFingerprint] ?: return false
        return nowMs < until
    }

    suspend fun markGroundingBlocked(keyFingerprint: String, blockedUntilMs: Long) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_GROUNDING_BLOCKED_JSON].orEmpty()
            val map = runCatching {
                if (raw.isBlank()) mutableMapOf()
                else json.decodeFromString<Map<String, Long>>(raw).toMutableMap()
            }.getOrDefault(mutableMapOf())
            map[keyFingerprint] = blockedUntilMs
            prefs[KEY_GROUNDING_BLOCKED_JSON] = json.encodeToString(map)
        }
    }

    suspend fun importBackup(backup: SwitchPulseBackup) {
        migrateLegacyDataIfNeeded()
        database.saveLibraryGames(backup.likedGames)
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_TASTE_PROMPT] = backup.userTastePrompt.trim().take(600)
            if (backup.gamerSoulProfile != null) {
                prefs[KEY_GAMER_SOUL_JSON] = json.encodeToString(backup.gamerSoulProfile)
            } else {
                prefs.remove(KEY_GAMER_SOUL_JSON)
            }
            prefs[KEY_AI_DISCOVERY_JSON] = json.encodeToString(backup.discoveryPicks)
            prefs[KEY_DISMISSED_DISCOVERY_JSON] = json.encodeToString(backup.dismissedDiscoveryTitles)
            prefs[KEY_BLACKLIST_IDS] = backup.blacklistIds
            prefs[KEY_BLACKLIST_TITLES] = json.encodeToString(backup.blacklistTitles)
            prefs[KEY_MIN_DISCOUNT] = backup.minDiscountPercent.coerceIn(0, 80)
            prefs[KEY_MIN_REGULAR_EUR] = backup.minRegularPriceEur.coerceIn(0f, 50f)
            prefs[KEY_APP_PLATFORM] = backup.appPlatformFilter.name
            prefs[KEY_WIDGET_PLATFORM] = backup.widgetPlatformFilter.name
            prefs[KEY_RECOMMENDATION_WEIGHT] = backup.recommendationWeight.coerceIn(0, 100)
            prefs[KEY_FOR_YOU_MODE] = backup.forYouOnlyMode
            prefs[KEY_SYNC_INTERVAL_HOURS] = backup.syncIntervalHours.coerceAtLeast(1)
            prefs[KEY_BACKGROUND_RERANK_POLICY] = backup.backgroundRerankPolicy.name
            prefs[KEY_GEMINI_SIGNATURE] = ""
        }
    }

    private fun readModelsList(raw: String?): List<GeminiModelInfo> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<GeminiModelInfo>>(raw) }.getOrDefault(emptyList())
    }

    private fun readLikedList(raw: String?): List<LikedGame> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LikedGame>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun resetFilters() {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIN_DISCOUNT] = 30
            prefs[KEY_MIN_REGULAR_EUR] = 10f
            prefs[KEY_RECOMMENDATION_WEIGHT] = 50
            prefs[KEY_FOR_YOU_MODE] = false
            prefs[KEY_EXPIRING_SOON_ONLY] = false
            prefs[KEY_FEED_SORT_MODE] = FeedSortMode.PERSONAL.name
            prefs[KEY_APP_PLATFORM] = PlatformFilter.ALL.name
        }
    }

    private fun parsePlatformFilter(raw: String?): PlatformFilter {
        return runCatching { PlatformFilter.valueOf(raw.orEmpty()) }.getOrDefault(PlatformFilter.ALL)
    }

    companion object {
        private val KEY_DB_MIGRATED_V1 = booleanPreferencesKey("db_migrated_v1")
        private val KEY_MIN_DISCOUNT = intPreferencesKey("min_discount_pct")
        private val KEY_MIN_REGULAR_EUR = floatPreferencesKey("min_regular_eur")
        private val KEY_APP_PLATFORM = stringPreferencesKey("app_platform")
        private val KEY_WIDGET_PLATFORM = stringPreferencesKey("widget_platform")
        private val KEY_SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        private val KEY_BACKGROUND_RERANK_POLICY = stringPreferencesKey("background_rerank_policy")
        private val KEY_BLACKLIST_IDS = stringSetPreferencesKey("blacklist_ids")
        private val KEY_BLACKLIST_TITLES = stringPreferencesKey("blacklist_titles_json")
        private val KEY_LIKED_GAMES_JSON = stringPreferencesKey("liked_games_json")
        private val KEY_RECOMMENDATION_WEIGHT = intPreferencesKey("recommendation_weight")
        private val KEY_FOR_YOU_MODE = booleanPreferencesKey("for_you_mode")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        private val KEY_DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_RERANK_PROVIDER = stringPreferencesKey("rerank_provider")
        private val KEY_INSIGHTS_PROVIDER = stringPreferencesKey("insights_provider")
        private val KEY_SOUL_PROVIDER = stringPreferencesKey("soul_provider")
        private val KEY_GEMINI_MODEL_RERANK = stringPreferencesKey("gemini_model_rerank")
        private val KEY_GEMINI_MODEL_INSIGHTS = stringPreferencesKey("gemini_model_insights")
        private val KEY_GEMINI_MODEL_SOUL = stringPreferencesKey("gemini_model_soul")
        private val KEY_AVAILABLE_GEMINI_MODELS_JSON = stringPreferencesKey("available_gemini_models_json")
        private val KEY_AVAILABLE_OPENROUTER_MODELS_JSON = stringPreferencesKey("available_openrouter_models_json")
        private val KEY_AVAILABLE_DEEPSEEK_MODELS_JSON = stringPreferencesKey("available_deepseek_models_json")
        private val KEY_GEMINI_MODELS_UPDATED_MS = longPreferencesKey("gemini_models_updated_ms")
        private val KEY_GEMINI_STATUS_SUMMARY = stringPreferencesKey("gemini_status_summary")
        private val KEY_GEMINI_SIGNATURE = stringPreferencesKey("gemini_signature")
        private val KEY_USER_TASTE_PROMPT = stringPreferencesKey("user_taste_prompt")
        private val KEY_GAMER_SOUL_JSON = stringPreferencesKey("gamer_soul_json")
        private val KEY_AI_DISCOVERY_JSON = stringPreferencesKey("ai_discovery_json")
        private val KEY_DISCOVERY_HISTORY_JSON = stringPreferencesKey("discovery_history_json")
        private val KEY_DISMISSED_DISCOVERY_JSON = stringPreferencesKey("dismissed_discovery_json")
        private val KEY_INSIGHTS_CACHE_JSON = stringPreferencesKey("insights_cache_json")
        private val KEY_GROUNDING_BLOCKED_JSON = stringPreferencesKey("grounding_blocked_json")
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        private val KEY_CACHED_DEALS_JSON = stringPreferencesKey("cached_deals_json")
        private val KEY_FEED_SORT_MODE = stringPreferencesKey("feed_sort_mode")
        private val KEY_EXPIRING_SOON_ONLY = booleanPreferencesKey("expiring_soon_only")
        private val KEY_PREVIOUS_FEED_ORDER_JSON = stringPreferencesKey("previous_feed_order_json")
        private const val PORTABLE_AI_KEYS_FILE = "apphub_portable_ai_keys.json"
    }
}
