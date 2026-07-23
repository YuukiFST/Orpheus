package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.graphics.Shape

/**
 * Cached shape instances for list items and surfaces.
 */
object ShapeCache {
    val smooth8: Shape get() = OrpheusActiveShapes.set.smooth8
    val smooth10: Shape get() = OrpheusActiveShapes.set.smooth10
    val smooth12: Shape get() = OrpheusActiveShapes.set.smooth12
    val smooth14: Shape get() = OrpheusActiveShapes.set.smooth14
    val smooth16: Shape get() = OrpheusActiveShapes.set.smooth16
    val smooth20: Shape get() = OrpheusActiveShapes.set.smooth20
    val smooth24: Shape get() = OrpheusActiveShapes.set.smooth24
    val smooth28: Shape get() = OrpheusActiveShapes.set.smooth28
    val smooth32: Shape get() = OrpheusActiveShapes.set.smooth32
    val smoothPill: Shape get() = OrpheusActiveShapes.set.smoothPill

    val expressiveAvatar: Shape get() = OrpheusActiveShapes.set.expressiveAvatar
    val expressiveClover: Shape get() = OrpheusActiveShapes.set.expressiveClover
    val expressiveHero: Shape get() = OrpheusActiveShapes.set.expressiveHero
}
