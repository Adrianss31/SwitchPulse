package com.switchpulse.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.switchpulse.app.R
import com.switchpulse.app.SwitchPulseApp
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.model.Deal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class CarouselWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            runCatching {
                val container = SwitchPulseApp.containerFrom(context)
                val settings = container.preferences.getSettings()
                val rawDeals = container.preferences.getCachedDeals()
                val topDeals = DealFilterEngine.filterTopTen(
                    rawDeals = rawDeals,
                    settings = settings,
                    platformOverride = settings.widgetPlatformFilter,
                    limit = 10
                )
                val cache = container.repository.coverCache
                val mainBitmaps = topDeals.mapNotNull { deal ->
                    cache.loadCachedBitmap(deal.id, CoverBitmapCache.Kind.TALL_MAIN)
                        ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                        ?.let { deal.id to it }
                }.toMap()
                val sideBitmaps = topDeals.mapNotNull { deal ->
                    cache.loadCachedBitmap(deal.id, CoverBitmapCache.Kind.TALL_SIDE)
                        ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                        ?.let { deal.id to it }
                }.toMap()
                CarouselSnapshot(
                    deals = topDeals,
                    mainBitmaps = mainBitmaps,
                    sideBitmaps = sideBitmaps
                )
            }.getOrElse {
                CarouselSnapshot(deals = emptyList(), mainBitmaps = emptyMap(), sideBitmaps = emptyMap())
            }
        }

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val activeRank = prefs[KEY_ACTIVE_RANK] ?: 1
                CarouselWidgetContent(
                    context = context,
                    snapshot = snapshot,
                    activeRank = activeRank
                )
            }
        }
    }

    companion object {
        val KEY_ACTIVE_RANK = intPreferencesKey("active_rank")
    }
}

private data class CarouselSnapshot(
    val deals: List<Deal>,
    val mainBitmaps: Map<String, Bitmap>,
    val sideBitmaps: Map<String, Bitmap>
)

@Composable
private fun CarouselWidgetContent(
    context: Context,
    snapshot: CarouselSnapshot,
    activeRank: Int
) {
    if (snapshot.deals.isEmpty()) {
        WidgetEmptyBox(context = context)
        return
    }

    val total = snapshot.deals.size
    val clampedActive = activeRank.coerceIn(1, total)
    val slotRanks = carouselSlots(clampedActive, total)
    val totalWidth = LocalSize.current.width
    val bigPanelWidth = if (totalWidth > 120.dp) (totalWidth * 0.57f).coerceAtLeast(120.dp) else 160.dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetPalette.carouselBg)
            .cornerRadius(24.dp)
    ) {
        slotRanks.forEachIndexed { idx, rank ->
            val deal = snapshot.deals[rank - 1]
            val isMain = rank == clampedActive
            val bitmap = (if (isMain) snapshot.mainBitmaps[deal.id] else snapshot.sideBitmaps[deal.id])
                ?.takeIf { !it.isRecycled }
            if (idx > 0) {
                Spacer(modifier = GlanceModifier.width(2.dp))
            }

            if (isMain) {
                Box(
                    modifier = GlanceModifier
                        .width(bigPanelWidth)
                        .fillMaxHeight()
                        .background(WidgetPalette.coverFallback)
                        .semantics {
                            contentDescription = "Apri ${deal.title} nell'app"
                        }
                        .clickable(actionStartActivity(openDealDetailIntent(context, deal.id)))
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = deal.title,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }
                    Image(
                        provider = ImageProvider(R.drawable.widget_carousel_scrim_main),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 11.dp)
                    ) {
                        Text(
                            text = rank.toString().padStart(2, '0'),
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = deal.title,
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                                style = TextStyle(
                                    color = WidgetPalette.white,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            if (deal.discountPercent > 0) {
                                Spacer(modifier = GlanceModifier.width(7.dp))
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(3.dp)
                                        .background(WidgetPalette.accent)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "−${deal.discountPercent}%",
                                        style = TextStyle(
                                            color = WidgetPalette.white,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val directionLabel = if (rank < clampedActive) {
                    "Offerta precedente: n. $rank, ${deal.title}"
                } else {
                    "Offerta successiva: n. $rank, ${deal.title}"
                }
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(WidgetPalette.tileFallbacks[(rank - 1) % WidgetPalette.tileFallbacks.size])
                        .semantics { contentDescription = directionLabel }
                        .clickable(
                            actionRunCallback<CarouselStepAction>(
                                actionParametersOf(CarouselStepAction.KEY_RANK to rank)
                            )
                        )
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = deal.title,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }
                    Image(
                        provider = ImageProvider(R.drawable.widget_carousel_scrim_side),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 6.dp, top = 10.dp, bottom = 11.dp)
                    ) {
                        Text(
                            text = rank.toString().padStart(2, '0'),
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = String.format(Locale.ITALY, "%.2f", deal.discountedPriceEur),
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        if (deal.discountPercent > 0) {
                            Spacer(modifier = GlanceModifier.height(1.dp))
                            Text(
                                text = "−${deal.discountPercent}%",
                                style = TextStyle(
                                    color = WidgetPalette.carouselSmallOff,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

class CarouselStepAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val nextRank = parameters[KEY_RANK] ?: return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[CarouselWidget.KEY_ACTIVE_RANK] = nextRank.coerceIn(1, 10)
            }
        }
        CarouselWidget().update(context, glanceId)
    }

    companion object {
        val KEY_RANK = ActionParameters.Key<Int>("rank")
    }
}

class CarouselWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarouselWidget()
}
