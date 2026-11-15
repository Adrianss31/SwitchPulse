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
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.PlatformFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "switchpulse_prefs")

class PreferencesStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val settingsFlow: Flow<FilterSettings> = context.dataStore.data.map { prefs ->
        val titlesMapJson = prefs[KEY_BLACKLIST_TITLES].orEmpty()
        val titlesMap = runCatching {
            if (titlesMapJson.isBlank()) emptyMap()
            else json.decodeFromString<Map<String, String>>(titlesMapJson)
        }.getOrDefault(emptyMap())

        FilterSettings(
            minDiscountPercent = prefs[KEY_MIN_DISCOUNT] ?: 30,
            minRegularPriceEur = prefs[KEY_MIN_REGULAR_EUR] ?: 10f,
            appPlatformFilter = parsePlatformFilter(prefs[KEY_APP_PLATFORM]),
            widgetPlatformFilter = parsePlatformFilter(prefs[KEY_WIDGET_PLATFORM]),
            syncIntervalHours = prefs[KEY_SYNC_INTERVAL_HOURS] ?: 12,
            blacklistIds = prefs[KEY_BLACKLIST_IDS] ?: emptySet(),
            blacklistTitles = titlesMap,
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

    suspend fun saveCachedDeals(deals: List<Deal>, syncEpochMs: Long) {
        val encoded = json.encodeToString(deals)
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHED_DEALS_JSON] = encoded
            prefs[KEY_LAST_SYNC_MS] = syncEpochMs
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
        private val KEY_GITHUB_REPO = stringPreferencesKey("github_repo")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        private val KEY_CACHED_DEALS_JSON = stringPreferencesKey("cached_deals_json")
    }
}
