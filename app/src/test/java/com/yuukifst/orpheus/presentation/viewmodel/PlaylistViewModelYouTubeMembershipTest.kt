package com.yuukifst.orpheus.presentation.viewmodel

import android.content.Context
import com.yuukifst.orpheus.MainCoroutineExtension
import com.yuukifst.orpheus.data.DailyMixManager
import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistSongEntity
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import com.yuukifst.orpheus.presentation.viewmodel.toPlaybackSong

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

    private fun PlaylistViewModel.seedCurrentMixedPlaylist(
        songs: List<Song>,
        mixedTracks: List<PlaylistMixedTrack>,
    ) {
        val field = PlaylistViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<PlaylistUiState>
        stateFlow.value = stateFlow.value.copy(
            currentPlaylistDetails = Playlist(id = playlistId, name = "Mixed", songIds = listOf("local-a")),
            currentPlaylistSongs = songs,
            currentPlaylistMixedTracks = mixedTracks,
            playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
        )
    }

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

    @Test
    fun reorderSongsInPlaylist_mixedPlaylist_persistsYouTubeSortOrderAtNewIndex() = runTest {
        val localPlaylistDao = mockk<LocalPlaylistDao>(relaxed = true)
        val localSong = Song(
            id = "local-a",
            title = "Local",
            artist = "Artist",
            artistId = 1L,
            album = "Album",
            albumId = 1L,
            path = "/music/local.mp3",
            contentUriString = "content://local",
            albumArtUriString = null,
            duration = 200_000L,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )
        val youtubeTrack = YouTubeTrack(
            videoId = "vid1",
            title = "YT Title",
            channelName = "Channel",
            thumbnailUrl = "https://thumb",
            durationMs = 180_000L,
        )
        val mixedTracks = listOf(
            PlaylistMixedTrack.Local(localSong, sortOrder = 0),
            PlaylistMixedTrack.YouTube(youtubeTrack, sortOrder = 1),
        )
        val songs = mixedTracks.map { track ->
            when (track) {
                is PlaylistMixedTrack.Local -> track.song
                is PlaylistMixedTrack.YouTube -> track.track.toPlaybackSong()
            }
        }
        val youtubeEntity = PlaylistYouTubeTrackEntity(
            playlistId = playlistId,
            videoId = "vid1",
            sortOrder = 1,
            title = youtubeTrack.title,
            channelName = youtubeTrack.channelName,
            thumbnailUrl = youtubeTrack.thumbnailUrl,
            durationMs = youtubeTrack.durationMs,
            displayTitle = null,
        )

        coEvery { localPlaylistDao.observePlaylistSongs(playlistId) } returns flowOf(
            listOf(PlaylistSongEntity(playlistId, "local-a", 0)),
        )
        coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(listOf(youtubeEntity))
        val youtubeSlot = slot<List<PlaylistYouTubeTrackEntity>>()
        coEvery { youTubePlaylistDao.replaceForPlaylist(playlistId, capture(youtubeSlot)) } returns Unit

        val realMembership = PlaylistYouTubeMembership(
            localPlaylistDao = localPlaylistDao,
            youTubePlaylistDao = youTubePlaylistDao,
            mixedTrackResolver = mixedTrackResolver,
        )
        viewModel = PlaylistViewModel(
            playlistPreferencesRepository = playlistPreferencesRepository,
            musicRepository = musicRepository,
            dailyMixManager = dailyMixManager,
            m3uManager = m3uManager,
            mixedTrackResolver = mixedTrackResolver,
            youTubePlaylistDao = youTubePlaylistDao,
            playlistYouTubeMembership = realMembership,
            youTubeCachedTrackRepository = youTubeCachedTrackRepository,
            playbackController = playbackController,
            context = context,
        )
        viewModel.seedCurrentMixedPlaylist(songs, mixedTracks)

        viewModel.reorderSongsInPlaylist(playlistId, fromIndex = 1, toIndex = 0)
        advanceUntilIdle()

        assertEquals(0, youtubeSlot.captured.single().sortOrder)
        coVerify(exactly = 1) {
            playlistPreferencesRepository.reorderSongsInPlaylist(playlistId, listOf("local-a"))
        }
        coVerify(exactly = 1) {
            playlistPreferencesRepository.setPlaylistSongOrderMode(playlistId, "manual")
        }
        assertEquals(
            listOf("youtube_vid1", "local-a"),
            viewModel.uiState.value.currentPlaylistSongs.map { it.id },
        )
        assertEquals(
            listOf("local-a"),
            viewModel.uiState.value.currentPlaylistDetails?.songIds,
        )
    }
}
