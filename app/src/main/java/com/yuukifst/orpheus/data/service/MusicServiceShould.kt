package com.yuukifst.orpheus.data.service

/**
 * Pure decision helper for MusicService.onStartCommand unload paths.
 * Keeps notification-dismiss unload from falling through to Media3 sticky start.
 */
object MusicServiceShould {
    fun returnEarlyAfterUnload(action: String?, media3Dismissed: Boolean): Boolean {
        return media3Dismissed || action == MusicService.ACTION_STOP_AND_UNLOAD
    }
}
