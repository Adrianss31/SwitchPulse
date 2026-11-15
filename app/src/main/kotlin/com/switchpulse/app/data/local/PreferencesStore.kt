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
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.data.remote.GeminiModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "switchpulse_prefs")

class PreferencesStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settingsFlow: Flow<FilterSettings> = context.dataStore.data.map { prefs ->
        val titlesMapJson = prefs[KEY_BLACKLIST_TITLES].orEmpty()
        val titlesMap = runCatching {
            if (titlesMapJson.isBlank()) emptyMap()
            else json.decodeFromString<Map<String, String>>(titlesMapJson)
        }.getOrDefault(emptyMap())

        val likedGames = readLikedList(prefs[KEY_LIKED_GAMES_JSON])

        FilterSettings(
            minDiscountPercent = prefs[KEY_MIN_DISCOUNT] ?: 30,
            minRegularPriceEur = prefs[KEY_MIN_REGULAR_EUR] ?: 10f,
            appPlatformFilter = parsePlatformFilter(prefs[KEY_APP_PLATFORM]),
            widgetPlatformFilter = parsePlatformFilter(prefs[KEY_WIDGET_PLATFORM]),
            syncIntervalHours = prefs[KEY_SYNC_INTERVAL_HOURS] ?: 12,
            blacklistIds = prefs[KEY_BLACKLIST_IDS] ?: emptySet(),
            blacklistTitles = titlesMap,
            likedGames = likedGames,
            recommendationWeight = prefs[KEY_RECOMMENDATION_WEIGHT] ?: 50,
            forYouOnlyMode = prefs[KEY_FOR_YOU_MODE] ?: false,
            geminiApiKey = prefs[KEY_GEMINI_API_KEY] ?: "",
            geminiModel = prefs[KEY_GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: GeminiModels.HYDROGEN,
            geminiStatusSummary = prefs[KEY_GEMINI_STATUS_SUMMARY] ?: "",
            geminiSignature = prefs[KEY_GEMINI_SIGNATURE] ?: "",
            userTastePrompt = prefs[KEY_USER_TASTE_PROMPT] ?: "",
            githubRepo = prefs[KEY_GITHUB_REPO] ?: BuildConfig.DEFAULT_GITHUB_REPO,
            autoCheckUpdates = prefs[KEY_AUTO_CHECK_UPDATES] ?: true,
            lastSyncEpochMs = prefs[KEY_LAST_SYNC_MS] ?: 0L
        )
    }

    val cachedDealsFlow: Flow<List<Deal>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CACHED_DEALS_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<Deal>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun getSettings(): FilterSettings = settingsFlow.first()

    suspend fun getCachedDeals(): List<Deal> = cachedDealsFlow.first()

    suspend fun saveCachedDeals(deals: List<Deal>, syncEpochMs: Long? = null) {
        val encoded = json.encodeToString(deals)
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHED_DEALS_JSON] = encoded
            if (syncEpochMs != null) {
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

    suspend fun updateSyncIntervalHours(hours: Int) {
        context.dataStore.edit { it[KEY_SYNC_INTERVAL_HOURS] = hours }
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
            if (resolved.rating == null && !resolved.isOwned && resolved.userNote.isNullOrBlank()) null else resolved
        }
    }

    suspend fun setGameOwned(game: LikedGame, owned: Boolean) {
        mutateLibraryGame(game) { existing ->
            val nextRating = existing?.rating ?: game.rating
            val resolved = mergeGameMetadata(existing, game).copy(
                rating = nextRating,
                isOwned = owned
            )
            if (resolved.rating == null && !resolved.isOwned && resolved.userNote.isNullOrBlank()) null else resolved
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
            if (resolved.rating == null && !resolved.isOwned && resolved.userNote == null) null else resolved
        }
    }

    suspend fun removeLikedGame(gameId: String) {
        context.dataStore.edit { prefs ->
            val current = readLikedList(prefs[KEY_LIKED_GAMES_JSON]).filterNot { it.id == gameId }
            prefs[KEY_LIKED_GAMES_JSON] = json.encodeToString(current)
            prefs[KEY_GEMINI_SIGNATURE] = ""
        }
    }

