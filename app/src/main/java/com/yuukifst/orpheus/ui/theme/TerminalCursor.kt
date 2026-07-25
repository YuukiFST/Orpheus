package com.yuukifst.orpheus.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun TerminalCursorBlink(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    charWidth: Dp = 8.dp,
    charHeight: Dp = 14.dp,
) {
    val reduceMotion = rememberReduceMotion()
    val alpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "cursorBlink")
        val blink by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(OrpheusMotion.DurationSlow, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cursorAlpha"
        )
        blink
    }

    Canvas(modifier = modifier) {
        drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(charWidth.toPx(), charHeight.toPx())
        )
    }
}
