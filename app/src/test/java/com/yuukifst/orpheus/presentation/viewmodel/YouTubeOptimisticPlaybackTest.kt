package com.yuukifst.orpheus.presentation.viewmodel

import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.utils.isYouTubeMediaId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeOptimisticPlaybackTest {

    @Test
    fun optimisticUiForTrackMapsSearchMetadata() {
        val track = YouTubeTrack(
            videoId = "abc123",
            title = "Song Title",
            channelName = "Channel",
            thumbnailUrl = "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            durationMs = 180_000L,
        )

        val optimistic = optimisticUiForTrack(track)

        assertEquals(track.mediaId, optimistic.song.id)
        assertEquals(track.effectiveTitle, optimistic.song.title)
        assertEquals(track.channelName, optimistic.song.artist)
        assertEquals(track.thumbnailUrl, optimistic.song.albumArtUriString)
        assertEquals(track.durationMs, optimistic.song.duration)
        assertEquals(0, optimistic.mediaItemIndex)
        assertEquals("YouTube", optimistic.queueName)
    }

    @Test
    fun blankThumbnailYieldsNullAlbumArt() {
        val track = YouTubeTrack(
            videoId = "xyz",
            title = "t",
            channelName = "c",
            thumbnailUrl = "   ",
            durationMs = 1L,
        )

        val optimistic = optimisticUiForTrack(track)

        assertNull(optimistic.song.albumArtUriString)
    }

    @Test
    fun negativeIndexCoercedToZero() {
        val track = YouTubeTrack(
            videoId = "x",
            title = "t",
            channelName = "c",
            thumbnailUrl = "u",
            durationMs = 1L,
        )

        val optimistic = optimisticUiForTrack(track, index = -3)

        assertEquals(0, optimistic.mediaItemIndex)
    }

    @Test
    fun optimisticSongIdIsRecognizedAsYouTubeMediaId() {
        val track = YouTubeTrack(
            videoId = "vid99",
            title = "t",
            channelName = "c",
            thumbnailUrl = "u",
            durationMs = 1L,
        )

        val optimistic = optimisticUiForTrack(track)

        assertTrue(optimistic.song.id.isYouTubeMediaId())
    }
}
