package com.yuukifst.orpheus.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object OrpheusButtonDefaults {

    @Composable
    fun filledColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
    )

    @Composable
    fun filledTonalColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    @Composable
    fun outlinedColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun textColors(): ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun elevation(): ButtonElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 1.dp,
        pressedElevation = 0.dp,
        focusedElevation = 2.dp,
        hoveredElevation = 3.dp,
        disabledElevation = 0.dp,
    )

    @Composable
    fun tonalElevation(): ButtonElevation = ButtonDefaults.filledTonalButtonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 1.dp,
        hoveredElevation = 2.dp,
        disabledElevation = 0.dp,
    )

    @Composable
    fun filledIconColors(): IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )

    @Composable
    fun filledTonalIconColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
fun OrpheusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = OrpheusButtonDefaults.filledColors(),
    elevation: ButtonElevation? = OrpheusButtonDefaults.elevation(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val buttonShape = shape ?: OrpheusButtonShape
    Button(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun OrpheusFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = OrpheusButtonDefaults.filledTonalColors(),
    elevation: ButtonElevation? = OrpheusButtonDefaults.tonalElevation(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val buttonShape = shape ?: OrpheusButtonShape
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        elevation = elevation,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun OrpheusOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = OrpheusButtonDefaults.outlinedColors(),
    border: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outline,
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val buttonShape = shape ?: OrpheusButtonShape
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun OrpheusTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = OrpheusButtonDefaults.textColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val buttonShape = shape ?: OrpheusButtonShape
    TextButton(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun OrpheusFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = OrpheusButtonDefaults.filledIconColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val buttonShape = shape ?: OrpheusIconButtonShape
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun OrpheusFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = OrpheusButtonDefaults.filledTonalIconColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val buttonShape = shape ?: OrpheusIconButtonShape
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.terminalPressScale(interactionSource, pressedScale = 0.96f),
        enabled = enabled,
        shape = buttonShape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
