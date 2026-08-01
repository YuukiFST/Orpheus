package com.yuukifst.orpheus.data.stats

import com.google.common.truth.Truth.assertThat
import com.yuukifst.orpheus.data.database.SongEngagementEntity
import com.yuukifst.orpheus.data.model.Song
import org.junit.jupiter.api.Test

class TopPlayedRankingTest {

    @Test
    fun `rankTopPlayed sorts by play count then last played timestamp`() {
        val engagements = listOf(
            engagement("1", playCount = 5, lastPlayedTimestamp = 100L),
            engagement("2", playCount = 10, lastPlayedTimestamp = 50L),
            engagement("3", playCount = 10, lastPlayedTimestamp = 200L),
            engagement("4", playCount = 1, lastPlayedTimestamp = 999L),
        )
        val songs = mapOf(
            "1" to song("1", "Alpha"),
            "2" to song("2", "Bravo"),
            "3" to song("3", "Charlie"),
            "4" to song("4", "Delta"),
        )

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.ALL, limit = 20)

        assertThat(result.map { it.songId }).containsExactly("3", "2", "1", "4").inOrder()
        assertThat(result.map { it.playCount }).containsExactly(10, 10, 5, 1).inOrder()
    }

    @Test
    fun `rankTopPlayed LOCAL filter excludes YouTube ids`() {
        val engagements = listOf(
            engagement("42", playCount = 20),
            engagement("youtube_abc", playCount = 100),
            engagement("7", playCount = 15),
        )
        val songs = mapOf(
            "42" to song("42"),
            "youtube_abc" to song("youtube_abc"),
            "7" to song("7"),
        )

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.LOCAL, limit = 20)

        assertThat(result.map { it.songId }).containsExactly("42", "7").inOrder()
    }

    @Test
    fun `rankTopPlayed YOUTUBE filter includes only YouTube ids`() {
        val engagements = listOf(
            engagement("42", playCount = 20),
            engagement("youtube_abc", playCount = 100),
            engagement("youtube_xyz", playCount = 50),
        )
        val songs = mapOf(
            "42" to song("42"),
            "youtube_abc" to song("youtube_abc"),
            "youtube_xyz" to song("youtube_xyz"),
        )

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.YOUTUBE, limit = 20)

        assertThat(result.map { it.songId }).containsExactly("youtube_abc", "youtube_xyz").inOrder()
    }

    @Test
    fun `rankTopPlayed ALL filter includes mixed ids`() {
        val engagements = listOf(
            engagement("42", playCount = 20),
            engagement("youtube_abc", playCount = 100),
        )
        val songs = mapOf(
            "42" to song("42"),
            "youtube_abc" to song("youtube_abc"),
        )

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.ALL, limit = 20)

        assertThat(result.map { it.songId }).containsExactly("youtube_abc", "42").inOrder()
    }

    @Test
    fun `rankTopPlayed drops engagements without resolvable song metadata`() {
        val engagements = listOf(
            engagement("1", playCount = 50),
            engagement("missing", playCount = 40),
            engagement("2", playCount = 30),
        )
        val songs = mapOf(
            "1" to song("1", "Known One"),
            "2" to song("2", "Known Two"),
        )

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.ALL, limit = 20)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.songId }).containsExactly("1", "2").inOrder()
        assertThat(result.first().title).isEqualTo("Known One")
        assertThat(result.first().artist).isEqualTo("Artist")
    }

    @Test
    fun `rankTopPlayed respects limit 20`() {
        val engagements = (1..30).map { index ->
            engagement(index.toString(), playCount = index)
        }
        val songs = engagements.associate { engagement ->
            engagement.songId to song(engagement.songId)
        }

        val result = rankTopPlayed(engagements, songs, TopPlayedFilter.ALL, limit = 20)

        assertThat(result).hasSize(20)
        assertThat(result.first().songId).isEqualTo("30")
        assertThat(result.last().songId).isEqualTo("11")
    }

    @Test
    fun `rankTopPlayed maps song metadata into entry`() {
        val song = song(
            id = "1",
            title = "Track Title",
            artist = "Primary Artist",
            albumArtUriString = "content://art/1",
        )

        val result = rankTopPlayed(
            engagements = listOf(engagement("1", playCount = 12)),
            songsById = mapOf("1" to song),
            filter = TopPlayedFilter.ALL,
            limit = 20,
        )

        assertThat(result.single()).isEqualTo(
            TopPlayedEntry(
                songId = "1",
                title = "Track Title",
                artist = "Primary Artist",
                albumArtUri = "content://art/1",
                playCount = 12,
            )
        )
    }

    private fun engagement(
        songId: String,
        playCount: Int = 0,
        lastPlayedTimestamp: Long = 0L,
    ) = SongEngagementEntity(
        songId = songId,
        playCount = playCount,
        totalPlayDurationMs = 0L,
        lastPlayedTimestamp = lastPlayedTimestamp,
    )

    private fun song(
        id: String,
        title: String = "Title-$id",
        artist: String = "Artist",
        albumArtUriString: String? = null,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = "Album",
        albumId = 0L,
        path = "/music/$id.mp3",
        contentUriString = "content://$id",
        albumArtUriString = albumArtUriString,
        duration = 180_000L,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )
}
