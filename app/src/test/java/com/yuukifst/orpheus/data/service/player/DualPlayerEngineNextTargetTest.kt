package com.yuukifst.orpheus.data.service.player

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DualPlayerEngineNextTargetTest {

    @Test
    fun repeatAllWrapsLastToFirst() {
        assertEquals(0, nextIndex(2, 3, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun repeatOffStopsAtEnd() {
        assertNull(nextIndex(2, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun repeatOneStaysOnCurrent() {
        assertEquals(2, nextIndex(2, 3, Player.REPEAT_MODE_ONE))
    }

    @Test
    fun repeatAllReturnsNullForEmptyQueue() {
        assertNull(nextIndex(0, 0, Player.REPEAT_MODE_ALL))
    }
}
