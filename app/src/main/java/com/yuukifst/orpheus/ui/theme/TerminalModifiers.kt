package com.yuukifst.orpheus.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.terminalBorder(
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outline
): Modifier = border(width = width, color = color, shape = RectangleShape)

@Composable
fun Modifier.terminalDivider(): Modifier = terminalBorder(width = 1.dp)

fun Modifier.phosphorGlow(
    color: Color = VantaAccent,
    alpha: Float = 0.22f,
    layers: Int = 2,
): Modifier = drawBehind {
    val layerStep = 2.dp.toPx()
    repeat(layers) { index ->
        val inset = layerStep * (index + 1)
        drawRect(
            color = color.copy(alpha = alpha / (index + 1)),
            topLeft = Offset(-inset, -inset),
            size = androidx.compose.ui.geometry.Size(
                width = size.width + inset * 2,
                height = size.height + inset * 2
            )
        )
    }
}

fun Modifier.terminalPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut)
        } else {
            tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseBounceStrong)
        },
        label = "terminalPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.terminalAccentLine(
    position: TerminalLinePosition = TerminalLinePosition.Top,
    color: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = 1.dp,
    glowAlpha: Float = 0.35f,
): Modifier = drawBehind {
    val stroke = thickness.toPx()
    when (position) {
        TerminalLinePosition.Top -> {
            drawRect(color = color, size = androidx.compose.ui.geometry.Size(size.width, stroke))
            drawRect(
                color = color.copy(alpha = glowAlpha),
                topLeft = Offset(0f, stroke),
                size = androidx.compose.ui.geometry.Size(size.width, stroke * 2)
            )
        }
        TerminalLinePosition.Bottom -> {
            drawRect(
                color = color,
                topLeft = Offset(0f, size.height - stroke),
                size = androidx.compose.ui.geometry.Size(size.width, stroke)
            )
            drawRect(
                color = color.copy(alpha = glowAlpha),
                topLeft = Offset(0f, size.height - stroke * 3),
                size = androidx.compose.ui.geometry.Size(size.width, stroke * 2)
            )
        }
    }
}

enum class TerminalLinePosition {
    Top,
    Bottom
}
