package com.switchpulse.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.switchpulse.app.R
import com.switchpulse.app.data.TasteGenreBar
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.motion.hapticConfirm
import com.switchpulse.app.ui.motion.pressScale
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

fun ratingLabelResId(rating: Int?): Int? = when (rating) {
    1 -> R.string.rating_1
    2 -> R.string.rating_2
    3 -> R.string.rating_3
    4 -> R.string.rating_4
    5 -> R.string.rating_5
    else -> null
}

@Composable
fun StarRating(
    rating: Int?,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 28.dp,
    touchSize: Dp = 44.dp
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    var pulseTrigger by remember { mutableIntStateOf(0) }
    val starScales = remember { List(5) { Animatable(1f) } }

    LaunchedEffect(pulseTrigger, rating) {
        if (pulseTrigger > 0 && rating != null && !reduceMotion) {
            for (i in 0 until rating) {
                launch {
                    delay(i * 45L)
                    starScales[i].snapTo(1f)
                    starScales[i].animateTo(1.3f, tween(130, easing = Motion.Out))
                    starScales[i].animateTo(1f, tween(190, easing = Motion.Out))
                }
            }
        }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..5) {
            val filled = rating != null && star <= rating
            val cd = if (star == 1) "1 stella" else "$star stelle"
            Box(
                modifier = Modifier
                    .size(touchSize)
                    .semantics { contentDescription = cd }
                    .clickable {
                        view.hapticConfirm()
                        if (rating == star) {
                            onRate(null)
                        } else {
                            pulseTrigger++
                            onRate(star)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val scale = starScales[star - 1].value
                Icon(
                    imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = null,
                    tint = if (filled) c.ink else c.starOff,
                    modifier = Modifier
                        .size(starSize)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
    }
}

@Composable
fun RatingPopover(
    title: String,
    currentRating: Int?,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val containerScale = remember { Animatable(if (reduceMotion) 1f else 0.85f) }
    val containerAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val starVisible = remember { List(5) { Animatable(if (reduceMotion) 1f else 0f) } }
    var isClosing by remember { mutableStateOf(false) }

    fun dismissAnimated(afterExit: () -> Unit) {
        if (isClosing) return
        isClosing = true
        if (reduceMotion) {
            afterExit()
            return
        }
        scope.launch {
            launch { containerScale.animateTo(0.86f, tween(140, easing = Motion.Away)) }
            launch { containerAlpha.animateTo(0f, tween(130, easing = Motion.Away)) }
        }.invokeOnCompletion { afterExit() }
    }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            launch { containerAlpha.animateTo(1f, tween(180, easing = Motion.Out)) }
            launch { containerScale.animateTo(1f, tween(240, easing = Motion.Out)) }
            for (i in 0..4) {
                launch {
                    delay(40L + i * 35L)
                    starVisible[i].animateTo(1f, Motion.quick())
                }
            }
        }
    }

    Dialog(onDismissRequest = { dismissAnimated(onDismiss) }) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.paper,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = containerScale.value
                    scaleY = containerScale.value
                    alpha = containerAlpha.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .shadow(20.dp, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.rating_popover_title, title),
                    style = SpType.rowTitle.copy(fontSize = 15.sp),
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (star in 1..5) {
                        val filled = currentRating != null && star <= currentRating
                        val alpha = starVisible[star - 1].value
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .graphicsLayer {
                                    this.alpha = alpha
                                    val s = 0.7f + 0.3f * alpha
                                    scaleX = s
                                    scaleY = s
                                }
                                .clickable {
                                    view.hapticConfirm()
                                    dismissAnimated { onRate(star) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = if (star == 1) "1 stella" else "$star stelle",
                                tint = if (filled) c.ink else c.starOff,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwnedToggleButton(
    owned: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }

    val fillFraction = remember { Animatable(if (owned) 1f else 0f) }
    val checkProgress = remember { Animatable(if (owned) 1f else 0f) }

    LaunchedEffect(owned) {
        if (reduceMotion) {
            fillFraction.snapTo(if (owned) 1f else 0f)
            checkProgress.snapTo(if (owned) 1f else 0f)
        } else if (owned) {
            launch { fillFraction.animateTo(1f, Motion.standard()) }
            launch {
                delay(120L)
                checkProgress.animateTo(1f, tween(260, easing = Motion.Out))
            }
        } else {
            launch { checkProgress.animateTo(0f, Motion.quick()) }
            launch { fillFraction.animateTo(0f, Motion.quick()) }
        }
    }

    val textColor by animateColorAsState(
        targetValue = if (owned) c.paper else c.ink,
        animationSpec = tween(durationMillis = if (reduceMotion) 80 else 160, delayMillis = if (owned && !reduceMotion) 80 else 0),
        label = "owned_text"
    )

    Box(
        modifier = modifier
            .height(height)
            .pressScale(interaction)
            .clip(RoundedCornerShape(4.dp))
            .border(BorderStroke(1.5.dp, c.ink), RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                view.hapticConfirm()
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fillFraction.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(c.ink)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            if (owned || checkProgress.value > 0.05f) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .drawWithContent {
                            val progress = checkProgress.value
                            if (progress > 0f) {
                                val fullPath = Path().apply {
                                    moveTo(size.width * 0.16f, size.height * 0.52f)
                                    lineTo(size.width * 0.42f, size.height * 0.78f)
                                    lineTo(size.width * 0.86f, size.height * 0.24f)
                                }
                                val measure = PathMeasure()
                                measure.setPath(fullPath, false)
                                val segment = Path()
                                measure.getSegment(0f, measure.length * progress, segment, true)
                                drawPath(
                                    path = segment,
                                    color = textColor,
                                    style = Stroke(
                                        width = 2.2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                )
            }
            Text(
                text = if (owned) {
                    stringResource(R.string.detail_owned_yes)
                } else {
                    stringResource(R.string.detail_owned_ask)
                },
                style = SpType.rowTitle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatTrio(
    owned: Int,
    backlog: Int,
    rated: Int,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(
                value = owned,
                label = stringResource(R.string.library_stat_owned),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp)
            )
            Box(modifier = Modifier.width(1.dp).height(46.dp).background(c.line))
            StatColumn(
                value = backlog,
                label = stringResource(R.string.library_stat_backlog),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 14.dp).padding(end = 0.dp)
            )
            Box(modifier = Modifier.width(1.dp).height(46.dp).background(c.line))
            StatColumn(
                value = rated,
                label = stringResource(R.string.library_stat_rated),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 14.dp)
            )
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun StatColumn(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    Column(modifier = modifier) {
        RollingDigitsText(
            value = value.toString(),
            style = SpType.sheetTitle.copy(fontSize = 32.sp, lineHeight = 32.sp, fontFeatureSettings = "tnum"),
            color = c.ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = SpType.caption, color = c.muted, maxLines = 1)
    }
}

@Composable
fun TasteBars(
    bars: List<TasteGenreBar>,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val firstPositiveIdx = bars.indexOfFirst { it.isPositive }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        bars.forEachIndexed { index, bar ->
            val targetFrac = (bar.avgStars / 5.0).toFloat().coerceIn(0.08f, 1f)
            val animFrac = remember(bar.label, targetFrac) {
                Animatable(if (reduceMotion) targetFrac else 0f)
            }
            LaunchedEffect(bar.label, targetFrac, reduceMotion) {
                if (!reduceMotion) {
                    animFrac.snapTo(0f)
                    delay(index * 60L)
                    animFrac.animateTo(targetFrac, tween(400, easing = Motion.Out))
                }
            }
            val barColor = when {
                !bar.isPositive -> c.starOff
                index == firstPositiveIdx -> c.accent
                else -> c.ink
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = bar.label,
                    style = SpType.rowTitle.copy(fontSize = 13.5.sp),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(128.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(c.track)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animFrac.value)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
                Text(
                    text = String.format(Locale.ITALY, "%.1f", bar.avgStars),
                    style = SpType.rowTitle.copy(fontSize = 13.5.sp, fontFeatureSettings = "tnum"),
                    color = c.ink,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }
}

@Composable
fun SwipeActionBackground(
    isRightSwipe: Boolean,
    armed: Boolean,
    modifier: Modifier = Modifier,
    leftActionLabel: String = stringResource(R.string.ranking_swipe_owned),
    rightActionLabel: String = stringResource(R.string.ranking_swipe_hide)
) {
    val c = SpTheme.colors
    val bgColor = if (isRightSwipe) c.ink else c.accent
    val icon = if (isRightSwipe) Icons.Rounded.Check else Icons.Rounded.VisibilityOff
    val label = if (isRightSwipe) leftActionLabel else rightActionLabel

    val iconScale = remember { Animatable(0.85f) }
    val iconAlpha = remember { Animatable(0.7f) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(armed) {
        if (reduceMotion) {
            iconScale.snapTo(if (armed) 1.05f else 0.85f)
            iconAlpha.snapTo(if (armed) 1f else 0.7f)
        } else {
            launch { iconScale.animateTo(if (armed) 1.15f else 0.85f, tween(160, easing = Motion.Out)) }
            launch { iconAlpha.animateTo(if (armed) 1f else 0.7f, tween(160, easing = Motion.Out)) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 20.dp),
        contentAlignment = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
                alpha = iconAlpha.value
            }
        ) {
            if (isRightSwipe) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(text = label, style = SpType.tab.copy(fontWeight = FontWeight.Bold), color = Color.White)
            } else {
                Text(text = label, style = SpType.tab.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
