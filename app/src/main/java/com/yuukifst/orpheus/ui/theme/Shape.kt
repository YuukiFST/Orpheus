package com.yuukifst.orpheus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class OrpheusShapeSet(
    val terminal: RoundedCornerShape,
    val button: RoundedCornerShape,
    val iconButton: RoundedCornerShape,
    val searchBar: RoundedCornerShape,
    val smooth8: RoundedCornerShape,
    val smooth10: RoundedCornerShape,
    val smooth12: RoundedCornerShape,
    val smooth14: RoundedCornerShape,
    val smooth16: RoundedCornerShape,
    val smooth20: RoundedCornerShape,
    val smooth24: RoundedCornerShape,
    val smooth28: RoundedCornerShape,
    val smooth32: RoundedCornerShape,
    val smoothPill: RoundedCornerShape,
    val expressiveAvatar: Shape,
    val expressiveClover: Shape,
    val expressiveHero: Shape,
)

object OrpheusShapeSets {
    val Square = OrpheusShapeSet(
        terminal = RoundedCornerShape(0.dp),
        button = RoundedCornerShape(0.dp),
        iconButton = RoundedCornerShape(0.dp),
        searchBar = RoundedCornerShape(0.dp),
        smooth8 = RoundedCornerShape(0.dp),
        smooth10 = RoundedCornerShape(0.dp),
        smooth12 = RoundedCornerShape(0.dp),
        smooth14 = RoundedCornerShape(0.dp),
        smooth16 = RoundedCornerShape(0.dp),
        smooth20 = RoundedCornerShape(0.dp),
        smooth24 = RoundedCornerShape(0.dp),
        smooth28 = RoundedCornerShape(0.dp),
        smooth32 = RoundedCornerShape(0.dp),
        smoothPill = RoundedCornerShape(0.dp),
        expressiveAvatar = RectangleShape,
        expressiveClover = RectangleShape,
        expressiveHero = RectangleShape,
    )

    val Rounded = OrpheusShapeSet(
        terminal = RoundedCornerShape(12.dp),
        button = RoundedCornerShape(12.dp),
        iconButton = RoundedCornerShape(16.dp),
        searchBar = RoundedCornerShape(28.dp),
        smooth8 = RoundedCornerShape(8.dp),
        smooth10 = RoundedCornerShape(10.dp),
        smooth12 = RoundedCornerShape(12.dp),
        smooth14 = RoundedCornerShape(14.dp),
        smooth16 = RoundedCornerShape(16.dp),
        smooth20 = RoundedCornerShape(20.dp),
        smooth24 = RoundedCornerShape(24.dp),
        smooth28 = RoundedCornerShape(28.dp),
        smooth32 = RoundedCornerShape(32.dp),
        smoothPill = RoundedCornerShape(50),
        expressiveAvatar = RoundedCornerShape(16.dp),
        expressiveClover = RoundedCornerShape(20.dp),
        expressiveHero = RoundedCornerShape(24.dp),
    )
}

/** Synced from [OrpheusTheme]; safe to read outside @Composable (defaults, remember keys). */
object OrpheusActiveShapes {
    var set: OrpheusShapeSet = OrpheusShapeSets.Square
        internal set
}

val LocalOrpheusShapes = staticCompositionLocalOf { OrpheusShapeSets.Square }

val TerminalShape: Shape get() = OrpheusActiveShapes.set.terminal

val TerminalCornerShape: RoundedCornerShape get() = OrpheusActiveShapes.set.terminal

val OrpheusButtonShape: RoundedCornerShape get() = OrpheusActiveShapes.set.button

val OrpheusIconButtonShape: RoundedCornerShape get() = OrpheusActiveShapes.set.iconButton

val OrpheusSearchBarShape: RoundedCornerShape get() = OrpheusActiveShapes.set.searchBar

@Composable
@ReadOnlyComposable
fun terminalCornerShape(
    cornerRadius: Dp = 0.dp,
    smoothnessAsPercent: Int = 0,
): Shape {
    val shapes = LocalOrpheusShapes.current
    return if (shapes === OrpheusShapeSets.Square) {
        RoundedCornerShape(0.dp)
    } else if (cornerRadius > 0.dp) {
        RoundedCornerShape(cornerRadius)
    } else {
        shapes.terminal
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
@ReadOnlyComposable
fun terminalCornerShape(
    cornerRadiusTL: Dp = 0.dp,
    smoothnessAsPercentTL: Int = 0,
    cornerRadiusTR: Dp = 0.dp,
    smoothnessAsPercentTR: Int = 0,
    cornerRadiusBL: Dp = 0.dp,
    smoothnessAsPercentBL: Int = 0,
    cornerRadiusBR: Dp = 0.dp,
    smoothnessAsPercentBR: Int = 0,
): Shape = terminalCornerShape()

fun orpheusMaterialShapes(shapeSet: OrpheusShapeSet): Shapes = Shapes(
    small = shapeSet.button,
    medium = shapeSet.button,
    large = shapeSet.iconButton,
)
