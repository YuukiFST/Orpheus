package com.yuukifst.orpheus.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.yuukifst.orpheus.ui.theme.OrpheusMotion
import com.yuukifst.orpheus.ui.theme.terminalPressScale

@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(
            durationMillis = if (isSelected) OrpheusMotion.DurationFast else OrpheusMotion.DurationQuick,
            easing = OrpheusMotion.EaseSmoothOut
        ),
        label = "tabBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) onSelectedColor else onUnselectedColor,
        animationSpec = tween(OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut),
        label = "tabContentColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.dp else 0.dp,
        animationSpec = tween(OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut),
        label = "tabBorderWidth"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseBounce),
        label = "tabScale"
    )

    Tab(
        modifier = modifier
            .padding(all = 5.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.transformOrigin = transformOrigin
            }
            .terminalPressScale(interactionSource)
            .clip(RectangleShape)
            .background(color = backgroundColor, shape = RectangleShape)
            .border(width = borderWidth, color = selectedColor, shape = RectangleShape),
        selected = isSelected,
        text = content,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        selectedContentColor = contentColor,
        unselectedContentColor = contentColor,
        interactionSource = interactionSource
    )
}
