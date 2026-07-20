package com.yuukifst.orpheus.data.model

import androidx.compose.runtime.Immutable
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

@Immutable
sealed class PlaylistMixedTrack {
    abstract val sortOrder: Int

    @Immutable
    data class Local(
        val song: Song,
        override val sortOrder: Int,
    ) : PlaylistMixedTrack()

    @Immutable
    data class YouTube(
        val track: YouTubeTrack,
        override val sortOrder: Int,
    ) : PlaylistMixedTrack()
}
