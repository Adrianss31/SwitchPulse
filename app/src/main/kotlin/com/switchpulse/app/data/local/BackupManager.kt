package com.switchpulse.app.data.local

import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SwitchPulseBackup(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = 0L,
    val likedGames: List<LikedGame> = emptyList(),
    val userTastePrompt: String = "",
    val gamerSoulProfile: GamerSoulProfile? = null,
    val discoveryPicks: List<AiDiscoveryPick> = emptyList(),
    val dismissedDiscoveryTitles: List<String> = emptyList(),
    val blacklistIds: Set<String> = emptySet(),
    val blacklistTitles: Map<String, String> = emptyMap(),
    val minDiscountPercent: Int = 30,
    val minRegularPriceEur: Float = 10f,
    val appPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val widgetPlatformFilter: PlatformFilter = PlatformFilter.ALL,
    val recommendationWeight: Int = 50,
    val forYouOnlyMode: Boolean = false,
    val syncIntervalHours: Int = 12,
    val backgroundRerankPolicy: BackgroundRerankPolicy = BackgroundRerankPolicy.WIFI_ONLY
)

object BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Serializes library, ratings, notes, wishlist price targets, Soul profile, dismissed list and
     * display filters into JSON. Intentionally omits all BYOK API keys for security.
     */
    fun exportToJson(settings: FilterSettings, nowMs: Long = System.currentTimeMillis()): String {
        val payload = SwitchPulseBackup(
            schemaVersion = 1,
            exportedAtMs = nowMs,
            likedGames = settings.likedGames,
            userTastePrompt = settings.userTastePrompt,
            gamerSoulProfile = settings.gamerSoulProfile,
            discoveryPicks = settings.discoveryPicks,
            dismissedDiscoveryTitles = settings.dismissedDiscoveryTitles,
            blacklistIds = settings.blacklistIds,
            blacklistTitles = settings.blacklistTitles,
            minDiscountPercent = settings.minDiscountPercent,
            minRegularPriceEur = settings.minRegularPriceEur,
            appPlatformFilter = settings.appPlatformFilter,
            widgetPlatformFilter = settings.widgetPlatformFilter,
            recommendationWeight = settings.recommendationWeight,
            forYouOnlyMode = settings.forYouOnlyMode,
            syncIntervalHours = settings.syncIntervalHours,
            backgroundRerankPolicy = settings.backgroundRerankPolicy
        )
        return json.encodeToString(payload)
    }

    fun decodeFromJson(rawJson: String): Result<SwitchPulseBackup> = runCatching {
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "File backup vuoto" }
        val decoded = json.decodeFromString<SwitchPulseBackup>(trimmed)
        decoded
    }
}
