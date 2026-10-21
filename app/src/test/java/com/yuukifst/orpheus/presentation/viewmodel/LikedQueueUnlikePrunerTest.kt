package com.yuukifst.orpheus.presentation.viewmodel

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LikedQueueUnlikePrunerTest {

    /** Player whose queue is a plain list of media ids. */
    private class FakeQueue(initialMediaIds: List<String>, var currentIndex: Int) {
        val mediaIds = initialMediaIds.toMutableList()

        val player: Player = mockk(relaxed = true) {
            every { mediaItemCount } answers { mediaIds.size }
            every { getMediaItemAt(any()) } answers { mediaItem(mediaIds[firstArg()]) }
            every { currentMediaItem } answers { mediaItem(mediaIds[currentIndex]) }
            every { removeMediaItem(any()) } answers {
                val index = firstArg<Int>()
                mediaIds.removeAt(index)
                if (index < currentIndex) currentIndex--
            }
        }

        fun playNext() {
            currentIndex = (currentIndex + 1) % mediaIds.size
        }

        private fun mediaItem(mediaId: String) = MediaItem.Builder().setMediaId(mediaId).build()
    }

    @Test
    fun `regression 63 - song unliked while the second plays does not come back after the wrap`() {
        val liked = FakeQueue(listOf("first", "second"), currentIndex = 1)
        val pruner = LikedQueueUnlikePruner()
        pruner.onFavoriteIdsChanged(setOf("first", "second"), isLikedQueue = true, player = liked.player)

        pruner.onFavoriteIdsChanged(setOf("second"), isLikedQueue = true, player = liked.player)
        liked.playNext()

        assertEquals(listOf("second"), liked.mediaIds)
        assertEquals("second", liked.mediaIds[liked.currentIndex])
    }

    @Test
    fun `unliked song that is playing finishes and leaves on the next transition`() {
        val liked = FakeQueue(listOf("first", "second"), currentIndex = 0)
        val pruner = LikedQueueUnlikePruner()
        pruner.onFavoriteIdsChanged(setOf("first", "second"), isLikedQueue = true, player = liked.player)

        pruner.onFavoriteIdsChanged(setOf("second"), isLikedQueue = true, player = liked.player)
        assertEquals(listOf("first", "second"), liked.mediaIds)

        liked.playNext()
        pruner.onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, isLikedQueue = true, player = liked.player)

        assertEquals(listOf("second"), liked.mediaIds)
    }

    @Test
    fun `other queues and a song liked again stay untouched`() {
        val album = FakeQueue(listOf("first", "second"), currentIndex = 1)
        val pruner = LikedQueueUnlikePruner()
        pruner.onFavoriteIdsChanged(setOf("first", "second"), isLikedQueue = false, player = album.player)
        pruner.onFavoriteIdsChanged(setOf("second"), isLikedQueue = false, player = album.player)
        assertEquals(listOf("first", "second"), album.mediaIds)

        val liked = FakeQueue(listOf("first", "second"), currentIndex = 0)
        pruner.onFavoriteIdsChanged(setOf("first", "second"), isLikedQueue = true, player = liked.player)
        pruner.onFavoriteIdsChanged(setOf("second"), isLikedQueue = true, player = liked.player)
        pruner.onFavoriteIdsChanged(setOf("first", "second"), isLikedQueue = true, player = liked.player)
        liked.playNext()
        pruner.onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, isLikedQueue = true, player = liked.player)

        assertEquals(listOf("first", "second"), liked.mediaIds)
    }
}
