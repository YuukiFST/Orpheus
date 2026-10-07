package com.yuukifst.orpheus.data.service

import com.yuukifst.orpheus.data.model.PlaybackQueueItemSnapshot
import com.yuukifst.orpheus.data.youtube.youtubePlaybackUri
import com.yuukifst.orpheus.data.youtube.youtubeVideoIdFromMediaId
import com.yuukifst.orpheus.data.youtube.youtubeVideoIdFromPlaybackUri

/**
 * URI to rebuild a restored queue item with. Snapshots written before YouTube placeholders
 * stored the signed googlevideo URL, which has expired by the next launch; those streamed
 * items are rewritten to a placeholder so the player re-resolves them. Downloaded YouTube
 * tracks (file URIs) and every other item keep their stored URI.
 *
 * Example: mediaId `youtube_abc` + `https://rr1---sn.googlevideo.com/...` -> `youtubePlaybackUri("abc")`.
 */
internal fun snapshotPlaybackUriString(item: PlaybackQueueItemSnapshot): String {
    if (youtubeVideoIdFromPlaybackUri(item.uri) != null) return item.uri
    val videoId = youtubeVideoIdFromMediaId(item.mediaId) ?: return item.uri
    val isStreamUrl = item.uri.startsWith("https://") || item.uri.startsWith("http://")
    return if (isStreamUrl) youtubePlaybackUri(videoId) else item.uri
}
