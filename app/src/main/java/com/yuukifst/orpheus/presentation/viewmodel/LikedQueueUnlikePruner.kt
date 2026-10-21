package com.yuukifst.orpheus.presentation.viewmodel

import androidx.media3.common.Player

/**
 * Keeps a Liked Songs queue in step with the likes. A song unliked while that queue plays leaves
 * it at once; the song playing finishes first and leaves on the next transition. Without this,
 * the repeat-all wrap of the Liked queue plays an unliked song again.
 */
internal class LikedQueueUnlikePruner {
    private var previousFavoriteIds: Set<String>? = null
    private val pendingRemovalIds = mutableSetOf<String>()

    fun onFavoriteIdsChanged(favoriteIds: Set<String>, isLikedQueue: Boolean, player: Player?) {
        val previous = previousFavoriteIds
        previousFavoriteIds = favoriteIds
        pendingRemovalIds.removeAll(favoriteIds)
        if (previous == null || !isLikedQueue || player == null) return
        val unlikedIds = previous - favoriteIds
        if (unlikedIds.isEmpty()) return

        val currentMediaId = player.currentMediaItem?.mediaId
        pendingRemovalIds += unlikedIds.filter { it == currentMediaId }
        removeQueueItems(player, unlikedIds - pendingRemovalIds)
    }

    /** A new queue ([Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED]) drops pending removals. */
    fun onMediaItemTransition(reason: Int, isLikedQueue: Boolean, player: Player) {
        if (pendingRemovalIds.isEmpty()) return
        if (!isLikedQueue || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            pendingRemovalIds.clear()
            return
        }
        // Repeat-one replays the unliked song; it stays pending until playback moves on.
        val currentMediaId = player.currentMediaItem?.mediaId
        val leavingIds = pendingRemovalIds.filterTo(mutableSetOf()) { it != currentMediaId }
        pendingRemovalIds.removeAll(leavingIds)
        removeQueueItems(player, leavingIds)
    }

    private fun removeQueueItems(player: Player, mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId in mediaIds) player.removeMediaItem(index)
        }
    }
}
