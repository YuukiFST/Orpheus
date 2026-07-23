package com.yuukifst.orpheus.presentation.components.scoped
import com.yuukifst.orpheus.ui.theme.TerminalCornerShape

import androidx.compose.animation.animateColorAsState
import com.yuukifst.orpheus.ui.theme.OrpheusMotion
import com.yuukifst.orpheus.ui.theme.terminalBorder
import com.yuukifst.orpheus.ui.theme.terminalPressScale
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layoutId
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// In a new file or alongside PlayerInternalNavigationItemsRow.kt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compactMode: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Animated colors - only recompose when 'selected' changes
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = tween(durationMillis = OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = tween(durationMillis = OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut),
        label = "textColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = tween(
            durationMillis = if (selected) OrpheusMotion.DurationFast else OrpheusMotion.DurationQuick,
            easing = if (selected) OrpheusMotion.EaseBounce else OrpheusMotion.EaseBounceStrong
        ),
        label = "iconScale"
    )

    // Determine whether to show the label
    val showLabel = label != null && (alwaysShowLabel || selected)
    val indicatorWidth = 64.dp
    val indicatorHeight = 32.dp
    val iconWidth = 48.dp
    val iconHeight = 24.dp
    val indicatorPadding = 4.dp
    val indicatorShape = TerminalCornerShape
    val iconShape = TerminalCornerShape

    // Main layout
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .terminalPressScale(interactionSource)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null //ripple(bounded = true, radius = 24.dp) // Contained ripple
            )
            .semantics {
                 if (contentDescription != null) {
                     this.contentDescription = contentDescription
                 }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Container for the icon with indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(indicatorWidth, indicatorHeight)
        ) {
            // Background indicator (pill shape for Material 3 Expressive)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = tween(OrpheusMotion.DurationMicro)) +
                        scaleIn(
                            animationSpec = tween(OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseBounce),
                        ),
                exit = fadeOut(animationSpec = tween(OrpheusMotion.DurationQuick)) +
                        scaleOut(animationSpec = tween(OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = indicatorPadding)
                        .terminalBorder(
                            color = indicatorColor.copy(alpha = 0.85f),
                            width = 1.dp
                        )
                        .background(
                            color = indicatorColor.copy(alpha = 0.35f),
                            shape = indicatorShape
                        )
                )
            }

            // Clickable area of the icon (smaller than the container)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconWidth, iconHeight)
                    .clip(iconShape)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }

            ) {
                // Icon
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            if (showLabel) {
                                // Semantics are handled at the top level
                            }
                        }
                    ) {
                        if (selected) selectedIcon() else icon()
                    }
                }
            }
        }

        // Label with animation
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = tween(OrpheusMotion.DurationFast, delayMillis = OrpheusMotion.DurationMicro)),
            exit = fadeOut(animationSpec = tween(OrpheusMotion.DurationQuick))
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                ) {
                    label?.invoke()
                }
            }
        }
    }
}

