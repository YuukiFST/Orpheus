package com.yuukifst.orpheus.utils

import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubePlaybackUtilsTest {

    @Test
    fun `toPlaylistMixedTrack maps YouTube song to YouTube mixed entry`() {
        val song = Song(
            id = "youtube_abc123",
            title = "Title",
            artist = "Channel",
            artistId = -1L,
            album = "YouTube",
            albumId = -1L,
            path = "",
            contentUriString = "",
            albumArtUriString = "https://example.com/thumb.jpg",
            duration = 1234L,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )

        val mixed = song.toPlaylistMixedTrack(0)

        assertTrue(mixed is PlaylistMixedTrack.YouTube)
        val track = (mixed as PlaylistMixedTrack.YouTube).track
        assertEquals("abc123", track.videoId)
        assertEquals("Title", track.title)
        assertEquals("Channel", track.channelName)
        assertEquals(1234L, track.durationMs)
    }
}
