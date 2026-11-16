package com.switchpulse.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.motion.hapticTick
import com.switchpulse.app.ui.motion.pressScale
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlin.math.roundToInt

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    Text(
        text = text.uppercase(),
        style = SpType.label,
        color = c.muted,
        modifier = modifier
    )
}

@Composable
fun UnderlineTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    horizontalPadding: Dp = 20.dp,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                if (idx > 0) Spacer(modifier = Modifier.width(22.dp))
                Column(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!selected) {
                                view.hapticTick()
                                onSelect(idx)
                            }
                        },
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = SpType.tab.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) c.ink else c.muted,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.dp)
                            .background(if (selected) c.ink else Color.Transparent)
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.weight(1f))
                trailing()
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = c.line,
            modifier = Modifier.padding(horizontal = if (scrollable) 0.dp else horizontalPadding)
        )
    }
}

@Composable
fun SegmentedChoice(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, c.border, RoundedCornerShape(4.dp))
    ) {
        items.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            if (idx > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(c.border)
                )
            }
            val bg by animateColorAsState(
                targetValue = if (selected) c.ink else Color.Transparent,
                animationSpec = Motion.quick(),
                label = "segment_bg_$idx"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(bg)
                    .clickable {
                        if (!selected) {
                            view.hapticTick()
                            onSelect(idx)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = SpType.tab.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) c.paper else c.ink,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SpSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null
) {
    val c = SpTheme.colors
    val view = LocalView.current
    var lastHapticBucket by remember { mutableIntStateOf(value.roundToInt()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = SpType.rowTitle.copy(fontSize = 15.sp),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RollingDigitsText(
                value = valueText,
                style = SpType.rowPrice.copy(
                    fontFamily = com.switchpulse.app.ui.theme.Bricolage,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = c.ink
            )
        }
        Slider(
            value = value,
            onValueChange = { next ->
                val bucket = next.roundToInt()
                if (bucket != lastHapticBucket) {
                    lastHapticBucket = bucket
                    view.hapticTick()
                }
                onValueChange(next)
            },
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = c.ink,
                activeTrackColor = c.ink,
                inactiveTrackColor = c.track,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (!helpText.isNullOrBlank()) {
            Text(
                text = helpText,
                style = SpType.caption.copy(fontSize = 13.sp),
                color = c.muted
            )
        }
    }
}

@Composable
fun SpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) c.ink else c.border,
        animationSpec = Motion.quick(),
        label = "switch_track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = Motion.quick(),
        label = "switch_thumb"
    )
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable {
                view.hapticTick()
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.paper else Color.White)
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueStrong: Boolean = false,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val c = SpTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SpType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = SpType.caption,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = SpType.body.copy(
                        fontWeight = if (valueStrong) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (valueStrong) c.ink else c.muted,
                    maxLines = 1
                )
            }
            if (trailing != null) {
                trailing()
            } else if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.faint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    trailingIcon: ImageVector? = null
) {
    val c = SpTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interaction, enabled = enabled)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) c.ink else c.border)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = SpType.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) c.paper else c.muted
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = if (enabled) c.paper else c.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp
) {
    val c = SpTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(height)
            .pressScale(interaction, enabled = enabled)
            .clip(RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, c.border), RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = SpType.rowTitle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            color = c.ink,
            maxLines = 1
        )
    }
}

@Composable
fun ActionBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    urgentOfflineIcon: Boolean = false
) {
    val c = SpTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bannerBg)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (urgentOfflineIcon) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = c.urgent,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            style = SpType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = c.ink,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.ink)
                .clickable(onClick = onAction)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                style = SpType.captionBold.copy(fontSize = 13.sp),
                color = c.paper
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    outlinedCta: Boolean = false,
    centered: Boolean = true
) {
    val c = SpTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = title,
            style = SpType.cardTitle.copy(fontSize = 20.sp),
            color = c.ink,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = SpType.body.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = c.muted,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (outlinedCta) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, c.ink), RoundedCornerShape(4.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = SpType.tab.copy(fontWeight = FontWeight.Bold),
                    color = c.ink
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.ink)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = SpType.tab.copy(fontWeight = FontWeight.Bold),
                    color = c.paper
                )
            }
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    radius: Dp = 4.dp
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton_pulse")
        val animated by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )
        animated
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .graphicsLayer { this.alpha = alpha }
            .background(c.track)
    )
}

@Composable
fun EditorialIndeterminateBar(
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(c.track)
    ) {
        if (reduceMotion) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().background(c.accent))
        } else {
            val transition = rememberInfiniteTransition(label = "sync_bar")
            val progress by transition.animateFloat(
                initialValue = -0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = Motion.Out),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sync_bar_x"
            )
            val barWidth = maxWidth * 0.35f
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * progress)
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(c.accent)
            )
        }
    }
}

@Composable
fun SpSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(12.dp)
    ) { data ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.snackbar)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = data.visuals.message,
                style = SpType.body.copy(fontSize = 14.sp),
                color = c.snackbarText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val actionLabel = data.visuals.actionLabel
            if (!actionLabel.isNullOrBlank()) {
                TextButton(
                    onClick = { data.performAction() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = SpType.tab.copy(fontWeight = FontWeight.Bold),
                        color = c.snackbarAction
                    )
                }
            }
        }
    }
}
