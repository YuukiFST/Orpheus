package com.yuukifst.orpheus.data.worker

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncManagerPolicyTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `setup incomplete denies catch-up`() {
        assertFalse(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = 0L,
                lastLibrarySyncTimestampMs = 0L,
                initialSetupDone = false,
            ),
        )
    }

    @Test
    fun `cooldown denies catch-up`() {
        assertFalse(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = now - 30_000L,
                lastLibrarySyncTimestampMs = 0L,
                initialSetupDone = true,
            ),
        )
    }

    @Test
    fun `fresh install allows catch-up`() {
        assertTrue(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = 0L,
                lastLibrarySyncTimestampMs = 0L,
                initialSetupDone = true,
            ),
        )
    }

    @Test
    fun `inside 6h denies catch-up`() {
        assertFalse(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = 0L,
                lastLibrarySyncTimestampMs = now - 60 * 60 * 1000L,
                initialSetupDone = true,
            ),
        )
    }

    @Test
    fun `after 6h allows catch-up`() {
        assertTrue(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = 0L,
                lastLibrarySyncTimestampMs = now - 7 * 60 * 60 * 1000L,
                initialSetupDone = true,
            ),
        )
    }

    @Test
    fun `boundary of min interval allows catch-up`() {
        assertTrue(
            shouldEnqueueForegroundCatchUp(
                nowMs = now,
                lastForegroundSyncTimeMs = 0L,
                lastLibrarySyncTimestampMs = now - MIN_SYNC_INTERVAL_MS,
                initialSetupDone = true,
            ),
        )
    }
}
