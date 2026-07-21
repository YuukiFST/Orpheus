package com.yuukifst.orpheus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Cached shape instances for list items and surfaces.
 * Terminal aesthetic: sharp corners, no smooth/iOS-style curves.
 */
object ShapeCache {
    val smooth8 = RoundedCornerShape(0.dp)
    val smooth10 = RoundedCornerShape(0.dp)
    val smooth12 = RoundedCornerShape(0.dp)
    val smooth14 = RoundedCornerShape(0.dp)
    val smooth16 = RoundedCornerShape(0.dp)
    val smooth20 = RoundedCornerShape(0.dp)
    val smooth24 = RoundedCornerShape(0.dp)
    val smooth28 = RoundedCornerShape(0.dp)
    val smooth32 = RoundedCornerShape(0.dp)
    val smoothPill = RoundedCornerShape(0.dp)

    val expressiveAvatar: Shape = RectangleShape
    val expressiveClover: Shape = RectangleShape
    val expressiveHero: Shape = RectangleShape
}
