package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeSearchMergeTest {

    private fun track(videoId: String) = YouTubeTrack(
        videoId = videoId,
        title = videoId,
        channelName = "ch",
        thumbnailUrl = "https://thumb",
        durationMs = 1_000L,
    )

    @Test
    fun `empty existing keeps incoming order`() {
        val incoming = listOf(track("a"), track("b"))
        assertEquals(incoming, mergeYouTubeSearchTracks(emptyList(), incoming))
    }

    @Test
    fun `duplicate videoId from incoming is dropped`() {
        val existing = listOf(track("a"))
        val incoming = listOf(track("a"), track("b"))
        assertEquals(listOf(track("a"), track("b")), mergeYouTubeSearchTracks(existing, incoming))
    }

    @Test
    fun `empty incoming leaves existing unchanged`() {
        val existing = listOf(track("a"), track("b"))
        assertEquals(existing, mergeYouTubeSearchTracks(existing, emptyList()))
    }
}
