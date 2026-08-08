package com.yuukifst.orpheus.data.backup.module

import android.content.Context
import com.google.gson.GsonBuilder
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictAction
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictMatchReason
import com.yuukifst.orpheus.data.database.MusicDao
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsModuleHandlerTest {

    private val context: Context = mockk(relaxed = true)
    private val playlistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val musicDao: MusicDao = mockk(relaxed = true)
    private val youTubePlaylistDao: YouTubePlaylistDao = mockk(relaxed = true)
    private val handler = PlaylistsModuleHandler(
        context = context,
        playlistPreferencesRepository = playlistPreferencesRepository,
        userPreferencesRepository = userPreferencesRepository,
        musicDao = musicDao,
        youTubePlaylistDao = youTubePlaylistDao,
        gson = GsonBuilder().serializeNulls().create()
    )

    @Test
    fun `detectConflicts matches by id first`() {
        val device = listOf(
            Playlist(id = "same", name = "Rock", songIds = listOf("1"), lastModified = 10L)
        )
        val backup = listOf(
            Playlist(id = "same", name = "Rock Renamed", songIds = listOf("2"), lastModified = 20L)
        )

        val conflicts = handler.detectConflicts(backup, device)

        assertEquals(1, conflicts.size)
        assertEquals(PlaylistConflictMatchReason.ID, conflicts[0].matchReason)
        assertEquals("same", conflicts[0].devicePlaylistId)
    }

    @Test
    fun `detectConflicts matches by name when ids differ`() {
        val device = listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("1"), lastModified = 10L)
        )
        val backup = listOf(
            Playlist(id = "backup-1", name = "rock", songIds = listOf("2"), lastModified = 20L)
        )

        val conflicts = handler.detectConflicts(backup, device)

        assertEquals(1, conflicts.size)
        assertEquals(PlaylistConflictMatchReason.NAME, conflicts[0].matchReason)
        assertEquals("device-1", conflicts[0].devicePlaylistId)
    }

    @Test
    fun `detectConflicts prefers newest device playlist when duplicate names`() {
        val device = listOf(
            Playlist(id = "old", name = "Rock", songIds = listOf("1"), lastModified = 10L),
            Playlist(id = "new", name = "Rock", songIds = listOf("2"), lastModified = 99L)
        )
        val backup = listOf(
            Playlist(id = "backup-1", name = "Rock", songIds = listOf("3"), lastModified = 20L)
        )

        val conflicts = handler.detectConflicts(backup, device)

        assertEquals(1, conflicts.size)
        assertEquals("new", conflicts[0].devicePlaylistId)
    }

    @Test
    fun `restore merge unions songs and keeps device playlist id`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("a", "b"))
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        val updated = slot<Playlist>()
        coEvery { playlistPreferencesRepository.updatePlaylist(capture(updated)) } returns Unit

        val payload = """
            {
              "playlists": [{
                "id": "backup-1",
                "name": "Rock",
                "songIds": ["b", "c"],
                "source": "LOCAL"
              }]
            }
        """.trimIndent()

        handler.restore(
            payload,
            mapOf("backup-1" to PlaylistConflictAction.MERGE)
        )

        coVerify(exactly = 0) { playlistPreferencesRepository.replaceAllPlaylists(any()) }
        assertEquals("device-1", updated.captured.id)
        assertEquals(listOf("a", "b", "c"), updated.captured.songIds)
        assertEquals("Rock", updated.captured.name)
        coVerify(exactly = 0) { playlistPreferencesRepository.setPlaylistsSortOption(any()) }
        coVerify(exactly = 0) { playlistPreferencesRepository.setPlaylistSongOrderModes(any()) }
    }

    @Test
    fun `restore replace overwrites songs on device playlist`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("a", "b"))
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        val updated = slot<Playlist>()
        coEvery { playlistPreferencesRepository.updatePlaylist(capture(updated)) } returns Unit

        // Same id as device → conflict even when names differ
        val payload = """
            {
              "playlists": [{
                "id": "device-1",
                "name": "Rock From Backup",
                "songIds": ["z"],
                "source": "LOCAL"
              }]
            }
        """.trimIndent()

        handler.restore(
            payload,
            mapOf("device-1" to PlaylistConflictAction.REPLACE)
        )

        assertEquals("device-1", updated.captured.id)
        assertEquals(listOf("z"), updated.captured.songIds)
        assertEquals("Rock From Backup", updated.captured.name)
    }

    @Test
    fun `restore ignore leaves device playlist unchanged`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("a"))
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()

        val payload = """
            {
              "playlists": [{
                "id": "backup-1",
                "name": "Rock",
                "songIds": ["z"],
                "source": "LOCAL"
              }]
            }
        """.trimIndent()

        handler.restore(
            payload,
            mapOf("backup-1" to PlaylistConflictAction.IGNORE)
        )

        coVerify(exactly = 0) { playlistPreferencesRepository.updatePlaylist(any()) }
        coVerify(exactly = 0) { playlistPreferencesRepository.createPlaylist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { playlistPreferencesRepository.replaceAllPlaylists(any()) }
    }

    @Test
    fun `restore creates non-conflicting playlist`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("a"))
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery {
            playlistPreferencesRepository.createPlaylist(
                name = any(),
                songIds = any(),
                isQueueGenerated = any(),
                coverImageUri = any(),
                coverColorArgb = any(),
                coverIconName = any(),
                coverShapeType = any(),
                coverShapeDetail1 = any(),
                coverShapeDetail2 = any(),
                coverShapeDetail3 = any(),
                coverShapeDetail4 = any(),
                customId = any(),
                source = any(),
                displayOrder = any(),
            )
        } returns Playlist(id = "jazz", name = "Jazz", songIds = emptyList())

        val payload = """
            {
              "playlists": [{
                "id": "jazz",
                "name": "Jazz",
                "songIds": ["j1"],
                "source": "LOCAL",
                "displayOrder": 3
              }]
            }
        """.trimIndent()

        handler.restore(payload, emptyMap())

        coVerify {
            playlistPreferencesRepository.createPlaylist(
                name = "Jazz",
                songIds = listOf("j1"),
                isQueueGenerated = false,
                coverImageUri = null,
                coverColorArgb = null,
                coverIconName = null,
                coverShapeType = null,
                coverShapeDetail1 = null,
                coverShapeDetail2 = null,
                coverShapeDetail3 = null,
                coverShapeDetail4 = null,
                customId = "jazz",
                source = "LOCAL",
                displayOrder = 3,
            )
        }
        coVerify(exactly = 0) { playlistPreferencesRepository.replaceAllPlaylists(any()) }
    }

    @Test
    fun `export includes displayOrder on playlists`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "p1", name = "A", songIds = listOf("1"), displayOrder = 2, source = "LOCAL"),
        )
        every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns flowOf(emptyMap())
        every { playlistPreferencesRepository.playlistsSortOptionFlow } returns flowOf("playlist_name_az")
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery { musicDao.getAllNavidromeSongIds() } returns emptyList()
        coEvery { musicDao.getAllJellyfinSongIds() } returns emptyList()

        val payload = handler.export()

        assertTrue(payload.contains("\"displayOrder\""))
        assertTrue(payload.contains("\"displayOrder\": 2") || payload.contains("\"displayOrder\":2"))
    }

    @Test
    fun `restore replace preserves displayOrder from backup`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Rock", songIds = listOf("a", "b"), displayOrder = 0)
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        val updated = slot<Playlist>()
        coEvery { playlistPreferencesRepository.updatePlaylist(capture(updated)) } returns Unit

        val payload = """
            {
              "playlists": [{
                "id": "device-1",
                "name": "Rock From Backup",
                "songIds": ["z"],
                "source": "LOCAL",
                "displayOrder": 5
              }]
            }
        """.trimIndent()

        handler.restore(
            payload,
            mapOf("device-1" to PlaylistConflictAction.REPLACE)
        )

        assertEquals(5, updated.captured.displayOrder)
    }

    @Test
    fun `rollback replaces all playlists from snapshot`() = runTest {
        val snapshot = """
            {
              "playlists": [{
                "id": "p1",
                "name": "Only",
                "songIds": ["1"],
                "source": "LOCAL"
              }],
              "playlistSongOrderModes": {},
              "playlistsSortOption": "playlist_name_az"
            }
        """.trimIndent()

        handler.rollback(snapshot)

        coVerify {
            playlistPreferencesRepository.replaceAllPlaylists(
                match { it.size == 1 && it[0].id == "p1" }
            )
        }
        assertTrue(true)
    }

    @Test
    fun `export includes youtube playlist tracks`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "p1", name = "YT Only", songIds = emptyList(), source = "LOCAL"),
        )
        every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns flowOf(emptyMap())
        every { playlistPreferencesRepository.playlistsSortOptionFlow } returns flowOf("playlist_name_az")
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery { musicDao.getAllNavidromeSongIds() } returns emptyList()
        coEvery { musicDao.getAllJellyfinSongIds() } returns emptyList()
        coEvery { youTubePlaylistDao.getAllOnce() } returns listOf(
            PlaylistYouTubeTrackEntity(
                playlistId = "p1",
                videoId = "abc123",
                sortOrder = 0,
                title = "Song",
                channelName = "Channel",
                thumbnailUrl = "https://example.com/t.jpg",
                durationMs = 180_000L,
                displayTitle = null,
            )
        )

        val payload = handler.export()

        assertTrue(payload.contains("\"youtubeTracks\""))
        assertTrue(payload.contains("\"abc123\""))
        assertTrue(payload.contains("\"videoId\""))
    }

    @Test
    fun `restore upserts youtube tracks for new playlist`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns emptyList()
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery {
            playlistPreferencesRepository.createPlaylist(
                name = any(),
                songIds = any(),
                isQueueGenerated = any(),
                coverImageUri = any(),
                coverColorArgb = any(),
                coverIconName = any(),
                coverShapeType = any(),
                coverShapeDetail1 = any(),
                coverShapeDetail2 = any(),
                coverShapeDetail3 = any(),
                coverShapeDetail4 = any(),
                customId = any(),
                source = any(),
                displayOrder = any(),
            )
        } returns Playlist(id = "p1", name = "YT Only", songIds = emptyList())

        val payload = """
            {
              "playlists": [{
                "id": "p1",
                "name": "YT Only",
                "songIds": [],
                "source": "LOCAL"
              }],
              "youtubeTracks": [{
                "playlistId": "p1",
                "videoId": "abc123",
                "sortOrder": 0,
                "title": "Song",
                "channelName": "Channel",
                "thumbnailUrl": "https://example.com/t.jpg",
                "durationMs": 180000
              }]
            }
        """.trimIndent()

        handler.restore(payload, emptyMap())

        coVerify {
            youTubePlaylistDao.replaceForPlaylist(
                "p1",
                match { tracks ->
                    tracks.size == 1 &&
                        tracks[0].videoId == "abc123" &&
                        tracks[0].playlistId == "p1" &&
                        tracks[0].title == "Song"
                }
            )
        }
    }

    @Test
    fun `restore merge unions youtube tracks onto device playlist id`() = runTest {
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = "device-1", name = "Mix", songIds = listOf("a"))
        )
        coEvery { musicDao.getAllLocalSongSummaries() } returns emptyList()
        coEvery { youTubePlaylistDao.observeForPlaylist("device-1") } returns flowOf(
            listOf(
                PlaylistYouTubeTrackEntity(
                    playlistId = "device-1",
                    videoId = "existing",
                    sortOrder = 0,
                    title = "Old",
                    channelName = "Ch",
                    thumbnailUrl = "",
                    durationMs = 1L,
                )
            )
        )
        coEvery { playlistPreferencesRepository.updatePlaylist(any()) } returns Unit

        val payload = """
            {
              "playlists": [{
                "id": "backup-1",
                "name": "Mix",
                "songIds": ["b"],
                "source": "LOCAL"
              }],
              "youtubeTracks": [{
                "playlistId": "backup-1",
                "videoId": "newvid",
                "sortOrder": 1,
                "title": "New",
                "channelName": "Ch",
                "thumbnailUrl": "",
                "durationMs": 2
              }]
            }
        """.trimIndent()

        handler.restore(
            payload,
            mapOf("backup-1" to PlaylistConflictAction.MERGE)
        )

        coVerify {
            youTubePlaylistDao.upsertAll(
                match { tracks ->
                    tracks.any { it.videoId == "newvid" && it.playlistId == "device-1" }
                }
            )
        }
    }
}
