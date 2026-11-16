package com.switchpulse.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.switchpulse.app.MainActivity
import com.switchpulse.app.R
import com.switchpulse.app.worker.SyncScheduler

object WidgetPalette {
    val paper = ColorProvider(day = Color(0xFFFAFAF7), night = Color(0xFF141413))
    val ink = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF2F1EC))
    val muted = ColorProvider(day = Color(0xFF5B5B57), night = Color(0xFFA3A29A))
    val accent = ColorProvider(day = Color(0xFFD7261E), night = Color(0xFFD7261E))
    val accentText = ColorProvider(day = Color(0xFFC0261C), night = Color(0xFFFF7A68))
    val white = ColorProvider(day = Color.White, night = Color.White)
    val heroExpiryTint = ColorProvider(day = Color(0xFFF1D9CF), night = Color(0xFFF1D9CF))
    val carouselSmallOff = ColorProvider(day = Color(0xFFFFC9BF), night = Color(0xFFFFC9BF))
    val carouselBg = ColorProvider(day = Color(0xFF111111), night = Color(0xFF111111))
    val coverFallback = ColorProvider(day = Color(0xFF6B5537), night = Color(0xFF3C342B))
    val tileFallbacks = listOf(
        ColorProvider(day = Color(0xFF34407A), night = Color(0xFF2B3566)),
        ColorProvider(day = Color(0xFFD98B3A), night = Color(0xFFB3702B)),
        ColorProvider(day = Color(0xFF7A2C4B), night = Color(0xFF64233C))
    )
}

fun openDealDetailIntent(context: Context, dealId: String): Intent {
    return Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        data = Uri.parse("switchpulse://deal/$dealId")
        putExtra(MainActivity.EXTRA_OPEN_DEAL_ID, dealId)
    }
}

fun openAppIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/**
 * Computes the visible 3-slot window [left, mid, right] for CarouselWidget (W12):
 * - rank 1: [1, 2, 3]
 * - rank 2..n-1: [r - 1, r, r + 1]
 * - rank n (e.g. 10): [n - 2, n - 1, n]
 */
fun carouselSlots(activeRank: Int, totalCount: Int): List<Int> {
    if (totalCount <= 0) return emptyList()
    if (totalCount <= 3) return (1..totalCount).toList()
    val clamped = activeRank.coerceIn(1, totalCount)
    return when {
        clamped <= 1 -> listOf(1, 2, 3)
        clamped >= totalCount -> listOf(totalCount - 2, totalCount - 1, totalCount)
        else -> listOf(clamped - 1, clamped, clamped + 1)
    }
}

@Composable
fun WidgetEmptyBox(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetPalette.paper)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(openAppIntent(context)))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(R.string.widget_empty_prompt),
            style = TextStyle(
                color = WidgetPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
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
