package com.yuukifst.orpheus.data.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDismissCommandTest {

    @Test
    fun stopAndUnloadActionShouldReturnEarlyWithoutSuper() {
        assertTrue(
            MusicServiceShould.returnEarlyAfterUnload(
                action = MusicService.ACTION_STOP_AND_UNLOAD,
                media3Dismissed = false,
            )
        )
    }

    @Test
    fun media3DismissFlagShouldReturnEarlyWithoutSuper() {
        assertTrue(
            MusicServiceShould.returnEarlyAfterUnload(
                action = null,
                media3Dismissed = true,
            )
        )
    }

    @Test
    fun unrelatedActionShouldNotReturnEarly() {
        assertFalse(
            MusicServiceShould.returnEarlyAfterUnload(
                action = MusicService.ACTION_SLEEP_TIMER_EXPIRED,
                media3Dismissed = false,
            )
        )
    }
}
