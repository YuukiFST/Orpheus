package com.yuukifst.orpheus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
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

    val EaseSmoothOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}
