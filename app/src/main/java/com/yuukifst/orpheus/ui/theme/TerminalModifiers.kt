package com.yuukifst.orpheus.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.terminalBorder(
    width: Dp = 1.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline
): Modifier = border(width = width, color = color, shape = RectangleShape)

@Composable
fun Modifier.terminalDivider(): Modifier = terminalBorder(width = 1.dp)
