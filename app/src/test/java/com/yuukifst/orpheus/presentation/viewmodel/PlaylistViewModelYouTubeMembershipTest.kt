package com.yuukifst.orpheus.presentation.viewmodel

import android.content.Context
import com.yuukifst.orpheus.MainCoroutineExtension
import com.yuukifst.orpheus.data.DailyMixManager
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.playlist.M3uManager
import com.yuukifst.orpheus.data.playlist.PlaylistMixedTrackResolver
import com.yuukifst.orpheus.data.playlist.PlaylistYouTubeMembership
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.repository.MusicRepository
import com.yuukifst.orpheus.data.youtube.YouTubeCachedTrackRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaylistViewModelYouTubeMembershipTest {

    private val playlistPreferencesRepository = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val dailyMixManager = mockk<DailyMixManager>(relaxed = true)
    private val m3uManager = mockk<M3uManager>(relaxed = true)
    private val mixedTrackResolver = mockk<PlaylistMixedTrackResolver>(relaxed = true)
    private val youTubePlaylistDao = mockk<YouTubePlaylistDao>(relaxed = true)
    private val playlistYouTubeMembership = mockk<PlaylistYouTubeMembership>(relaxed = true)
    private val youTubeCachedTrackRepository = mockk<YouTubeCachedTrackRepository>(relaxed = true)
    private val playbackController = mockk<YouTubePlaybackController>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var viewModel: PlaylistViewModel

    private val playlistId = "pl-1"
    private val mediaId = "youtube_vid1"
    private val cachedSong = Song(
        id = mediaId,
        title = "YT Title",
        artist = "Channel",
        artistId = -1L,
        album = "",
        albumId = -1L,
        path = "",
        contentUriString = "",
        albumArtUriString = "https://thumb",
        duration = 180_000L,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )

    @BeforeEach
    fun setUp() {
        every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns flowOf(emptyMap())
        every { playlistPreferencesRepository.playlistsSortOptionFlow } returns flowOf("PlaylistNameAZ")
        every { playlistPreferencesRepository.userPlaylistsFlow } returns flowOf(emptyList())

        viewModel = PlaylistViewModel(
            playlistPreferencesRepository = playlistPreferencesRepository,
            musicRepository = musicRepository,
            dailyMixManager = dailyMixManager,
            m3uManager = m3uManager,
            mixedTrackResolver = mixedTrackResolver,
            youTubePlaylistDao = youTubePlaylistDao,
            playlistYouTubeMembership = playlistYouTubeMembership,
            youTubeCachedTrackRepository = youTubeCachedTrackRepository,
            playbackController = playbackController,
            context = context,
        )
    }

    @Test
    fun addOrRemoveSongFromPlaylists_youtubeAdd_usesMembershipNotLocalPrefs() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = playlistId, name = "Test", songIds = emptyList()),
        )
        coEvery { youTubeCachedTrackRepository.getSongsByMediaIds(listOf(mediaId)) } returns listOf(cachedSong)
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo("vid1") } returns emptySet()

        viewModel.addOrRemoveSongFromPlaylists(mediaId, listOf(playlistId), currentPlaylistId = null)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playlistYouTubeMembership.addYouTubeTrackToPlaylist(
                playlistId,
                YouTubeTrack(
                    videoId = "vid1",
                    title = "YT Title",
                    channelName = "Channel",
                    thumbnailUrl = "https://thumb",
                    durationMs = 180_000L,
                ),
            )
        }
        coVerify(exactly = 0) {
            playlistPreferencesRepository.addOrRemoveSongFromPlaylists(mediaId, any())
        }
        coVerify(exactly = 0) {
            playlistPreferencesRepository.addSongsToPlaylist(playlistId, listOf(mediaId))
        }
    }

    @Test
    fun addOrRemoveSongFromPlaylists_youtubeRemove_clearsYoutubeTableAndLegacyRow() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = playlistId, name = "Test", songIds = listOf(mediaId)),
        )
        coEvery { youTubeCachedTrackRepository.getSongsByMediaIds(listOf(mediaId)) } returns listOf(cachedSong)
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo("vid1") } returns setOf(playlistId)

        viewModel.addOrRemoveSongFromPlaylists(mediaId, emptyList(), currentPlaylistId = null)
        advanceUntilIdle()

        coVerify(exactly = 1) { youTubePlaylistDao.removeTrack(playlistId, "vid1") }
        coVerify(exactly = 1) {
            playlistPreferencesRepository.removeSongFromPlaylist(playlistId, mediaId)
        }
        coVerify(exactly = 0) { playlistYouTubeMembership.addYouTubeTrackToPlaylist(any(), any()) }
    }

    @Test
    fun addOrRemoveSongFromPlaylists_youtubeRemove_worksWhenCacheMiss() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = playlistId, name = "Test", songIds = listOf(mediaId)),
        )
        coEvery { youTubeCachedTrackRepository.getSongsByMediaIds(listOf(mediaId)) } returns emptyList()
        coEvery { musicRepository.getSongsByIds(listOf(mediaId)) } returns flowOf(emptyList())
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo("vid1") } returns setOf(playlistId)

        viewModel.addOrRemoveSongFromPlaylists(mediaId, emptyList(), currentPlaylistId = null)
        advanceUntilIdle()

        coVerify(exactly = 1) { youTubePlaylistDao.removeTrack(playlistId, "vid1") }
        coVerify(exactly = 1) {
            playlistPreferencesRepository.removeSongFromPlaylist(playlistId, mediaId)
        }
        coVerify(exactly = 0) { playlistYouTubeMembership.addYouTubeTrackToPlaylist(any(), any()) }
    }

    @Test
    fun createPlaylist_withYoutubeIds_addsViaMembershipNotLocalSongIds() = runTest {
        val localId = "local-1"
        val createdPlaylist = Playlist(
            id = "new-pl",
            name = "Mixed",
            songIds = listOf(localId),
        )
        val trackSlot = slot<YouTubeTrack>()
        coEvery {
            playlistPreferencesRepository.createPlaylist(
                name = "Mixed",
                songIds = listOf(localId),
                isQueueGenerated = false,
                coverImageUri = null,
                coverColorArgb = null,
                coverIconName = null,
                coverShapeType = null,
                coverShapeDetail1 = null,
                coverShapeDetail2 = null,
                coverShapeDetail3 = null,
                coverShapeDetail4 = null,
                source = "LOCAL",
            )
        } returns createdPlaylist
        coEvery {
            youTubeCachedTrackRepository.getSongsByMediaIds(listOf(mediaId))
        } returns listOf(cachedSong)
        coEvery {
            playlistYouTubeMembership.addYouTubeTrackToPlaylist("new-pl", capture(trackSlot))
        } returns Unit

        viewModel.createPlaylist(
            name = "Mixed",
            songIds = listOf(localId, mediaId),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            playlistPreferencesRepository.createPlaylist(
                name = "Mixed",
                songIds = listOf(localId),
                isQueueGenerated = false,
                coverImageUri = null,
                coverColorArgb = null,
                coverIconName = null,
                coverShapeType = null,
                coverShapeDetail1 = null,
                coverShapeDetail2 = null,
                coverShapeDetail3 = null,
                coverShapeDetail4 = null,
                source = "LOCAL",
            )
        }
        coVerify(exactly = 1) {
            playlistYouTubeMembership.addYouTubeTrackToPlaylist("new-pl", any())
        }
        assertEquals("vid1", trackSlot.captured.videoId)
    }

    @Test
    fun playlistIdsContainingSong_delegatesToMembership() = runTest {
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo("vid1") } returns setOf(playlistId)

        val ids = viewModel.playlistIdsContainingSong(mediaId)

        assertEquals(setOf(playlistId), ids)
    }
}
