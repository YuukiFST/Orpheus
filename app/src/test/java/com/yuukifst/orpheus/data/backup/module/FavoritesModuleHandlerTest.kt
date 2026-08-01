package com.yuukifst.orpheus.data.backup.module

import com.google.gson.GsonBuilder
import com.yuukifst.orpheus.data.database.FavoritesDao
import com.yuukifst.orpheus.data.database.FavoritesEntity
import com.yuukifst.orpheus.data.database.LikedOrderDao
import com.yuukifst.orpheus.data.database.LikedOrderEntity
import com.yuukifst.orpheus.data.database.YouTubeCachedTrackDao
import com.yuukifst.orpheus.data.database.YouTubeCachedTrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesModuleHandlerTest {

    private val favoritesDao: FavoritesDao = mockk(relaxed = true)
    private val youTubeCachedTrackDao: YouTubeCachedTrackDao = mockk(relaxed = true)
    private val likedOrderDao: LikedOrderDao = mockk(relaxed = true)
    private val handler = FavoritesModuleHandler(
        favoritesDao = favoritesDao,
        youTubeCachedTrackDao = youTubeCachedTrackDao,
        likedOrderDao = likedOrderDao,
        gson = GsonBuilder().serializeNulls().create()
    )

    @Test
    fun `countEntries sums local and youtube favorites`() = runTest {
        coEvery { favoritesDao.getAllFavoritesOnce() } returns listOf(
            FavoritesEntity(songId = 1L, isFavorite = true, timestamp = 1L),
            FavoritesEntity(songId = 2L, isFavorite = true, timestamp = 2L)
        )
        coEvery { youTubeCachedTrackDao.getFavoriteTracksOnce() } returns listOf(
            YouTubeCachedTrackEntity(
                videoId = "abc",
                title = "Song",
                channelName = "Artist",
                thumbnailUrl = "",
                durationMs = 0L,
                displayTitle = null,
                isFavorite = true,
                lastPlayedAt = 0L,
                favoritedAt = 1L
            )
        )

        assertEquals(3, handler.countEntries())
    }

    @Test
    fun `export uses v2 envelope with local and youtube`() = runTest {
        coEvery { favoritesDao.getAllFavoritesOnce() } returns listOf(
            FavoritesEntity(songId = 123L, isFavorite = true, timestamp = 1_700_000_000_000L)
        )
        coEvery { youTubeCachedTrackDao.getFavoriteTracksOnce() } returns listOf(
            YouTubeCachedTrackEntity(
                videoId = "abc",
                title = "Song",
                channelName = "Artist",
                thumbnailUrl = "https://example.com/a.jpg",
                durationMs = 180_000L,
                displayTitle = null,
                isFavorite = true,
                lastPlayedAt = 1L,
                favoritedAt = 1_700_000_000_000L
            )
        )
        coEvery { likedOrderDao.getAllOrdered() } returns listOf(
            LikedOrderEntity(mediaId = "123", sortOrder = 0),
            LikedOrderEntity(mediaId = "youtube_abc", sortOrder = 1),
        )

        val payload = handler.export()

        assertTrue(payload.contains("\"version\""))
        assertTrue(payload.contains("\"local\""))
        assertTrue(payload.contains("\"youtube\""))
        assertTrue(payload.contains("\"order\""))
        assertTrue(payload.contains("\"123\""))
        assertTrue(payload.contains("\"youtube_abc\""))
        assertTrue(payload.contains("\"videoId\""))
        assertTrue(payload.contains("\"songId\""))
        assertFalse(payload.contains("\"song_id\""))
    }

    @Test
    fun `restore v2 merges liked order when present`() = runTest {
        coEvery { favoritesDao.getAllFavoritesOnce() } returns listOf(
            FavoritesEntity(songId = 123L, isFavorite = true, timestamp = 1L),
        )
        coEvery { youTubeCachedTrackDao.getFavoriteTracksOnce() } returns emptyList()
        coEvery { likedOrderDao.getAllOrdered() } returns emptyList()
        val payload = """
            {
              "version": 2,
              "local": [{"songId": 123, "isFavorite": true, "timestamp": 1}],
              "youtube": [],
              "order": ["123", "456"]
            }
        """.trimIndent()

        handler.restore(payload)

        coVerify {
            likedOrderDao.replaceAllOrdered(listOf("123"))
        }
    }

    @Test
    fun `restore v2 without order leaves liked order untouched`() = runTest {
        val payload = """
            {"version":2,"local":[{"songId":123,"isFavorite":true,"timestamp":1}],"youtube":[]}
        """.trimIndent()

        handler.restore(payload)

        coVerify(exactly = 0) { likedOrderDao.replaceAllOrdered(any()) }
        coVerify(exactly = 0) { likedOrderDao.clear() }
    }

    @Test
    fun `restore legacy array merges local favorites without clearAll`() = runTest {
        val payload = """[{"song_id": 123, "is_favorite": true, "added_at": 1700000000000}]"""

        handler.restore(payload)

        coVerify(exactly = 0) { favoritesDao.clearAll() }
        coVerify(exactly = 0) { favoritesDao.replaceAll(any()) }
        coVerify(atLeast = 1) {
            favoritesDao.setFavorite(
                FavoritesEntity(
                    songId = 123L,
                    isFavorite = true,
                    timestamp = 1_700_000_000_000L
                )
            )
        }
    }

    @Test
    fun `restore v2 merges youtube favorites via upsert`() = runTest {
        coEvery { youTubeCachedTrackDao.getByVideoId("abc") } returns null
        val payload = """
            {
              "version": 2,
              "local": [],
              "youtube": [{
                "videoId": "abc",
                "title": "Song",
                "channelName": "Artist",
                "thumbnailUrl": "https://example.com/a.jpg",
                "durationMs": 180000,
                "favoritedAt": 1700000000000
              }]
            }
        """.trimIndent()

        handler.restore(payload)

        coVerify {
            youTubeCachedTrackDao.upsert(
                match {
                    it.videoId == "abc" && it.isFavorite && it.title == "Song"
                }
            )
        }
    }

    @Test
    fun `restore v2 does not remove existing local favorites absent from payload`() = runTest {
        val payload = """
            {"version":2,"local":[{"songId":123,"isFavorite":true,"timestamp":1}],"youtube":[]}
        """.trimIndent()

        handler.restore(payload)

        coVerify(exactly = 0) { favoritesDao.clearAll() }
        coVerify(exactly = 0) { favoritesDao.removeFavorite(any()) }
    }

    @Test
    fun `rollback replaces local favorites and liked order from snapshot`() = runTest {
        val snapshot = """
            {
              "version": 2,
              "local": [{"songId": 1, "isFavorite": true, "timestamp": 1}],
              "youtube": [],
              "order": ["1", "youtube_abc"]
            }
        """.trimIndent()
        coEvery { youTubeCachedTrackDao.getFavoriteTracksOnce() } returns emptyList()

        handler.rollback(snapshot)

        coVerify {
            favoritesDao.replaceAll(
                listOf(FavoritesEntity(songId = 1L, isFavorite = true, timestamp = 1L))
            )
        }
        coVerify {
            likedOrderDao.replaceAllOrdered(listOf("1", "youtube_abc"))
        }
    }

    @Test
    fun `rollback clears liked order when snapshot has no order field`() = runTest {
        val snapshot = """
            {"version":2,"local":[{"songId":1,"isFavorite":true,"timestamp":1}],"youtube":[]}
        """.trimIndent()
        coEvery { youTubeCachedTrackDao.getFavoriteTracksOnce() } returns emptyList()

        handler.rollback(snapshot)

        coVerify {
            favoritesDao.replaceAll(
                listOf(FavoritesEntity(songId = 1L, isFavorite = true, timestamp = 1L))
            )
        }
        coVerify { likedOrderDao.clear() }
    }
}