    private suspend fun mutateLibraryGame(
        candidate: LikedGame,
        transform: (LikedGame?) -> LikedGame?
    ) {
        context.dataStore.edit { prefs ->
            val current = readLikedList(prefs[KEY_LIKED_GAMES_JSON]).toMutableList()
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
            prefs[KEY_LIKED_GAMES_JSON] = json.encodeToString(current)
            // Rating, note or ownership edits invalidate previous AI cache signature.
            val aiRelevantChanged = existing?.rating != updated?.rating ||
                existing?.userNote != updated?.userNote ||
                existing?.isOwned != updated?.isOwned
            if (aiRelevantChanged) {
                prefs[KEY_GEMINI_SIGNATURE] = ""
            }
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
        context.dataStore.edit {
            it[KEY_GEMINI_API_KEY] = apiKey.trim()
            it[KEY_GEMINI_SIGNATURE] = ""
            if (apiKey.trim().isBlank()) {
                it[KEY_GEMINI_STATUS_SUMMARY] = ""
            }
        }
    }

    suspend fun updateGeminiModel(modelId: String) {
        val clean = modelId.trim().ifBlank { GeminiModels.HYDROGEN }
        context.dataStore.edit {
            it[KEY_GEMINI_MODEL] = clean
            it[KEY_GEMINI_SIGNATURE] = ""
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

    suspend fun getCachedInsight(dealId: String): GameExternalInsights? {
        val raw = context.dataStore.data.first()[KEY_INSIGHTS_CACHE_JSON].orEmpty()
        if (raw.isBlank()) return null
        val map = runCatching {
            json.decodeFromString<Map<String, GameExternalInsights>>(raw)
        }.getOrDefault(emptyMap())
        return map[dealId]
    }

    suspend fun saveCachedInsight(insight: GameExternalInsights) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_INSIGHTS_CACHE_JSON].orEmpty()
            val current = if (raw.isBlank()) {
                mutableMapOf()
            } else {
                runCatching {
                    json.decodeFromString<Map<String, GameExternalInsights>>(raw).toMutableMap()
                }.getOrDefault(mutableMapOf())
            }
            current[insight.dealId] = insight
            val trimmed = if (current.size > 40) {
                current.entries
                    .sortedByDescending { it.value.fetchedAtMs }
                    .take(40)
                    .associate { it.key to it.value }
            } else {
                current
            }
            prefs[KEY_INSIGHTS_CACHE_JSON] = json.encodeToString(trimmed)
        }
    }

    private fun readLikedList(raw: String?): List<LikedGame> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LikedGame>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun resetFilters() {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIN_DISCOUNT] = 30
            prefs[KEY_MIN_REGULAR_EUR] = 10f
            prefs[KEY_APP_PLATFORM] = PlatformFilter.ALL.name
        }
    }

    private fun parsePlatformFilter(raw: String?): PlatformFilter {
        return runCatching { PlatformFilter.valueOf(raw.orEmpty()) }.getOrDefault(PlatformFilter.ALL)
    }

    companion object {
        private val KEY_MIN_DISCOUNT = intPreferencesKey("min_discount_pct")
        private val KEY_MIN_REGULAR_EUR = floatPreferencesKey("min_regular_eur")
        private val KEY_APP_PLATFORM = stringPreferencesKey("app_platform")
        private val KEY_WIDGET_PLATFORM = stringPreferencesKey("widget_platform")
        private val KEY_SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        private val KEY_BLACKLIST_IDS = stringSetPreferencesKey("blacklist_ids")
        private val KEY_BLACKLIST_TITLES = stringPreferencesKey("blacklist_titles_json")
        private val KEY_LIKED_GAMES_JSON = stringPreferencesKey("liked_games_json")
        private val KEY_RECOMMENDATION_WEIGHT = intPreferencesKey("recommendation_weight")
        private val KEY_FOR_YOU_MODE = booleanPreferencesKey("for_you_mode")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_GEMINI_STATUS_SUMMARY = stringPreferencesKey("gemini_status_summary")
        private val KEY_GEMINI_SIGNATURE = stringPreferencesKey("gemini_signature")
        private val KEY_USER_TASTE_PROMPT = stringPreferencesKey("user_taste_prompt")
        private val KEY_INSIGHTS_CACHE_JSON = stringPreferencesKey("insights_cache_json")
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        private val KEY_CACHED_DEALS_JSON = stringPreferencesKey("cached_deals_json")
    }
}
