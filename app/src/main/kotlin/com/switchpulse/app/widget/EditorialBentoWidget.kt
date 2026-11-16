package com.switchpulse.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import com.switchpulse.app.MainActivity
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.SwitchPulseApp
import com.switchpulse.app.data.local.CoverBitmapCache
import com.switchpulse.app.data.local.PreferencesStore
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditorialBentoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = PreferencesStore(context)
        val coverCache = SwitchPulseApp.containerFrom(context).repository.coverCache

        val snapshot = withContext(Dispatchers.IO) {
            val settings = prefs.getSettings()
            val rawDeals = prefs.getCachedDeals()
            val topDeals = DealFilterEngine.filterTopTen(
                rawDeals = rawDeals,
                settings = settings,
                platformOverride = settings.widgetPlatformFilter,
                limit = 10
            )
            val bitmaps = topDeals.mapIndexedNotNull { index, deal ->
                coverCache.loadCachedBitmap(deal.id, hero = index == 0)?.let { deal.id to it }
            }.toMap()

            WidgetSnapshot(
                deals = topDeals,
                platformFilter = settings.widgetPlatformFilter,
                lastSyncEpochMs = settings.lastSyncEpochMs,
                coverBitmaps = bitmaps
            )
        }

        provideContent {
            GlanceTheme {
                EditorialBentoContent(context = context, snapshot = snapshot)
            }
        }
    }
}

private data class WidgetSnapshot(
    val deals: List<Deal>,
    val platformFilter: PlatformFilter,
    val lastSyncEpochMs: Long,
    val coverBitmaps: Map<String, android.graphics.Bitmap>
)

private object BentoPalette {
    val surface = ColorProvider(day = Color(0xFFFAF9F7), night = Color(0xFF181A20))
    val primaryText = ColorProvider(day = Color(0xFF141416), night = Color(0xFFF4F4F5))
    val secondaryText = ColorProvider(day = Color(0xFF64646B), night = Color(0xFF9A9AA3))
    val divider = ColorProvider(day = Color(0x14141416), night = Color(0x16FFFFFF))
    val segTrack = ColorProvider(day = Color(0x10141416), night = Color(0x14FFFFFF))
    val segSelected = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF2B2E38))
    val crimson = ColorProvider(day = Color(0xFFE60012), night = Color(0xFFE60012))
    val sw2Accent = ColorProvider(day = Color(0xFFC80010), night = Color(0xFFFF7A85))
    val heroFallback = ColorProvider(day = Color(0xFF27272E), night = Color(0xFF242731))
    val white = ColorProvider(day = Color.White, night = Color.White)
    val whiteMuted = ColorProvider(day = Color(0xCCFFFFFF), night = Color(0xCCFFFFFF))
    val darkScrimPill = ColorProvider(day = Color(0x99000000), night = Color(0x99000000))
}

