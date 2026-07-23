package com.yuukifst.orpheus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion tokens aligned with transitions.dev scale.
 * Reference these instead of ad-hoc durations in UI animations.
 */
object OrpheusMotion {
    const val DurationStagger = 40
    const val DurationMicro = 80
    const val DurationQuick = 150
    const val DurationFast = 250
    const val DurationMedium = 350
    const val DurationSlow = 400
    const val DurationVerySlow = 500

    val EaseSmoothOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseBounce: Easing = CubicBezierEasing(0.34f, 1.36f, 0.64f, 1f)
    val EaseBounceStrong: Easing = CubicBezierEasing(0.34f, 3.85f, 0.64f, 1f)

    fun openTween(): FiniteAnimationSpec<Float> =
        tween(durationMillis = DurationFast, easing = EaseSmoothOut)

    fun closeTween(): FiniteAnimationSpec<Float> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun openSizeTween(): FiniteAnimationSpec<IntSize> =
        tween(durationMillis = DurationFast, easing = EaseSmoothOut)

    fun closeSizeTween(): FiniteAnimationSpec<IntSize> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun openOffsetTween(): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = DurationFast, easing = EaseSmoothOut)

    fun closeOffsetTween(): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun openDpTween(): FiniteAnimationSpec<Dp> =
        tween(durationMillis = DurationFast, easing = EaseSmoothOut)

    fun closeDpTween(): FiniteAnimationSpec<Dp> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun openColorTween(): FiniteAnimationSpec<Color> =
        tween(durationMillis = DurationFast, easing = EaseSmoothOut)

    fun closeColorTween(): FiniteAnimationSpec<Color> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun hoverInTween(): FiniteAnimationSpec<Float> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    fun hoverOutTween(): FiniteAnimationSpec<Float> =
        tween(durationMillis = DurationFast, easing = EaseBounceStrong)

    /** Settings/about-style screen enter — fast fade, no heavy slide. */
    fun screenEnterAlphaTween(): FiniteAnimationSpec<Float> = openTween()

    fun screenEnterOffsetTween(): FiniteAnimationSpec<Dp> =
        tween(durationMillis = DurationQuick, easing = EaseSmoothOut)

    /** Subtle content swap — opacity + tiny scale, not zoom. */
    const val ContentSwapScale = 0.95f
}
