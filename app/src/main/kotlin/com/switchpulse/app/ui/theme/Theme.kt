package com.switchpulse.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.switchpulse.app.ui.motion.LocalReduceMotion

// Legacy compatibility color aliases kept until unused callers are removed in Phase 7
val EshopCrimson = Color(0xFFD7261E)
val EshopCrimsonSoft = Color(0xFFFF7A68)

private val EditorialLightColorScheme = lightColorScheme(
    primary = LightSpPalette.accent,
    onPrimary = Color.White,
    primaryContainer = LightSpPalette.flash,
    onPrimaryContainer = LightSpPalette.ink,
    secondary = LightSpPalette.ink,
    onSecondary = LightSpPalette.paper,
    secondaryContainer = LightSpPalette.bannerBg,
    onSecondaryContainer = LightSpPalette.ink,
    background = LightSpPalette.paper,
    onBackground = LightSpPalette.ink,
    surface = LightSpPalette.paper,
    onSurface = LightSpPalette.ink,
    surfaceVariant = LightSpPalette.track,
    onSurfaceVariant = LightSpPalette.muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSpPalette.paper,
    surfaceContainer = LightSpPalette.bannerBg,
    surfaceContainerHigh = LightSpPalette.track,
    surfaceContainerHighest = LightSpPalette.border,
    outline = LightSpPalette.border,
    outlineVariant = LightSpPalette.line,
    error = LightSpPalette.urgent,
    onError = Color.White
)

private val EditorialDarkColorScheme = darkColorScheme(
    primary = DarkSpPalette.accent,
    onPrimary = Color.White,
    primaryContainer = DarkSpPalette.flash,
    onPrimaryContainer = DarkSpPalette.ink,
    secondary = DarkSpPalette.ink,
    onSecondary = DarkSpPalette.paper,
    secondaryContainer = DarkSpPalette.bannerBg,
    onSecondaryContainer = DarkSpPalette.ink,
    background = DarkSpPalette.paper,
    onBackground = DarkSpPalette.ink,
    surface = DarkSpPalette.paper,
    onSurface = DarkSpPalette.ink,
    surfaceVariant = DarkSpPalette.track,
    onSurfaceVariant = DarkSpPalette.muted,
    surfaceContainerLowest = DarkSpPalette.paper,
    surfaceContainerLow = DarkSpPalette.pressed,
    surfaceContainer = DarkSpPalette.bannerBg,
    surfaceContainerHigh = DarkSpPalette.track,
    surfaceContainerHighest = DarkSpPalette.border,
    outline = DarkSpPalette.border,
    outlineVariant = DarkSpPalette.line,
    error = DarkSpPalette.urgent,
    onError = DarkSpPalette.paper
)

private val EditorialMaterialTypography = Typography(
    titleLarge = SpType.cardTitle,
    titleMedium = SpType.rowTitle,
    bodyMedium = SpType.body,
    bodySmall = SpType.caption,
    labelMedium = SpType.captionBold
)

object SpTheme {
    val colors: SpPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSpColors.current
}

@Composable
fun SwitchPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val spPalette = if (darkTheme) DarkSpPalette else LightSpPalette
    val colorScheme = if (darkTheme) EditorialDarkColorScheme else EditorialLightColorScheme

    CompositionLocalProvider(
        LocalSpColors provides spPalette,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EditorialMaterialTypography,
            content = content
        )
    }
}
