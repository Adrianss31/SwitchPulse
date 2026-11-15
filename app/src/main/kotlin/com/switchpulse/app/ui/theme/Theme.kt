package com.switchpulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EshopCrimson = Color(0xFFE60012)
val EshopCrimsonSoft = Color(0xFFFF7A85)

private val CeramicLightColors = lightColorScheme(
    primary = EshopCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF534341),
    onSecondary = Color.White,
    background = Color(0xFFF7F5F2),
    onBackground = Color(0xFF141416),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF141416),
    surfaceVariant = Color(0xFFECE9E4),
    onSurfaceVariant = Color(0xFF64646B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3EF),
    surfaceContainer = Color(0xFFEFEDE8),
    surfaceContainerHigh = Color(0xFFE8E5DF),
    outline = Color(0x22141416),
    outlineVariant = Color(0x14141416)
)

private val ObsidianDarkColors = darkColorScheme(
    primary = EshopCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A0007),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFD8C2BF),
    onSecondary = Color(0xFF3B2C2A),
    background = Color(0xFF121318),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF181A20),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF262932),
    onSurfaceVariant = Color(0xFF9A9AA3),
    surfaceContainerLowest = Color(0xFF0E1014),
    surfaceContainerLow = Color(0xFF181A20),
    surfaceContainer = Color(0xFF1C1E25),
    surfaceContainerHigh = Color(0xFF252832),
    outline = Color(0x24FFFFFF),
    outlineVariant = Color(0x14FFFFFF)
)

private val SwitchPulseTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
)

@Composable
fun SwitchPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) ObsidianDarkColors else CeramicLightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = SwitchPulseTypography,
        content = content
    )
}
