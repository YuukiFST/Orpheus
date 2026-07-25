package com.yuukifst.orpheus.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.yuukifst.orpheus.data.model.Song

/**
 * Fields from [StablePlayerState] that the player sheet shell needs without
 * subscribing to high-frequency updates such as buffering or seek duration.
 */
@Immutable
data class StablePlayerSheetShellSlice(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
)

/**
 * Playback-control fields used by mini player, full player chrome, and queue sheet.
 * Excludes lyrics, buffering, and duration ticks.
 */
@Immutable
data class StablePlayerControlsSlice(
    val currentSong: Song? = null,
    val currentMediaItemIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isShuffleTransitionInProgress: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

fun StablePlayerState.toSheetShellSlice(): StablePlayerSheetShellSlice = StablePlayerSheetShellSlice(
    currentSong = currentSong,
    isPlaying = isPlaying,
)

fun StablePlayerState.toControlsSlice(): StablePlayerControlsSlice = StablePlayerControlsSlice(
    currentSong = currentSong,
    currentMediaItemIndex = currentMediaItemIndex,
    isPlaying = isPlaying,
    isShuffleEnabled = isShuffleEnabled,
    isShuffleTransitionInProgress = isShuffleTransitionInProgress,
    repeatMode = repeatMode,
)
