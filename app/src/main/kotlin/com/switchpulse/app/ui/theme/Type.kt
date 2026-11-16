package com.switchpulse.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("wdth", width),
        FontVariation.Setting("opsz", 48f)
    )
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight)
    )
)

val BricolageCondensed = FontFamily(bricolage(800, 75f))
val BricolageSemi = FontFamily(bricolage(800, 80f))
val Bricolage = FontFamily(bricolage(700, 100f), bricolage(800, 100f))
val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold)
)

object SpType {
    val screenTitle = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).em
    )

    val sheetTitle = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em
    )

    val detailTitle = TextStyle(
        fontFamily = BricolageSemi,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.025).em
    )

    val rankHero = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = "tnum"
    )

    val rankPodium = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum"
    )

    val rankRow = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum"
    )

    val score = TextStyle(
        fontFamily = BricolageCondensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum"
    )

    val cardTitle = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.015).em
    )

    val priceL = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum"
    )

    val priceM = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    )

    val body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    )

    val rowTitle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 19.sp
    )

    val rowPrice = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum"
    )

    val label = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.06.em
    )

    val caption = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 16.sp
    )

    val captionBold = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val tab = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    val navLabel = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 14.sp
    )
}
