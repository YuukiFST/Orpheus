package com.yuukifst.orpheus.presentation.viewmodel

import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeRecordPlayedTracksTest {

    private fun youtube(videoId: String) = YouTubeTrack(
        videoId = videoId,
        title = videoId,
        channelName = "ch",
        thumbnailUrl = "https://thumb",
        durationMs = 1_000L,
    )

    private fun localSong(id: String) = Song(
        id = id,
        title = id,
        artist = "a",
        artistId = 0L,
        album = "alb",
        albumId = 0L,
        path = "/tmp/$id",
        contentUriString = "content://$id",
        albumArtUriString = null,
        duration = 1_000L,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )

    @Test
    fun `empty mixed list records nothing`() {
        assertEquals(emptyList<YouTubeTrack>(), youtubeTracksToRecord(emptyList()))
    }

    @Test
    fun `mixed local and youtube keeps youtube order`() {
        val ytA = youtube("a")
        val ytB = youtube("b")
        val tracks = listOf(
            PlaylistMixedTrack.Local(localSong("local-1"), sortOrder = 0),
            PlaylistMixedTrack.YouTube(ytA, sortOrder = 1),
            PlaylistMixedTrack.Local(localSong("local-2"), sortOrder = 2),
            PlaylistMixedTrack.YouTube(ytB, sortOrder = 3),
        )
        assertEquals(listOf(ytA, ytB), youtubeTracksToRecord(tracks))
    }

    @Test
    fun `all youtube playlist maps one to one`() {
        val ytA = youtube("a")
        val ytB = youtube("b")
        val tracks = listOf(
            PlaylistMixedTrack.YouTube(ytA, sortOrder = 0),
            PlaylistMixedTrack.YouTube(ytB, sortOrder = 1),
        )
        assertEquals(listOf(ytA, ytB), youtubeTracksToRecord(tracks))
    }
}
