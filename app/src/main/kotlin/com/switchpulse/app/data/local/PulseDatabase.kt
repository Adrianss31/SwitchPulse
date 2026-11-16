package com.switchpulse.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.remote.SemanticRerankEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CachedSteamLookup(
    val normTitle: String,
    val appId: Int? = null,
    val found: Boolean = false,
    val reviewCount: Int = 0,
    val positivePercent: Int? = null,
    val tagIds: List<Int> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val developer: String? = null,
    val shortDescription: String? = null,
    val fetchedAtMs: Long = 0L
)

/**
 * Dedicated SQLite storage separating heavy catalogs, library entries, Steam lookups, per-deal AI
 * scores, insights and price history from lightweight user preferences in DataStore.
 */
class PulseDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dealsState: MutableStateFlow<List<Deal>> by lazy {
        MutableStateFlow(readDealsBlocking())
    }
    private val libraryState: MutableStateFlow<List<LikedGame>> by lazy {
        MutableStateFlow(readLibraryBlocking())
    }

    val cachedDealsFlow: Flow<List<Deal>> get() = dealsState.asStateFlow()
    val libraryFlow: Flow<List<LikedGame>> get() = libraryState.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE deals (
                id TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE library_games (
                id TEXT PRIMARY KEY,
                norm_title TEXT NOT NULL,
                position INTEGER NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE insights_cache (
                deal_id TEXT PRIMARY KEY,
                profile_sig TEXT NOT NULL,
                fetched_at_ms INTEGER NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE deal_ai_scores (
                norm_title TEXT NOT NULL,
                profile_sig TEXT NOT NULL,
                score REAL NOT NULL,
                reason TEXT,
                detailed_reason TEXT,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (norm_title, profile_sig)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE steam_lookup_cache (
                norm_title TEXT PRIMARY KEY,
                fetched_at_ms INTEGER NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE price_history (
                norm_title TEXT PRIMARY KEY,
                lowest_price_eur REAL NOT NULL,
                first_seen_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    private fun readDealsBlocking(): List<Deal> {
        val out = ArrayList<Deal>()
        readableDatabase.rawQuery(
            "SELECT json FROM deals ORDER BY sort_order ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = cursor.getString(0)
                runCatching { json.decodeFromString<Deal>(raw) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    @Synchronized
    private fun readLibraryBlocking(): List<LikedGame> {
        val out = ArrayList<LikedGame>()
        readableDatabase.rawQuery(
            "SELECT json FROM library_games ORDER BY position ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = cursor.getString(0)
                runCatching { json.decodeFromString<LikedGame>(raw) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    suspend fun getCachedDeals(): List<Deal> = withContext(Dispatchers.IO) {
        dealsState.value
    }

    suspend fun saveCachedDeals(deals: List<Deal>) = withContext(Dispatchers.IO) {
        replaceDealsSync(deals)
        dealsState.value = deals
    }

    @Synchronized
    private fun replaceDealsSync(deals: List<Deal>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("deals", null, null)
            deals.forEachIndexed { index, deal ->
                val cv = ContentValues().apply {
                    put("id", deal.id)
                    put("sort_order", index)
                    put("json", json.encodeToString(deal))
                }
                db.insertWithOnConflict("deals", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getLibraryGames(): List<LikedGame> = withContext(Dispatchers.IO) {
        libraryState.value
    }

    suspend fun saveLibraryGames(games: List<LikedGame>) = withContext(Dispatchers.IO) {
        replaceLibrarySync(games)
        libraryState.value = games
    }

    @Synchronized
    private fun replaceLibrarySync(games: List<LikedGame>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("library_games", null, null)
            games.forEachIndexed { index, game ->
                val cv = ContentValues().apply {
                    put("id", game.id)
                    put("norm_title", DealTitleNormalizer.normalize(game.title))
                    put("position", index)
                    put("json", json.encodeToString(game))
                }
                db.insertWithOnConflict("library_games", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getCachedInsight(dealId: String): GameExternalInsights? = withContext(Dispatchers.IO) {
        readInsightSync(dealId)
    }

    @Synchronized
    private fun readInsightSync(dealId: String): GameExternalInsights? {
        readableDatabase.rawQuery(
            "SELECT json FROM insights_cache WHERE deal_id = ?",
            arrayOf(dealId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return runCatching { json.decodeFromString<GameExternalInsights>(cursor.getString(0)) }.getOrNull()
        }
    }

    suspend fun saveCachedInsight(insight: GameExternalInsights) = withContext(Dispatchers.IO) {
        writeInsightSync(insight)
    }

    @Synchronized
    private fun writeInsightSync(insight: GameExternalInsights) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("deal_id", insight.dealId)
                put("profile_sig", insight.profileSignature)
                put("fetched_at_ms", insight.fetchedAtMs)
                put("json", json.encodeToString(insight))
            }
            db.insertWithOnConflict("insights_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL(
                "DELETE FROM insights_cache WHERE deal_id NOT IN (SELECT deal_id FROM insights_cache ORDER BY fetched_at_ms DESC LIMIT 100)"
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getAiScoresForProfile(profileSig: String): Map<String, SemanticRerankEntry> =
        withContext(Dispatchers.IO) {
            readAiScoresSync(profileSig)
        }

    @Synchronized
    private fun readAiScoresSync(profileSig: String): Map<String, SemanticRerankEntry> {
        val map = LinkedHashMap<String, SemanticRerankEntry>()
        readableDatabase.rawQuery(
            "SELECT norm_title, score, reason, detailed_reason FROM deal_ai_scores WHERE profile_sig = ?",
            arrayOf(profileSig)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)
                val score = cursor.getDouble(1)
                val reason = cursor.getString(2)
                val detailed = cursor.getString(3)
                map[title] = SemanticRerankEntry(
                    score = score,
                    reason = reason,
                    detailedReason = detailed
                )
            }
        }
        return map
    }

    suspend fun saveAiScoresForProfile(
        profileSig: String,
        scoresByNormTitle: Map<String, SemanticRerankEntry>,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (scoresByNormTitle.isEmpty()) return@withContext
        writeAiScoresSync(profileSig, scoresByNormTitle, nowMs)
    }

    @Synchronized
    private fun writeAiScoresSync(
        profileSig: String,
        scoresByNormTitle: Map<String, SemanticRerankEntry>,
        nowMs: Long
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            scoresByNormTitle.forEach { (normTitle, entry) ->
                val cv = ContentValues().apply {
                    put("norm_title", normTitle)
                    put("profile_sig", profileSig)
                    put("score", entry.score)
                    put("reason", entry.reason)
                    put("detailed_reason", entry.detailedReason)
                    put("updated_at_ms", nowMs)
                }
                db.insertWithOnConflict("deal_ai_scores", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val cutoff = nowMs - 30L * 24 * 60 * 60 * 1000L
            db.delete("deal_ai_scores", "updated_at_ms < ?", arrayOf(cutoff.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getSteamLookups(normTitles: List<String>, nowMs: Long = System.currentTimeMillis()): Map<String, CachedSteamLookup> =
        withContext(Dispatchers.IO) {
            readSteamLookupsSync(normTitles, nowMs)
        }

    @Synchronized
    private fun readSteamLookupsSync(normTitles: List<String>, nowMs: Long): Map<String, CachedSteamLookup> {
        if (normTitles.isEmpty()) return emptyMap()
        val foundTtlMs = 21L * 24 * 60 * 60 * 1000L
        val missTtlMs = 7L * 24 * 60 * 60 * 1000L
        val targetSet = normTitles.toSet()
        val out = LinkedHashMap<String, CachedSteamLookup>()
        readableDatabase.rawQuery("SELECT norm_title, json FROM steam_lookup_cache", null).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                if (key !in targetSet) continue
                val entry = runCatching {
                    json.decodeFromString<CachedSteamLookup>(cursor.getString(1))
                }.getOrNull() ?: continue
                val ttl = if (entry.found) foundTtlMs else missTtlMs
                if (nowMs - entry.fetchedAtMs <= ttl) {
                    out[key] = entry
                }
            }
        }
        return out
    }

    suspend fun saveSteamLookups(entries: List<CachedSteamLookup>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        writeSteamLookupsSync(entries)
    }

    @Synchronized
    private fun writeSteamLookupsSync(entries: List<CachedSteamLookup>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                val cv = ContentValues().apply {
                    put("norm_title", entry.normTitle)
                    put("fetched_at_ms", entry.fetchedAtMs)
                    put("json", json.encodeToString(entry))
                }
                db.insertWithOnConflict("steam_lookup_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Records lowest observed price per normalized title and annotates deals with `lowestSeenPriceEur`
     * and `isHistoricalLow` (true when we have at least one prior lower/higher record matching the
     * current price or beating prior seen prices).
     */
    suspend fun recordAndAnnotatePriceHistory(
        deals: List<Deal>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Deal> = withContext(Dispatchers.IO) {
        recordAndAnnotateSync(deals, nowMs)
    }

    @Synchronized
    private fun recordAndAnnotateSync(deals: List<Deal>, nowMs: Long): List<Deal> {
        if (deals.isEmpty()) return emptyList()
        val db = writableDatabase
        val existingLowest = HashMap<String, Double>()
        db.rawQuery("SELECT norm_title, lowest_price_eur FROM price_history", null).use { cursor ->
            while (cursor.moveToNext()) {
                existingLowest[cursor.getString(0)] = cursor.getDouble(1)
            }
        }

        val updatedLowest = HashMap<String, Double>()
        val result = deals.map { deal ->
            val norm = DealTitleNormalizer.normalize(deal.title)
            val prevMin = existingLowest[norm]
            val current = deal.discountedPriceEur
            if (current <= 0.0) {
                deal
            } else {
                val newMin = if (prevMin == null) current else minOf(prevMin, current)
                val curTracked = updatedLowest[norm]
                if (curTracked == null || newMin < curTracked) {
                    updatedLowest[norm] = newMin
                }
                val isHistLow = prevMin != null && current <= prevMin + 0.01 && current < deal.regularPriceEur
                deal.copy(
                    lowestSeenPriceEur = newMin,
                    isHistoricalLow = isHistLow
                )
            }
        }

        db.beginTransaction()
        try {
            updatedLowest.forEach { (norm, minPrice) ->
                val cv = ContentValues().apply {
                    put("norm_title", norm)
                    put("lowest_price_eur", minPrice)
                    put("first_seen_ms", nowMs)
                    put("updated_at_ms", nowMs)
                }
                db.insertWithOnConflict("price_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return result
    }

    companion object {
        private const val DB_NAME = "switchpulse.db"
        private const val DB_VERSION = 1
    }
}
