package com.yuukifst.orpheus.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val ScanlineSpacingPx = 3f
private const val ScanlineAlpha = 0.045f
private const val VignetteAlpha = 0.42f
private const val PhosphorTintAlpha = 0.018f

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
fun CrtScreenOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) return

    val accent = MaterialTheme.colorScheme.primary
    val reduceMotion = rememberReduceMotion()

    val flickerAlpha = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "crtFlicker")
        val flicker by transition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = OrpheusMotion.DurationVerySlow * 4, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "crtFlickerAlpha"
        )
        flicker
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawScanlines(flickerAlpha)
        drawVignette()
        drawPhosphorTint(accent)
    }
}

private fun DrawScope.drawScanlines(flickerAlpha: Float) {
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = Color.Black.copy(alpha = ScanlineAlpha * flickerAlpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += ScanlineSpacingPx
    }
}

private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = VignetteAlpha)
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.72f
        )
    )
}

private fun DrawScope.drawPhosphorTint(accent: Color) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = PhosphorTintAlpha),
                Color.Transparent,
                accent.copy(alpha = PhosphorTintAlpha * 0.5f)
            )
        )
    )
}

@Composable
fun CrtContentWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        CrtScreenOverlay()
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
