package com.switchpulse.app.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

val LocalReduceMotion = compositionLocalOf { false }

/**
 * Editorial motion personality: decisive, brisk, zero bounce.
 */
object Motion {
    const val Quick = 120
    const val Standard = 240
    const val Emphasized = 420
    const val Exit = 200

    val Out = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emph = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Away = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> quick(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMillis = Quick, easing = Out)

    fun <T> standard(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduceMotion) Quick else Standard, easing = Out)

    fun <T> emphasized(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduceMotion) Quick else Emphasized, easing = Emph)

    fun <T> exit(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduceMotion) Quick else Exit, easing = Away)

    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 500f)
}

fun View.hapticTick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )
}

fun View.hapticConfirm() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )
}

fun View.hapticReject() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )
}

fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed && !reduceMotion) pressedScale else 1f,
        animationSpec = Motion.quick(),
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
