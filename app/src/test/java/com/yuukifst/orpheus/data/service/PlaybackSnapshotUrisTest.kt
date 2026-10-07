package com.yuukifst.orpheus.data.service

import com.yuukifst.orpheus.data.model.PlaybackQueueItemSnapshot
import com.yuukifst.orpheus.data.youtube.youtubePlaybackUri
import com.yuukifst.orpheus.data.youtube.youtubeVideoIdFromPlaybackUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Regression: a YouTube track restored after closing the app played its expired signed URL
// and failed with "Couldn't play this track".
class PlaybackSnapshotUrisTest {

    @Test
    fun expiredYouTubeStreamUrlIsRestoredAsPlaceholder() {
        val item = PlaybackQueueItemSnapshot(
            mediaId = "youtube_dQw4w9WgXcQ",
            uri = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&itag=251",
        )

        val restored = snapshotPlaybackUriString(item)

        assertEquals(youtubePlaybackUri("dQw4w9WgXcQ"), restored)
        assertEquals("dQw4w9WgXcQ", youtubeVideoIdFromPlaybackUri(restored))
    }

    @Test
    fun placeholderDownloadedAndLocalUrisAreKept() {
        val placeholder = youtubePlaybackUri("abc123")
        val cases = listOf(
            PlaybackQueueItemSnapshot(mediaId = "youtube_abc123", uri = placeholder),
            PlaybackQueueItemSnapshot(mediaId = "youtube_abc123", uri = "file:///storage/emulated/0/Music/Orpheus/a.m4a"),
            PlaybackQueueItemSnapshot(mediaId = "42", uri = "content://media/external/audio/media/42"),
        )

        cases.forEach { item -> assertEquals(item.uri, snapshotPlaybackUriString(item)) }
    }

    @Test
    fun nonPlaceholderUriHasNoVideoId() {
        assertNull(youtubeVideoIdFromPlaybackUri("https://www.youtube.com/watch?v=abc"))
    }
}
