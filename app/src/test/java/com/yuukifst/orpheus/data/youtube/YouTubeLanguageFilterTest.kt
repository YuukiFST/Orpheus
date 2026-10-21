package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.preferences.YouTubeResultLanguage
import com.yuukifst.orpheus.data.preferences.resolveYouTubeResultLanguage
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YouTubeLanguageFilterTest {

    private fun DetectedTextLanguage.label(): String = when (this) {
        is DetectedTextLanguage.Known -> language.name
        DetectedTextLanguage.Other -> "OTHER"
        DetectedTextLanguage.Undetermined -> "UNDETERMINED"
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Você não vai acreditar no que aconteceu|PORTUGUESE",
            "Pão de queijo mineiro|PORTUGUESE",
            "Como fazer isso em casa pra iniciantes|PORTUGUESE",
            "Mano Brown fala sobre o Brasil e a galera|PORTUGUESE",
            "Podcast com Fulano - EP 12|PORTUGUESE",
            "Cómo hacer pan en casa paso a paso|SPANISH",
            "Eros Ramazzotti - Più bella cosa|ITALIAN",
            "Comment devenir riche ? Les secrets des millionnaires|FRENCH",
            "Warum ich nicht mehr auf YouTube bin|GERMAN",
            "Um... what is this? Pro tips for beginners|ENGLISH",
            "Daft Punk - Get Lucky (Official Audio)|ENGLISH",
            "BTS (방탄소년단) Dynamite Official MV|ENGLISH",
            "Huberman Lab Podcast How to Improve Sleep https://www.youtube.com/watch|ENGLISH",
            "Mötley Crüe - Kickstart My Heart (Official Music Video)|UNDETERMINED",
            "Bad Bunny - Tití Me Preguntó|UNDETERMINED",
            "La Bamba|UNDETERMINED",
            "Sơn Tùng M-TP - Chúng Ta Của Hiện Tại|OTHER",
            "米津玄師 - Lemon|OTHER",
        ],
    )
    fun detectsResultLanguage(text: String, expected: String) {
        assertEquals(expected, detectTextLanguage(text).label())
    }

    private fun track(videoId: String, title: String, channel: String, description: String? = null) = YouTubeTrack(
        videoId = videoId,
        title = title,
        channelName = channel,
        thumbnailUrl = "",
        durationMs = 0L,
        descriptionSnippet = description,
    )

    @Test
    fun `English filter reads the description and keeps results with no language signal`() {
        val englishEpisode = track(
            "en1", "Podcast #212", "Huberman Lab",
            description = "In this episode we talk about how to improve your sleep",
        )
        val portugueseEpisode = track(
            "pt1", "Flow Podcast #300", "Flow Podcast",
            description = "Neste episódio conversamos com o convidado sobre a carreira dele",
        )
        val artistOnly = track("neutral1", "Get Lucky", "Daft Punk")
        val japanese = track("ja1", "米津玄師 - Lemon", "米津玄師")

        val visible = filterByResultLanguage(
            listOf(englishEpisode, portugueseEpisode, artistOnly, japanese),
            YouTubeResultLanguage.ENGLISH,
        )

        assertEquals(listOf("en1", "neutral1"), visible.map { it.videoId })
    }

    @Test
    fun `any language keeps every result`() {
        val results = listOf(track("pt1", "Você não vai acreditar", "Canal"), track("ja1", "米津玄師", "米津玄師"))

        assertEquals(results, filterByResultLanguage(results, YouTubeResultLanguage.ANY))
    }

    @Test
    fun `hide Portuguese users move to English results and a stored choice wins`() {
        assertEquals(YouTubeResultLanguage.ENGLISH, resolveYouTubeResultLanguage(storedTag = null, legacyHidePortuguese = true))
        assertEquals(YouTubeResultLanguage.ANY, resolveYouTubeResultLanguage(storedTag = null, legacyHidePortuguese = null))
        assertEquals(YouTubeResultLanguage.ANY, resolveYouTubeResultLanguage(storedTag = "", legacyHidePortuguese = true))
        assertEquals(YouTubeResultLanguage.SPANISH, resolveYouTubeResultLanguage(storedTag = "es", legacyHidePortuguese = false))
    }
}
