package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.preferences.YouTubeAudioQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

class YouTubeAudioStreamSelectionTest {

    @Test
    fun highPicksHighestKbps() {
        assertEquals(160, chooseYouTubeAudioBitrate(listOf(64, 128, 160), YouTubeAudioQuality.HIGH))
    }

    @Test
    fun mediumCapsAt128() {
        assertEquals(128, chooseYouTubeAudioBitrate(listOf(64, 128, 160), YouTubeAudioQuality.MEDIUM))
    }

    @Test
    fun lowCapsAt64() {
        assertEquals(64, chooseYouTubeAudioBitrate(listOf(50, 64, 128), YouTubeAudioQuality.LOW))
    }

    @Test
    fun capFallsBackToLowestWhenNothingUnderCap() {
        assertEquals(160, chooseYouTubeAudioBitrate(listOf(160, 192), YouTubeAudioQuality.LOW))
    }

    @Test
    fun bpsValuesAreNormalizedToKbps() {
        assertEquals(
            128_000,
            chooseYouTubeAudioBitrate(listOf(64_000, 128_000, 160_000), YouTubeAudioQuality.MEDIUM),
        )
    }

    @Test
    fun kbpsAndBpsNormalizeTheSame() {
        assertEquals(128, youtubeBitrateKbps(128))
        assertEquals(128, youtubeBitrateKbps(128_000))
    }

    @Test
    fun dubbedTrackIsNeverPickedWhenOriginalExists() {
        val streams = listOf(
            audioStream(id = "251-dubbed", kbps = 160, type = AudioTrackType.DUBBED),
            audioStream(id = "251-original", kbps = 130, type = AudioTrackType.ORIGINAL),
            audioStream(id = "140-original", kbps = 128, type = AudioTrackType.ORIGINAL),
        )

        assertEquals("251-original", selectYouTubeAudioStream(streams, YouTubeAudioQuality.HIGH)?.id)
    }

    @Test
    fun untaggedStreamsWinOverDubbedWhenNothingIsTaggedOriginal() {
        val streams = listOf(
            audioStream(id = "dubbed", kbps = 160, type = AudioTrackType.DUBBED),
            audioStream(id = "untagged", kbps = 128, type = null),
        )

        assertEquals("untagged", selectYouTubeAudioStream(streams, YouTubeAudioQuality.HIGH)?.id)
    }

    private fun audioStream(id: String, kbps: Int, type: AudioTrackType?): AudioStream =
        AudioStream.Builder()
            .setId(id)
            .setContent("https://example.invalid/$id", true)
            .setAverageBitrate(kbps)
            .setAudioTrackType(type)
            .build()
}
