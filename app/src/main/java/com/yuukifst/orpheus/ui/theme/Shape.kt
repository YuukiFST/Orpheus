package com.yuukifst.orpheus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sharp terminal corners — use instead of Shape or ad-hoc radii. */
val TerminalShape: Shape = RectangleShape
val TerminalCornerShape = RoundedCornerShape(0.dp)

/** Subtle radius for interactive controls — concentric with 8dp padding on 14dp outer. */
val OrpheusButtonShape = RoundedCornerShape(6.dp)
val OrpheusIconButtonShape = RoundedCornerShape(8.dp)

/** Drop-in replacement for Shape; ignores radius and smoothness. */
@Suppress("UNUSED_PARAMETER")
fun terminalCornerShape(
    cornerRadius: Dp = 0.dp,
    smoothnessAsPercent: Int = 0,
): Shape = TerminalCornerShape

@Suppress("UNUSED_PARAMETER")
fun terminalCornerShape(
    cornerRadiusTL: Dp = 0.dp,
    smoothnessAsPercentTL: Int = 0,
    cornerRadiusTR: Dp = 0.dp,
    smoothnessAsPercentTR: Int = 0,
    cornerRadiusBL: Dp = 0.dp,
    smoothnessAsPercentBL: Int = 0,
    cornerRadiusBR: Dp = 0.dp,
    smoothnessAsPercentBR: Int = 0,
): Shape = TerminalCornerShape

val Shapes = Shapes(
    small = OrpheusButtonShape,
    medium = OrpheusButtonShape,
    large = OrpheusIconButtonShape,
)
