package com.switchpulse.app.data

import android.content.Context
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.remote.GeminiReranker
import com.switchpulse.app.data.remote.NintendoEshopClient
import com.switchpulse.app.widget.EditorialBentoWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DealsRepository(
    private val context: Context,
    val preferences: PreferencesStore = PreferencesStore(context),
    private val eshopClient: NintendoEshopClient = NintendoEshopClient(),
    private val geminiReranker: GeminiReranker = GeminiReranker(),
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

    suspend fun refreshDeals(): Result<List<Deal>> {
        return runCatching {
            val settings = preferences.getSettings()
            val fetchedDeals = eshopClient.fetchPopularDeals(likedGames = settings.likedGames)
            val useAi = settings.geminiApiKey.isNotBlank() &&
                settings.likedGames.isNotEmpty() &&
                (settings.recommendationWeight > 0 || settings.forYouOnlyMode)

            val freshDeals = if (useAi) {
                val aiScores = geminiReranker.rerank(
                    apiKey = settings.geminiApiKey,
                    likedGames = settings.likedGames,
                    candidates = fetchedDeals
                )
                if (aiScores.isEmpty()) fetchedDeals
                else fetchedDeals.map { deal ->
                    val entry = aiScores[deal.id]
                    if (entry == null) deal
                    else deal.copy(
                        aiAffinityScore = entry.score,
                        aiReason = entry.reason
                    )
                }
            } else {
                fetchedDeals
            }

            val now = System.currentTimeMillis()
            preferences.saveCachedDeals(freshDeals, now)

            val widgetTop = DealFilterEngine.filterTopTen(
                rawDeals = freshDeals,
                settings = settings,
                platformOverride = settings.widgetPlatformFilter,
                limit = WIDGET_FEED_LIMIT
            )
            coverCache.prefetchCovers(widgetTop.map { it.id to it.imageUrl })

            EditorialBentoWidget().updateAll(context)
            freshDeals
        }
    }

    suspend fun searchGamesForProfile(query: String): List<LikedGame> {
        return eshopClient.searchGamesForProfile(query)
    }

    suspend fun notifyWidgetChanged() {
        val deals = preferences.getCachedDeals()
        val settings = preferences.getSettings()
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
