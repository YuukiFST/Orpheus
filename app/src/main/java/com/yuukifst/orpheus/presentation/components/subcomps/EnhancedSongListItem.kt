package com.yuukifst.orpheus.presentation.components.subcomps

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import coil.size.Size
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.presentation.components.AutoScrollingText
import com.yuukifst.orpheus.presentation.components.ShimmerBox
import androidx.compose.ui.res.stringResource
import com.yuukifst.orpheus.R
import com.yuukifst.orpheus.presentation.components.SmartImage
import com.yuukifst.orpheus.ui.theme.LocalTerminalChrome
import com.yuukifst.orpheus.ui.theme.OrpheusFilledIconButton
import com.yuukifst.orpheus.ui.theme.OrpheusIconButtonShape
import com.yuukifst.orpheus.ui.theme.OrpheusMotion
import com.yuukifst.orpheus.ui.theme.OrpheusSpacing
import com.yuukifst.orpheus.ui.theme.terminalBorder
import com.yuukifst.orpheus.ui.theme.terminalStaggerEnter

@Immutable
private data class EnhancedSongAnimationTarget(
    val isHighlighted: Boolean = false,
    val isSelected: Boolean = false
)

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

/**
 * Enhanced song list item with multi-selection support.
 * 
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Callback for more options button
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 50.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    enterIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val itemModifier = if (enterIndex != null) {
        modifier.terminalStaggerEnter(enterIndex)
    } else {
        modifier
    }
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() }
    val isHighlighted = isCurrentSong && !isLoading
    val transition = updateTransition(
        targetState = EnhancedSongAnimationTarget(
            isHighlighted = isHighlighted,
            isSelected = isSelected
        ),
        label = "EnhancedSongListItemTransition"
    )

    // Share one transition across the item and derive the visual properties from a
    // couple of progress values instead of animating each color/radius independently.
    val highlightProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = OrpheusMotion.DurationFast, easing = OrpheusMotion.EaseSmoothOut) },
        label = "highlightProgress"
    ) { state ->
        if (state.isHighlighted) 1f else 0f
    }
    val selectionVisualProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseSmoothOut) },
        label = "selectionVisualProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }
    val selectionScaleProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = OrpheusMotion.DurationQuick, easing = OrpheusMotion.EaseBounceStrong) },
        label = "selectionScaleProgress"
    ) { state ->
        if (state.isSelected) 1f else 0f
    }

    // Pixel theme: PixelPlayerOSS shapes (22→50dp row, 10→50dp album). Terminal Dark/Light: square.
    val usePixelChrome = !LocalTerminalChrome.current
    val animatedCornerRadius = if (usePixelChrome) {
        lerpDp(22.dp, 50.dp, highlightProgress)
    } else {
        0.dp
    }
    val animatedAlbumCornerRadius = if (usePixelChrome) {
        lerpDp(10.dp, 50.dp, highlightProgress)
    } else {
        0.dp
    }

    val surfaceShape: Shape = remember(animatedCornerRadius, customShape, isHighlighted, usePixelChrome) {
        when {
            customShape != null && !isHighlighted -> customShape
            usePixelChrome -> RoundedCornerShape(animatedCornerRadius)
            else -> customShape ?: RectangleShape
        }
    }
    val albumShape: Shape = if (usePixelChrome) {
        RoundedCornerShape(animatedAlbumCornerRadius)
    } else {
        RectangleShape
    }
    val albumLoadingShape: Shape = if (usePixelChrome) CircleShape else RectangleShape
    val trailingLoadingShape: Shape = if (usePixelChrome) CircleShape else RectangleShape
    val shimmerTextShape = if (usePixelChrome) RoundedCornerShape(4.dp) else RoundedCornerShape(0.dp)
    val trailingButtonShape: Shape = if (usePixelChrome) CircleShape else OrpheusIconButtonShape

    val colors = MaterialTheme.colorScheme
    val baseContainerColor = containerColorOverride
        ?: if (usePixelChrome) colors.surfaceContainerLow else colors.surface
    val playbackContainerColor = if (usePixelChrome) {
        lerpColor(baseContainerColor, colors.primaryContainer, highlightProgress)
    } else {
        baseContainerColor
    }
    val containerColor = if (usePixelChrome) {
        lerpColor(playbackContainerColor, colors.secondaryContainer, selectionVisualProgress)
    } else {
        lerpColor(baseContainerColor, colors.surfaceContainerLow, selectionVisualProgress * 0.35f)
    }

    val baseContentColor = colors.onSurface
    val contentColor = if (usePixelChrome) {
        val playback = lerpColor(baseContentColor, colors.onPrimaryContainer, highlightProgress)
        lerpColor(playback, colors.onSecondaryContainer, selectionVisualProgress)
    } else {
        lerpColor(baseContentColor, colors.primary, highlightProgress)
    }
    val titleColor = contentColor

    val selectionBorderColor = lerpColor(colors.primary.copy(alpha = 0f), colors.primary, selectionVisualProgress)
    val selectionBorderWidth = lerpDp(0.dp, if (usePixelChrome) 2.5.dp else 1.dp, selectionVisualProgress)
    val highlightBorderWidth = if (usePixelChrome) 0.dp else lerpDp(0.dp, 2.dp, highlightProgress)
    val idleOutlineColor = colors.outline.copy(alpha = 0.5f)
    val rowBorderColor = when {
        selectionVisualProgress > 0.001f -> selectionBorderColor
        highlightProgress > 0.001f -> colors.primary.copy(alpha = highlightProgress)
        else -> idleOutlineColor
    }
    val rowBorderWidth = when {
        selectionVisualProgress > 0.001f -> selectionBorderWidth
        highlightProgress > 0.001f -> highlightBorderWidth
        else -> 1.dp
    }
    val selectionScale = lerpFloat(1f, if (usePixelChrome) 0.98f else 0.96f, selectionScaleProgress)
    val selectionOverlayColor = lerpColor(
        Color.Transparent,
        colors.primary.copy(alpha = 0.7f),
        selectionVisualProgress
    )
    val selectionOverlayContentColor = lerpColor(
        Color.Transparent,
        colors.onPrimary,
        selectionVisualProgress
    )
    val showSelectionDecoration = selectionVisualProgress > 0.001f

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = itemModifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = containerColorOverride ?: colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrpheusSpacing.sm, vertical = OrpheusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(albumLoadingShape)
                    )
                    Spacer(modifier = Modifier.width(OrpheusSpacing.sm))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else OrpheusSpacing.xxs)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(shimmerTextShape)
                    )
                    Spacer(modifier = Modifier.height(OrpheusSpacing.xxs))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(shimmerTextShape)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(shimmerTextShape)
                    )
                }
                Spacer(modifier = Modifier.width(OrpheusSpacing.sm))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(trailingLoadingShape)
                )
            }
        }
    } else {
        // Actual Song Item Layout
        Surface(
            modifier = itemModifier
                .fillMaxWidth()
                .scale(selectionScale)
                .clip(surfaceShape)
                .then(
                    if (!usePixelChrome) {
                        Modifier.terminalBorder(width = rowBorderWidth, color = rowBorderColor)
                    } else if (showSelectionDecoration) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                // Expose a button + click/long-click actions to TalkBack (the raw
                // pointerInput gestures below are invisible to the a11y tree). Merge the
                // title/artist text into one node so it's announced as a single item.
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick {
                        if (isSelectionMode) {
                            onLongPress()
                        } else {
                            onClick()
                        }
                        true
                    }
                    onLongClick { onLongPress(); true }
                }
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) {
                                // In selection mode, tap toggles selection
                                onLongPress()
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = {
                            // Long press always activates/toggles selection
                            onLongPress()
                        }
                    )
                },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrpheusSpacing.sm, vertical = OrpheusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .then(
                                if (usePixelChrome) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, colors.outline, RectangleShape)
                                }
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant, albumShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            // Decorative here: the title is already announced via the row's merged semantics.
                            contentDescription = null,
                            shape = albumShape,
                            targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Selection check overlay on album art
                        if (showSelectionDecoration) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = selectionOverlayColor,
                                        shape = albumShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = selectionOverlayContentColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.presentation_batch_g_list_cd_selected),
                                        tint = selectionOverlayContentColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(OrpheusSpacing.sm))
                } else {
                    Spacer(modifier = Modifier.width(OrpheusSpacing.xxs))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (isHighlighted && !isSelectionMode) {
                        AutoScrollingText(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            gradientEdgeColor = containerColor,
                        )

                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = titleColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(OrpheusSpacing.xxs))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = showMoreOptionsButton && !isSelectionMode

                if (showPlayingIndicator) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = OrpheusSpacing.xs)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }

                if (showPlayingIndicator || showTrailingAction) {
                    Spacer(modifier = Modifier.width(OrpheusSpacing.sm))
                }

                if (showTrailingAction) {
                    OrpheusFilledIconButton(
                        onClick = { onMoreOptionsClick(song) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface
                        ),
                        shape = trailingButtonShape,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = OrpheusSpacing.xxs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.presentation_batch_g_list_cd_more_for_title, song.title),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
