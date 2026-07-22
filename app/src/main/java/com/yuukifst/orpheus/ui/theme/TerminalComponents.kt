package com.yuukifst.orpheus.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Staggered list/grid enter — offset capped so total stays under ~300ms.
 */
fun Modifier.terminalStaggerEnter(
    index: Int,
    maxStaggerItems: Int = 7,
    distance: Dp = 8.dp,
): Modifier = composed {
    var shown by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay((index.coerceAtMost(maxStaggerItems) * OrpheusMotion.DurationStagger).toLong())
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseSmoothOut),
        label = "terminalStaggerAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (shown) 0.dp else distance,
        animationSpec = tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseSmoothOut),
        label = "terminalStaggerOffset"
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = offsetY.toPx()
    }
}

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .terminalBorder(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun TerminalPromptLabel(
    text: String,
    modifier: Modifier = Modifier,
    showPrefix: Boolean = true,
) {
    Text(
        text = if (showPrefix) "> $text" else text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun TerminalSurface(
    modifier: Modifier = Modifier,
    accentLine: TerminalLinePosition? = null,
    content: @Composable () -> Unit,
) {
    val lineModifier = when (accentLine) {
        TerminalLinePosition.Top -> Modifier.terminalAccentLine(TerminalLinePosition.Top)
        TerminalLinePosition.Bottom -> Modifier.terminalAccentLine(TerminalLinePosition.Bottom)
        null -> Modifier
    }
    Surface(
        modifier = modifier.then(lineModifier),
        shape = TerminalCornerShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = content
    )
}
