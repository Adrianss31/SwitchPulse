package com.switchpulse.app.data

import android.content.Context
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.data.remote.NintendoEshopClient
import com.switchpulse.app.widget.EditorialBentoWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DealsRepository(
    private val context: Context,
    val preferences: PreferencesStore = PreferencesStore(context),
    private val eshopClient: NintendoEshopClient = NintendoEshopClient(),
    val coverCache: CoverBitmapCache = CoverBitmapCache(context)
) {
    val settingsFlow: Flow<FilterSettings> = preferences.settingsFlow
    val cachedDealsFlow: Flow<List<Deal>> = preferences.cachedDealsFlow

    val filteredAppDealsFlow: Flow<List<Deal>> = combine(
        cachedDealsFlow,
        settingsFlow
    ) { deals, settings ->
        DealFilterEngine.filterTopTen(deals, settings, settings.appPlatformFilter)
    }

    suspend fun refreshDeals(): Result<List<Deal>> {
        return runCatching {
            val freshDeals = eshopClient.fetchPopularDeals()
            val now = System.currentTimeMillis()
            preferences.saveCachedDeals(freshDeals, now)

            val settings = preferences.getSettings()
            val widgetTop = DealFilterEngine.filterTopTen(
                rawDeals = freshDeals,
                settings = settings,
                platformOverride = settings.widgetPlatformFilter
            )
            val appTop = DealFilterEngine.filterTopTen(
                rawDeals = freshDeals,
                settings = settings,
                platformOverride = settings.appPlatformFilter
            )
            val toPrefetch = (widgetTop + appTop)
                .distinctBy { it.id }
                .map { it.id to it.imageUrl }
            coverCache.prefetchCovers(toPrefetch)

            EditorialBentoWidget().updateAll(context)
            freshDeals
        }
    }

    suspend fun notifyWidgetChanged() {
        val deals = preferences.getCachedDeals()
        val settings = preferences.getSettings()
        val widgetTop = DealFilterEngine.filterTopTen(
            rawDeals = deals,
            settings = settings,
            platformOverride = settings.widgetPlatformFilter
        )
        coverCache.prefetchCovers(widgetTop.map { it.id to it.imageUrl })
        EditorialBentoWidget().updateAll(context)
    }
}
