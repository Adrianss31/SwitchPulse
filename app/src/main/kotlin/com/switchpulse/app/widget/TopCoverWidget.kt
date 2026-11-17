package com.switchpulse.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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

class TopCoverWidget : GlanceAppWidget() {

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
                ).take(4)

                val bitmaps = topDeals.mapIndexedNotNull { index, deal ->
                    val kind = if (index == 0) CoverBitmapCache.Kind.HERO else CoverBitmapCache.Kind.THUMB
                    container.repository.coverCache
                        .loadCachedBitmap(deal.id, kind)
                        ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
                        ?.let { deal.id to it }
                }.toMap()

                TopCoverSnapshot(deals = topDeals, bitmaps = bitmaps)
            }.getOrElse {
                TopCoverSnapshot(deals = emptyList(), bitmaps = emptyMap())
            }
        }

        provideContent {
            GlanceTheme {
                TopCoverWidgetContent(context = context, snapshot = snapshot)
            }
        }
    }
}

private data class TopCoverSnapshot(
    val deals: List<Deal>,
    val bitmaps: Map<String, Bitmap>
)

@Composable
private fun TopCoverWidgetContent(
    context: Context,
    snapshot: TopCoverSnapshot
) {
    if (snapshot.deals.isEmpty()) {
        WidgetEmptyBox(context = context)
        return
    }

    val widgetSize = LocalSize.current
    val compactHeightOnlyHero = (widgetSize.height > 0.dp && widgetSize.height < 220.dp) || snapshot.deals.size == 1
    val bottomColumnsCount = if (widgetSize.width > 0.dp && widgetSize.width < 245.dp) 2 else 3
    val hero = snapshot.deals.first()
    val secondary = snapshot.deals.drop(1).take(bottomColumnsCount)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetPalette.paper)
            .cornerRadius(24.dp)
    ) {
        // Top half: Protagonist cover #01
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .then(
                    if (compactHeightOnlyHero) GlanceModifier.fillMaxSize()
                    else GlanceModifier.defaultWeight()
                )
                .background(WidgetPalette.coverFallback)
                .clickable(actionStartActivity(openDealDetailIntent(context, hero.id)))
        ) {
            val heroBitmap = snapshot.bitmaps[hero.id]?.takeIf { !it.isRecycled }
            if (heroBitmap != null) {
                Image(
                    provider = ImageProvider(heroBitmap),
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize()
                )
            }

            Image(
                provider = ImageProvider(R.drawable.widget_scrim_bottom),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize()
            )

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            ) {
                // Top bar inside cover: "Top 10 eShop" paper pill + Refresh button
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .height(26.dp)
                            .cornerRadius(13.dp)
                            .background(WidgetPalette.paper)
                            .clickable(actionStartActivity(openAppIntent(context)))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.widget_pill_top10),
                            style = TextStyle(
                                color = WidgetPalette.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
                            .clickable(actionRunCallback<RefreshDealsAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = context.getString(R.string.action_refresh),
                            colorFilter = ColorFilter.tint(WidgetPalette.white),
                            modifier = GlanceModifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Bottom overlay: "01" + Title + Expiry + Price & −XX% badge
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "01",
                        style = TextStyle(
                            color = WidgetPalette.white,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = hero.title,
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        val expiry = DealFilterEngine.formatExpiryShort(hero.endsAtIso)
                        if (!expiry.isNullOrBlank()) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = expiry,
                                style = TextStyle(
                                    color = WidgetPalette.heroExpiryTint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = DealFilterEngine.formatEur(hero.discountedPriceEur),
                            style = TextStyle(
                                color = WidgetPalette.white,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        if (hero.discountPercent > 0) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(3.dp)
                                    .background(WidgetPalette.accent)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "−${hero.discountPercent}%",
                                    style = TextStyle(
                                        color = WidgetPalette.white,
                                        fontSize = 11.5.sp,
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

        // Bottom half: 2 or 3 equal columns for ranks #02, #03, #04
        if (!compactHeightOnlyHero && secondary.isNotEmpty()) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                secondary.forEachIndexed { idx, deal ->
                    if (idx > 0) Spacer(modifier = GlanceModifier.width(10.dp))
                    SecondaryPodiumColumn(
                        context = context,
                        rank = idx + 2,
                        deal = deal,
                        bitmap = snapshot.bitmaps[deal.id]?.takeIf { !it.isRecycled },
                        fallbackIndex = idx,
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryPodiumColumn(
    context: Context,
    rank: Int,
    deal: Deal,
    bitmap: Bitmap?,
    fallbackIndex: Int,
    modifier: ModifierOrGlance = GlanceModifier
) {
    Column(
        modifier = modifier
            .clickable(actionStartActivity(openDealDetailIntent(context, deal.id)))
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(54.dp)
                .cornerRadius(4.dp)
                .background(WidgetPalette.tileFallbacks[fallbackIndex % WidgetPalette.tileFallbacks.size])
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = deal.title,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(4.dp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .background(WidgetPalette.paper)
                    .cornerRadius(3.dp)
                    .padding(start = 2.dp, end = 5.dp, top = 1.dp, bottom = 2.dp)
            ) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    style = TextStyle(
                        color = WidgetPalette.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(5.dp))
        Text(
            text = deal.title,
            style = TextStyle(
                color = WidgetPalette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(1.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                style = TextStyle(
                    color = WidgetPalette.ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (deal.discountPercent > 0) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "−${deal.discountPercent}%",
                    style = TextStyle(
                        color = WidgetPalette.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

private typealias ModifierOrGlance = GlanceModifier

class TopCoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopCoverWidget()
}
