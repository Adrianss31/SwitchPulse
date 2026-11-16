package com.switchpulse.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SpPalette(
    val paper: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val border: Color,
    val track: Color,
    val starOff: Color,
    val starDim: Color,
    val pressed: Color,
    val bannerBg: Color,
    val accent: Color,
    val accentText: Color,
    val urgent: Color,
    val flash: Color,
    val snackbar: Color,
    val snackbarText: Color,
    val snackbarAction: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightSpPalette = SpPalette(
    paper = Color(0xFFFAFAF7),
    ink = Color(0xFF111111),
    ink2 = Color(0xFF2B2B28),
    muted = Color(0xFF5B5B57),
    faint = Color(0xFF6E6E69),
    line = Color(0xFFE4E3DD),
    border = Color(0xFFD9D8D1),
    track = Color(0xFFECEBE5),
    starOff = Color(0xFF9A998F),
    starDim = Color(0xFFCFCEC6),
    pressed = Color(0xFFEFEEE8),
    bannerBg = Color(0xFFEFEEE9),
    accent = Color(0xFFD7261E),
    accentText = Color(0xFFC0261C),
    urgent = Color(0xFFA3290F),
    flash = Color(0xFFF9D9D3),
    snackbar = Color(0xFF111111),
    snackbarText = Color(0xFFFAFAF7),
    snackbarAction = Color(0xFFFFB3A6),
    scrim = Color(0x73111111),
    isDark = false
)

val DarkSpPalette = SpPalette(
    paper = Color(0xFF141413),
    ink = Color(0xFFF2F1EC),
    ink2 = Color(0xFFD9D8D1),
    muted = Color(0xFFA3A29A),
    faint = Color(0xFF8C8B84),
    line = Color(0xFF2A2926),
    border = Color(0xFF3A3935),
    track = Color(0xFF2A2926),
    starOff = Color(0xFF6E6D66),
    starDim = Color(0xFF57564F),
    pressed = Color(0xFF1F1E1C),
    bannerBg = Color(0xFF1F1E1C),
    accent = Color(0xFFFF5A47),
    accentText = Color(0xFFFF7A68),
    urgent = Color(0xFFFF8A66),
    flash = Color(0xFF4A1F1A),
    snackbar = Color(0xFFF2F1EC),
    snackbarText = Color(0xFF141413),
    snackbarAction = Color(0xFFC0261C),
    scrim = Color(0x73111111),
    isDark = true
)

val LocalSpColors = staticCompositionLocalOf { LightSpPalette }
