package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.preferences.YouTubeAudioQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
