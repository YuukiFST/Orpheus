package com.yuukifst.orpheus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing

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

    fun openTween() = tween<Float>(durationMillis = DurationFast, easing = EaseSmoothOut)
    fun closeTween() = tween<Float>(durationMillis = DurationQuick, easing = EaseSmoothOut)
    fun hoverInTween() = tween<Float>(durationMillis = DurationQuick, easing = EaseSmoothOut)
    fun hoverOutTween() = tween<Float>(durationMillis = DurationFast, easing = EaseBounceStrong)
}
