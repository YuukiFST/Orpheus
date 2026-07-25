package com.yuukifst.orpheus.data.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationDismissCommandTest {

    @Test
    fun stopAndUnloadActionShouldReturnEarlyAfterUnload() {
        assertTrue(
            MusicServiceShould.returnEarlyAfterUnload(
                action = MusicService.ACTION_STOP_AND_UNLOAD,
                media3Dismissed = false,
            )
        )
    }

    @Test
    fun media3DismissShouldParkNotUnload() {
        assertFalse(
            MusicServiceShould.returnEarlyAfterUnload(
                action = null,
                media3Dismissed = true,
            )
        )
        assertTrue(
            MusicServiceShould.returnEarlyAfterPark(
                action = null,
                media3Dismissed = true,
            )
        )
    }

    @Test
    fun stopAndUnloadWithMedia3DismissShouldUnloadNotPark() {
        assertTrue(
            MusicServiceShould.returnEarlyAfterUnload(
                action = MusicService.ACTION_STOP_AND_UNLOAD,
                media3Dismissed = true,
            )
        )
        assertFalse(
            MusicServiceShould.returnEarlyAfterPark(
                action = MusicService.ACTION_STOP_AND_UNLOAD,
                media3Dismissed = true,
            )
        )
    }

    @Test
    fun pauseAndHideActionShouldPark() {
        assertTrue(
            MusicServiceShould.returnEarlyAfterPark(
                action = MusicService.ACTION_PAUSE_AND_HIDE_NOTIFICATION,
                media3Dismissed = false,
            )
        )
        assertFalse(
            MusicServiceShould.returnEarlyAfterUnload(
                action = MusicService.ACTION_PAUSE_AND_HIDE_NOTIFICATION,
                media3Dismissed = false,
            )
        )
    }
}
