package com.yuukifst.orpheus.presentation.viewmodel

import android.content.Context
import com.yuukifst.orpheus.MainCoroutineExtension
import com.yuukifst.orpheus.data.model.Song
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaylistDismissUndoStateHolderTest {

    private val appContext: Context = mockk(relaxed = true)

    private val fakeSong = Song(
        id = "1",
        title = "Song 1",
        artist = "Artist A",
        genre = "Rock",
        albumArtUriString = "cover1.png",
        artistId = 1L,
        albumId = 1L,
        contentUriString = "content://dummy/1",
        duration = 180_000L,
        bitrate = null,
        sampleRate = null,
        album = "Album",
        path = "path",
        mimeType = "audio/mpeg",
    )

    @Test
    fun dismissSetsUndoBarAndClearsQueueFlags() = runTest {
        val holder = PlaylistDismissUndoStateHolder(appContext)
        var state = PlayerUiState(
            currentPlaybackQueue = persistentListOf(fakeSong),
            currentQueueSourceName = "Q",
        )
        var cleared = false
        var sheet = true

        holder.dismissPlaylistAndShowUndo(
            scope = this,
            currentSong = fakeSong,
            queue = listOf(fakeSong),
            queueName = "Q",
            position = 10L,
            getUiState = { state },
            updateUiState = { mut -> state = mut(state) },
            disconnectRemoteIfNeeded = {},
            clearPlayback = { cleared = true },
            clearStablePlaybackState = {},
            setCurrentPosition = {},
            setSheetVisible = { sheet = it },
        )
        runCurrent()

        assertTrue(state.showDismissUndoBar)
        assertTrue(cleared)
        assertFalse(sheet)
        assertTrue(state.currentPlaybackQueue.isEmpty())
        assertTrue(state.currentQueueSourceName.isEmpty())
    }
}