@Composable
private fun EditorialBentoContent(
    context: Context,
    snapshot: WidgetSnapshot
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BentoPalette.surface)
            .cornerRadius(26.dp)
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(BentoPalette.crimson)
                ) {}
                Spacer(modifier = GlanceModifier.width(7.dp))
                Text(
                    text = "Top 10 eShop",
                    style = TextStyle(
                        color = BentoPalette.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }

            // Segmented platform selector
            Row(
                modifier = GlanceModifier
                    .background(BentoPalette.segTrack)
                    .cornerRadius(14.dp)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformFilter.entries.forEach { option ->
                    val selected = snapshot.platformFilter == option
                    Box(
                        modifier = GlanceModifier
                            .then(
                                if (selected) {
                                    GlanceModifier.background(BentoPalette.segSelected).cornerRadius(12.dp)
                                } else {
                                    GlanceModifier
                                }
                            )
                            .clickable(
                                actionRunCallback<SelectWidgetPlatformAction>(
                                    actionParametersOf(SelectWidgetPlatformAction.KEY_PLATFORM to option.name)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            style = TextStyle(
                                color = if (selected) BentoPalette.primaryText else BentoPalette.secondaryText,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Refresh icon button
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
                    .cornerRadius(13.dp)
                    .background(BentoPalette.segTrack)
                    .clickable(actionRunCallback<RefreshDealsAction>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Aggiorna offerte",
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(BentoPalette.primaryText)
                )
            }
        }

        // Body
        if (snapshot.deals.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionRunCallback<RefreshDealsAction>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (snapshot.lastSyncEpochMs == 0L) {
                        "Tocca per scaricare le offerte"
                    } else {
                        "Nessuna offerta con questi filtri"
                    },
                    style = TextStyle(
                        color = BentoPalette.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = "Aggiorna ora",
                    style = TextStyle(
                        color = BentoPalette.sw2Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            val hero = snapshot.deals.first()
            val rest = snapshot.deals.drop(1)

            HeroDealCard(
                deal = hero,
                bitmap = snapshot.coverBitmaps[hero.id]
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                itemsIndexed(rest) { index, deal ->
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        DealListRow(
                            rank = index + 2,
                            deal = deal,
                            bitmap = snapshot.coverBitmaps[deal.id]
                        )
                        if (index < rest.lastIndex) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BentoPalette.divider)
                            ) {}
                        }
                    }
                }
            }
        }

        // Footer
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BentoPalette.divider)
        ) {}
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSyncTime(snapshot.lastSyncEpochMs),
                style = TextStyle(
                    color = BentoPalette.secondaryText,
                    fontSize = 11.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "eShop Italia",
                style = TextStyle(
                    color = BentoPalette.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun HeroDealCard(
    deal: Deal,
    bitmap: android.graphics.Bitmap?
) {
    val context = androidx.glance.LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(108.dp)
            .cornerRadius(18.dp)
            .background(BentoPalette.heroFallback)
            .clickable(actionStartActivity(openDealDetailIntent(context, deal.id)))
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = deal.title,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp),
                contentScale = ContentScale.Crop
            )
        }
        Image(
            provider = ImageProvider(R.drawable.hero_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp),
            contentScale = ContentScale.FillBounds
        )

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(10.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(BentoPalette.darkScrimPill)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "#1 · ${deal.platform.displayName}",
                        style = TextStyle(
                            color = BentoPalette.white,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Box(
                    modifier = GlanceModifier
                        .background(BentoPalette.crimson)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "-${deal.discountPercent}%",
                        style = TextStyle(
                            color = BentoPalette.white,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = GlanceModifier.defaultWeight().padding(end = 8.dp)) {
                    Text(
                        text = deal.title,
                        style = TextStyle(
                            color = BentoPalette.white,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    val expiry = DealFilterEngine.formatExpiryShort(deal.endsAtIso)
                    val sub = if (expiry != null) "${deal.publisher} · $expiry" else deal.publisher
                    Text(
                        text = sub,
                        style = TextStyle(
                            color = BentoPalette.whiteMuted,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = DealFilterEngine.formatEur(deal.regularPriceEur),
                        style = TextStyle(
                            color = BentoPalette.whiteMuted,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                        style = TextStyle(
                            color = BentoPalette.white,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DealListRow(
    rank: Int,
    deal: Deal,
    bitmap: android.graphics.Bitmap?
) {
    val context = androidx.glance.LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(openDealDetailIntent(context, deal.id)))
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            style = TextStyle(
                color = BentoPalette.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.width(20.dp)
        )

        Box(
            modifier = GlanceModifier
                .width(42.dp)
                .height(30.dp)
                .cornerRadius(7.dp)
                .background(BentoPalette.heroFallback),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = deal.title,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(7.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight().padding(end = 6.dp)) {
            Text(
                text = deal.title,
                style = TextStyle(
                    color = BentoPalette.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            val urgent = if (DealFilterEngine.isUrgentExpiry(deal.endsAtIso)) {
                DealFilterEngine.formatExpiryShort(deal.endsAtIso)
            } else {
                null
            }
            val meta = if (urgent != null) "${deal.platform.displayName} · $urgent" else deal.platform.displayName
            Text(
                text = meta,
                style = TextStyle(
                    color = if (deal.platform == Platform.SWITCH_2) BentoPalette.sw2Accent else BentoPalette.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                style = TextStyle(
                    color = BentoPalette.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(BentoPalette.crimson)
                    .cornerRadius(6.dp)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "-${deal.discountPercent}%",
                    style = TextStyle(
                        color = BentoPalette.white,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

private fun openDealDetailIntent(context: Context, dealId: String): Intent {
    return Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        data = Uri.parse("switchpulse://deal/$dealId")
        putExtra(MainActivity.EXTRA_OPEN_DEAL_ID, dealId)
    }
}

private fun formatSyncTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Mai aggiornato"
    val fmt = SimpleDateFormat("HH:mm", Locale.ITALY)
    return "Aggiornato alle ${fmt.format(Date(epochMs))}"
}

class SelectWidgetPlatformAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val raw = parameters[KEY_PLATFORM] ?: return
        val filter = runCatching { PlatformFilter.valueOf(raw) }.getOrNull() ?: return
        val repo = SwitchPulseApp.containerFrom(context).repository
        repo.preferences.updateWidgetPlatformFilter(filter)
        repo.notifyWidgetChanged()
        EditorialBentoWidget().updateAll(context)
    }

    companion object {
        val KEY_PLATFORM = ActionParameters.Key<String>("platform")
    }
}

class RefreshDealsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        SyncScheduler.triggerImmediate(context)
    }
}
