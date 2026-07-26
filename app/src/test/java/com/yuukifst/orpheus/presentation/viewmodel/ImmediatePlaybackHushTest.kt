package com.yuukifst.orpheus.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImmediatePlaybackHushTest {

    @Test
    fun `playback generation matches only equal tokens`() {
        assertTrue(isPlaybackRequestCurrent(expectedGeneration = 3L, currentGeneration = 3L))
        assertFalse(isPlaybackRequestCurrent(expectedGeneration = 2L, currentGeneration = 3L))
        assertFalse(isPlaybackRequestCurrent(expectedGeneration = 4L, currentGeneration = 3L))
    }
}
