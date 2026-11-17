package com.switchpulse.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.theme.Bricolage
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.delay

enum class CutoutCorner {
    TopStart,
    BottomStart
}

enum class PriceSize {
    S,
    M,
    L
}

private val CoverFallbackSwatches = listOf(
    Color(0xFF6B5537),
    Color(0xFF34407A),
    Color(0xFF7A2C4B),
    Color(0xFF2C394B),
    Color(0xFF3C5A6B),
    Color(0xFF5E7D3A),
    Color(0xFF5B4A86),
    Color(0xFF9C7A45)
)

private fun fallbackColorFor(title: String): Color {
    val idx = (title.hashCode() and 0x7FFFFFFF) % CoverFallbackSwatches.size
    return CoverFallbackSwatches[idx]
}

private fun initialsFor(title: String): String {
    val words = title.trim()
        .split(Regex("\\s+"))
        .filter { it.any(Char::isLetterOrDigit) }
    return when {
        words.isEmpty() -> "SP"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words[0].first()}${words[1].first()}".uppercase()
    }
}

@Composable
fun Cover(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    aspect: Float = 16f / 9f,
    radius: Dp = 4.dp
) {
    val shape: Shape = RoundedCornerShape(radius)
    val bgColor = remember(title) { fallbackColorFor(title) }
    val initials = remember(title) { initialsFor(title) }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(shape)
            .background(bgColor),
        contentAlignment = Alignment.BottomEnd
    ) {
        val showInitials = this@BoxWithConstraints.maxWidth >= 140.dp
        val fallbackContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (showInitials) {
                    Text(
                        text = initials,
                        style = SpType.screenTitle.copy(
                            fontFamily = Bricolage,
                            fontSize = if (this@BoxWithConstraints.maxWidth > 260.dp) 64.sp else 36.sp,
                            color = Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier.padding(end = 14.dp, bottom = 6.dp)
                    )
                }
            }
        }

        if (url.isNullOrBlank()) {
            fallbackContent()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                fallbackContent()
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun RankCutout(
    rank: Int,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    corner: CutoutCorner = CutoutCorner.TopStart,
    previousRank: Int? = null
) {
    val c = SpTheme.colors
    val innerCornerRadius = if (fontSize.value >= 40f) 4.dp else 3.dp
    val shape = when (corner) {
        CutoutCorner.TopStart -> RoundedCornerShape(bottomEnd = innerCornerRadius)
        CutoutCorner.BottomStart -> RoundedCornerShape(topEnd = innerCornerRadius)
    }
    val paddingModifier = when {
        corner == CutoutCorner.BottomStart -> Modifier.padding(top = 10.dp, end = 12.dp, start = 20.dp)
        fontSize.value >= 40f -> Modifier.padding(top = 4.dp, end = 10.dp, bottom = 8.dp)
        else -> Modifier.padding(top = 2.dp, end = 7.dp, bottom = 5.dp)
    }

    val style = when {
        fontSize.value >= 40f -> SpType.rankHero.copy(fontSize = fontSize)
        fontSize.value >= 28f -> SpType.rankPodium.copy(fontSize = fontSize)
        else -> SpType.rankRow.copy(fontSize = fontSize)
    }

    Box(
        modifier = modifier
            .background(c.paper, shape)
            .then(paddingModifier)
    ) {
        RankNumber(
            rank = rank,
            style = style,
            color = color,
            previousRank = previousRank
        )
    }
}

@Composable
fun RankNumber(
    rank: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    previousRank: Int? = null
) {
    val formatted = rank.coerceAtLeast(1).toString().padStart(2, '0')
    val movingUp = previousRank == null || rank <= previousRank
    RollingDigitsText(
        value = formatted,
        style = style,
        color = color,
        movingUp = movingUp,
        modifier = modifier
    )
}

@Composable
fun RollingDigitsText(
    value: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    movingUp: Boolean = true
) {
    val reduceMotion = LocalReduceMotion.current
    val chars = value.toList()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        chars.forEachIndexed { index, ch ->
            val key = chars.size - index // align right-to-left place values
            if (reduceMotion) {
                Text(text = ch.toString(), style = style, color = color, maxLines = 1)
            } else {
                AnimatedContent(
                    targetState = ch to key,
                    transitionSpec = {
                        val enterSlide = slideInVertically(
                            animationSpec = Motion.standard(),
                            initialOffsetY = { fullHeight -> if (movingUp) fullHeight / 2 else -fullHeight / 2 }
                        ) + fadeIn(animationSpec = Motion.standard())
                        val exitSlide = slideOutVertically(
                            animationSpec = Motion.standard(),
                            targetOffsetY = { fullHeight -> if (movingUp) -fullHeight / 2 else fullHeight / 2 }
                        ) + fadeOut(animationSpec = Motion.quick())
                        enterSlide togetherWith exitSlide
                    },
                    label = "digit_roll_$key"
                ) { (charValue, _) ->
                    Text(text = charValue.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun RankDelta(
    delta: Int?,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    var showDelta by remember(delta) { mutableStateOf(reduceMotion && delta != null && delta != 0) }
    LaunchedEffect(delta, reduceMotion) {
        if (delta == null || delta == 0) {
            showDelta = false
        } else if (reduceMotion) {
            showDelta = true
        } else {
            showDelta = false
            delay(320L)
            showDelta = true
        }
    }
    AnimatedVisibility(
        visible = showDelta && delta != null && delta != 0,
        enter = fadeIn(Motion.quick()),
        exit = fadeOut(Motion.quick()),
        modifier = modifier
    ) {
        val d = delta ?: 0
        val arrow = if (d > 0) "▲ ${d}" else "▼ ${-d}"
        Text(
            text = arrow,
            style = SpType.captionBold.copy(fontSize = 10.5.sp),
            color = if (d > 0) c.ink else c.faint,
            maxLines = 1
        )
    }
}

@Composable
fun DiscountTag(
    discountPercent: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val c = SpTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(c.accent)
            .padding(
                horizontal = if (compact) 6.dp else 7.dp,
                vertical = if (compact) 2.dp else 3.dp
            )
    ) {
        Text(
            text = "−${discountPercent}%",
            style = SpType.captionBold.copy(
                fontSize = if (compact) 12.sp else 13.sp,
                color = Color.White,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1
        )
    }
}

@Composable
fun PriceLine(
    discountedPriceEur: Double,
    regularPriceEur: Double?,
    discountPercent: Int,
    size: PriceSize,
    modifier: Modifier = Modifier,
    previousDiscountedPriceEur: Double? = null
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val priceDropped = previousDiscountedPriceEur != null &&
        previousDiscountedPriceEur - discountedPriceEur >= 0.01

    var flashing by remember(discountedPriceEur, previousDiscountedPriceEur) {
        mutableStateOf(false)
    }
    LaunchedEffect(priceDropped) {
        if (priceDropped && !reduceMotion) {
            delay(320L)
            flashing = true
            delay(1200L)
            flashing = false
        }
    }
    val flashBg by animateColorAsState(
        targetValue = if (flashing) c.flash else Color.Transparent,
        animationSpec = if (flashing) Motion.quick() else androidx.compose.animation.core.tween(1200, easing = Motion.Out),
        label = "price_drop_flash"
    )

    val priceText = DealFilterEngine.formatEur(discountedPriceEur)
    val fullText = regularPriceEur?.takeIf { it > discountedPriceEur }?.let { DealFilterEngine.formatEur(it) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(flashBg)
            .padding(horizontal = if (flashing) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (size == PriceSize.L) 10.dp else 6.dp)
    ) {
        when (size) {
            PriceSize.L -> {
                Text(text = priceText, style = SpType.priceL, color = c.ink, maxLines = 1)
                if (fullText != null) {
                    Text(
                        text = fullText,
                        style = SpType.body.copy(fontSize = 15.sp, textDecoration = TextDecoration.LineThrough),
                        color = c.faint,
                        maxLines = 1
                    )
                }
                if (discountPercent > 0) {
                    DiscountTag(discountPercent = discountPercent, compact = false)
                }
            }
            PriceSize.M -> {
                Text(text = priceText, style = SpType.priceM, color = c.ink, maxLines = 1)
                if (discountPercent > 0) {
                    DiscountTag(discountPercent = discountPercent, compact = true)
                }
            }
            PriceSize.S -> {
                Text(text = priceText, style = SpType.rowPrice, color = c.ink, maxLines = 1)
                if (discountPercent > 0) {
                    Text(
                        text = "−${discountPercent}%",
                        style = SpType.captionBold.copy(fontFeatureSettings = "tnum"),
                        color = c.accentText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MatchLine(
    score: Int?,
    reason: String?,
    sentiment: DealMatchSentiment,
    modifier: Modifier = Modifier,
    expirySuffix: String? = null,
    isUrgentExpiry: Boolean = false,
    singleLine: Boolean = true
) {
    val c = SpTheme.colors
    if (sentiment == DealMatchSentiment.DISCOURAGED && score == null) {
        Text(
            text = stringResource(R.string.ranking_not_for_you),
            style = SpType.captionBold.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = c.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    val text = buildAnnotatedString {
        if (sentiment == DealMatchSentiment.DISCOURAGED) {
            withStyle(SpanStyle(color = c.faint, fontWeight = FontWeight.SemiBold)) {
                append(stringResource(R.string.ranking_not_for_you))
            }
        } else {
            if (score != null) {
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) {
                    append("${stringResource(R.string.detail_for_you_label)} $score")
                }
            }
            if (!reason.isNullOrBlank()) {
                if (score != null) {
                    withStyle(SpanStyle(color = c.ink2)) { append(" — ") }
                }
                withStyle(
                    SpanStyle(
                        color = if (score != null) c.ink2 else c.muted,
                        fontWeight = if (score != null) FontWeight.Normal else FontWeight.SemiBold
                    )
                ) {
                    append(reason)
                }
            }
        }
        if (!expirySuffix.isNullOrBlank()) {
            if (length > 0) append(" · ")
            withStyle(
                SpanStyle(
                    color = if (isUrgentExpiry) c.urgent else c.muted,
                    fontWeight = if (isUrgentExpiry) FontWeight.SemiBold else FontWeight.Normal
                )
            ) {
                append(expirySuffix.lowercase())
            }
        }
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = SpType.caption.copy(fontSize = if (singleLine) 12.sp else 13.sp, lineHeight = 18.sp),
            maxLines = if (singleLine) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
