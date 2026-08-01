package com.yuukifst.orpheus.data.playlist

import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistSongEntity
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistYouTubeMembershipTest {
    private val localPlaylistDao = mockk<LocalPlaylistDao>(relaxed = true)
    private val youTubePlaylistDao = mockk<YouTubePlaylistDao>(relaxed = true)
    private val mixedTrackResolver = mockk<PlaylistMixedTrackResolver>()
    private val membership = PlaylistYouTubeMembership(
        localPlaylistDao = localPlaylistDao,
        youTubePlaylistDao = youTubePlaylistDao,
        mixedTrackResolver = mixedTrackResolver,
    )

    private val sampleTrack = YouTubeTrack(
        videoId = "vid1",
        title = "YT Title",
        channelName = "Channel",
        thumbnailUrl = "https://thumb",
        durationMs = 180_000,
        displayTitle = "Display",
    )

    private val youtubeEntity = PlaylistYouTubeTrackEntity(
        playlistId = "pl-1",
        videoId = "vid1",
        sortOrder = 1,
        title = "YT Title",
        channelName = "Channel",
        thumbnailUrl = "https://thumb",
        durationMs = 180_000,
        displayTitle = "Display",
    )

    @Test
    fun addYouTubeTrackToPlaylist_upsertsWithNextSortOrderWhenAbsent() = runTest {
        val playlistId = "pl-1"
        coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(emptyList())
        coEvery { mixedTrackResolver.nextSortOrder(playlistId) } returns 3
        val upsertSlot = slot<List<PlaylistYouTubeTrackEntity>>()
        coEvery { youTubePlaylistDao.upsertAll(capture(upsertSlot)) } returns Unit

        membership.addYouTubeTrackToPlaylist(playlistId, sampleTrack)

        coVerify(exactly = 1) { youTubePlaylistDao.upsertAll(any()) }
        val entity = upsertSlot.captured.single()
        assertEquals(playlistId, entity.playlistId)
        assertEquals("vid1", entity.videoId)
        assertEquals(3, entity.sortOrder)
        assertEquals(sampleTrack.title, entity.title)
    }

    @Test
    fun addYouTubeTrackToPlaylist_skipsWhenAlreadyPresent() = runTest {
        val playlistId = "pl-1"
        coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(listOf(youtubeEntity))

        membership.addYouTubeTrackToPlaylist(playlistId, sampleTrack)

        coVerify(exactly = 0) { mixedTrackResolver.nextSortOrder(any()) }
        coVerify(exactly = 0) { youTubePlaylistDao.upsertAll(any()) }
    }

    @Test
    fun playlistIdsContainingVideo_returnsDistinctPlaylistIds() = runTest {
        coEvery { youTubePlaylistDao.getPlaylistIdsContainingVideo("vid1") } returns listOf("pl-1", "pl-2")

        val ids = membership.playlistIdsContainingVideo("vid1")

        assertEquals(setOf("pl-1", "pl-2"), ids)
    }

    @Test
    fun applyMixedOrder_assignsMixedIndicesToLocalAndYouTube() = runTest {
        val playlistId = "pl-1"
        coEvery { localPlaylistDao.observePlaylistSongs(playlistId) } returns flowOf(
            listOf(
                PlaylistSongEntity(playlistId, "local-a", 0),
                PlaylistSongEntity(playlistId, "local-b", 2),
            ),
        )
        coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(
            listOf(youtubeEntity.copy(sortOrder = 1)),
        )
        val localOrderSlot = slot<List<Pair<String, Int>>>()
        val youtubeSlot = slot<List<PlaylistYouTubeTrackEntity>>()
        coEvery { localPlaylistDao.replacePlaylistSongsWithOrder(playlistId, capture(localOrderSlot)) } returns Unit
        coEvery { youTubePlaylistDao.replaceForPlaylist(playlistId, capture(youtubeSlot)) } returns Unit

        membership.applyMixedOrder(
            playlistId = playlistId,
            orderedMediaIds = listOf("youtube_vid1", "local-a", "local-b"),
        )

        assertEquals(listOf("local-a" to 1, "local-b" to 2), localOrderSlot.captured)
        val youtubeTracks = youtubeSlot.captured
        assertEquals(1, youtubeTracks.size)
        assertEquals(0, youtubeTracks.single().sortOrder)
        assertEquals("vid1", youtubeTracks.single().videoId)
    }

    @Test
    fun applyMixedOrder_ignoresUnknownMediaIds() = runTest {
        val playlistId = "pl-1"
        coEvery { localPlaylistDao.observePlaylistSongs(playlistId) } returns flowOf(
            listOf(PlaylistSongEntity(playlistId, "local-a", 0)),
        )
        coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(emptyList())
        val localOrderSlot = slot<List<Pair<String, Int>>>()
        coEvery { localPlaylistDao.replacePlaylistSongsWithOrder(playlistId, capture(localOrderSlot)) } returns Unit

        membership.applyMixedOrder(
            playlistId = playlistId,
            orderedMediaIds = listOf("local-a", "youtube_missing", "local-unknown"),
        )

        assertEquals(listOf("local-a" to 0), localOrderSlot.captured)
        coVerify { youTubePlaylistDao.replaceForPlaylist(playlistId, emptyList()) }
    }
}
