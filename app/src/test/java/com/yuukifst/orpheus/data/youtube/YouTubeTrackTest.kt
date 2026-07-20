package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeTrackTest {
    @Test
    fun effectiveTitle_prefersDisplayTitle() {
        val track = YouTubeTrack(
            videoId = "abc123",
            title = "Raw YouTube Title",
            channelName = "Channel",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationMs = 180_000,
            displayTitle = "My Rename",
        )
        assertEquals("My Rename", track.effectiveTitle)
        assertEquals("youtube_abc123", track.mediaId)
    }

    @Test
    fun effectiveTitle_fallsBackToRawTitle() {
        val track = YouTubeTrack(
            videoId = "xyz",
            title = "Song Name",
            channelName = "Artist",
            thumbnailUrl = "",
            durationMs = 60_000,
        )
        assertEquals("Song Name", track.effectiveTitle)
    }
}
