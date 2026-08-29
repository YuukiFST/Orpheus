package com.yuukifst.orpheus.data.worker

internal const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
internal const val FOREGROUND_SYNC_COOLDOWN_MS = 60_000L

internal fun shouldEnqueueForegroundCatchUp(
    nowMs: Long,
    lastForegroundSyncTimeMs: Long,
    lastLibrarySyncTimestampMs: Long,
    initialSetupDone: Boolean,
    cooldownMs: Long = FOREGROUND_SYNC_COOLDOWN_MS,
    minIntervalMs: Long = MIN_SYNC_INTERVAL_MS,
): Boolean {
    if (!initialSetupDone) return false
    if (nowMs - lastForegroundSyncTimeMs < cooldownMs) return false
    if (lastLibrarySyncTimestampMs > 0L &&
        nowMs - lastLibrarySyncTimestampMs < minIntervalMs
    ) {
        return false
    }
    return true
}
