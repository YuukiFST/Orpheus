package com.yuukifst.orpheus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape

/**
 * Cached shape instances for list items and surfaces.
 */
object ShapeCache {
    val smooth8: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth8
    val smooth10: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth10
    val smooth12: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth12
    val smooth14: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth14
    val smooth16: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth16
    val smooth20: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth20
    val smooth24: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth24
    val smooth28: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth28
    val smooth32: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smooth32
    val smoothPill: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.smoothPill

    val expressiveAvatar: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.expressiveAvatar
    val expressiveClover: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.expressiveClover
    val expressiveHero: Shape
        @Composable @ReadOnlyComposable get() = LocalOrpheusShapes.current.expressiveHero
}
