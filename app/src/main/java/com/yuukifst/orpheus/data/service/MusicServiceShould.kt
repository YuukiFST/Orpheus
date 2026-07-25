package com.yuukifst.orpheus.data.service

/**
 * Pure decision helper for MusicService.onStartCommand unload paths.
 * Keeps notification-dismiss unload from falling through to Media3 sticky start.
 */
object MusicServiceShould {
    fun returnEarlyAfterUnload(action: String?, media3Dismissed: Boolean): Boolean {
        return action == MusicService.ACTION_STOP_AND_UNLOAD
    }

    fun returnEarlyAfterPark(action: String?, media3Dismissed: Boolean): Boolean {
        if (action == MusicService.ACTION_STOP_AND_UNLOAD) return false
        return media3Dismissed || action == MusicService.ACTION_PAUSE_AND_HIDE_NOTIFICATION
    }

    fun skipStalePlaybackClear(clearToken: Long, currentGeneration: Long): Boolean {
        return clearToken >= 0L && clearToken != currentGeneration
    }
}
