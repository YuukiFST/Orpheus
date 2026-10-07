package com.yuukifst.orpheus.data.youtube

// Streamed items carry this stable placeholder, not the signed googlevideo URL, which expires
// after hours (restored/queued items then failed with HTTP 403). YouTubeStreamDataSourceFactory
// swaps in a fresh URL at load time. The id sits in the fragment so the https scheme survives.

private const val YOUTUBE_PLAYBACK_FRAGMENT_PREFIX = "orpheus_yt="
private const val YOUTUBE_MEDIA_ID_PREFIX = "youtube_"
private const val YOUTUBE_AUDIO_CACHE_KEY_PREFIX = "yt-audio:"

/** Example: `youtubePlaybackUri("dQw4w9WgXcQ")` = `https://www.youtube.com/watch?v=dQw4w9WgXcQ#orpheus_yt=dQw4w9WgXcQ`. */
fun youtubePlaybackUri(videoId: String): String =
    "https://www.youtube.com/watch?v=$videoId#$YOUTUBE_PLAYBACK_FRAGMENT_PREFIX$videoId"

/** Video id of a placeholder from [youtubePlaybackUri]; null for any other URI. */
fun youtubeVideoIdFromPlaybackUri(uri: String?): String? =
    uri?.substringAfter("#$YOUTUBE_PLAYBACK_FRAGMENT_PREFIX", missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }

/** Video id of a `youtube_<id>` media id (see YouTubeTrack.mediaId); null otherwise. */
fun youtubeVideoIdFromMediaId(mediaId: String?): String? =
    mediaId?.takeIf { it.startsWith(YOUTUBE_MEDIA_ID_PREFIX) }
        ?.removePrefix(YOUTUBE_MEDIA_ID_PREFIX)
        ?.takeIf { it.isNotBlank() }

/** Disk-cache key stable across URL refreshes; [fileId] must identify the exact bytes (itag + size). */
fun youtubeAudioCacheKey(videoId: String, fileId: String): String =
    "$YOUTUBE_AUDIO_CACHE_KEY_PREFIX$videoId:$fileId"

fun isYouTubeAudioCacheKey(key: String?): Boolean =
    key?.startsWith(YOUTUBE_AUDIO_CACHE_KEY_PREFIX) == true
