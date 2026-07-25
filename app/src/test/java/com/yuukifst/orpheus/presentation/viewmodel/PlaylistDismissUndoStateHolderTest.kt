package com.yuukifst.orpheus.presentation.viewmodel

import android.content.Context
import com.yuukifst.orpheus.MainCoroutineExtension
import com.yuukifst.orpheus.data.model.Song
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val fakeSong2 = fakeSong.copy(
        id = "2",
        title = "Song 2",
        contentUriString = "content://dummy/2",
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

    @Test
    fun dismissClearsQueueSoNoSecondTrackRemains() = runTest {
        val holder = PlaylistDismissUndoStateHolder(appContext)
        val queue = listOf(fakeSong, fakeSong2)
        var state = PlayerUiState(
            currentPlaybackQueue = persistentListOf(fakeSong, fakeSong2),
            currentQueueSourceName = "Q",
        )
        var queueSizeWhenClearPlaybackRuns: Int? = null
        var stableCleared = false
        var sheet = true

        holder.dismissPlaylistAndShowUndo(
            scope = this,
            currentSong = fakeSong2,
            queue = queue,
            queueName = "Q",
            position = 10L,
            getUiState = { state },
            updateUiState = { mut -> state = mut(state) },
            disconnectRemoteIfNeeded = {},
            clearPlayback = {
                queueSizeWhenClearPlaybackRuns = state.currentPlaybackQueue.size
            },
            clearStablePlaybackState = { stableCleared = true },
            setCurrentPosition = {},
            setSheetVisible = { sheet = it },
        )
        runCurrent()

        assertEquals(0, queueSizeWhenClearPlaybackRuns)
        assertTrue(stableCleared)
        assertTrue(state.showDismissUndoBar)
        assertTrue(state.currentPlaybackQueue.isEmpty())
        assertEquals(2, state.dismissedQueue.size)
        assertFalse(sheet)
    }

    @Test
    fun dismissWithoutUndoClearsPlaybackAndNeverShowsUndoBar() = runTest {
        val holder = PlaylistDismissUndoStateHolder(appContext)
        var state = PlayerUiState(
            currentPlaybackQueue = persistentListOf(fakeSong),
            currentQueueSourceName = "Q",
        )
        var cleared = false
        var sheet = true
        var committed = false

        holder.dismissPlaylistWithoutUndo(
            scope = this,
            currentSong = fakeSong,
            queue = listOf(fakeSong),
            queueName = "Q",
            getUiState = { state },
            updateUiState = { mut -> state = mut(state) },
            disconnectRemoteIfNeeded = {},
            clearPlayback = { cleared = true },
            clearStablePlaybackState = {},
            setCurrentPosition = {},
            setSheetVisible = { sheet = it },
            onDismissCommitted = { committed = true },
        )
        runCurrent()

        assertFalse(state.showDismissUndoBar)
        assertTrue(cleared)
        assertFalse(sheet)
        assertTrue(state.currentPlaybackQueue.isEmpty())
        assertTrue(committed)
        assertEquals(null, state.dismissedSong)
    }

    @Test
    fun undoTimeoutCommitsDismissAndKeepsSheetHidden() = runTest {
        val holder = PlaylistDismissUndoStateHolder(appContext)
        var state = PlayerUiState(
            currentPlaybackQueue = persistentListOf(fakeSong),
            currentQueueSourceName = "Q",
            undoBarVisibleDuration = 50L,
        )
        var sheet = true
        var dismissCommitted = 0

        holder.dismissPlaylistAndShowUndo(
            scope = this,
            currentSong = fakeSong,
            queue = listOf(fakeSong),
            queueName = "Q",
            position = 10L,
            getUiState = { state },
            updateUiState = { mut -> state = mut(state) },
            disconnectRemoteIfNeeded = {},
            clearPlayback = {},
            clearStablePlaybackState = {},
            setCurrentPosition = {},
            setSheetVisible = { sheet = it },
            onDismissCommitted = { dismissCommitted++ },
        )
        runCurrent()
        assertTrue(state.showDismissUndoBar)
        assertFalse(sheet)

        advanceTimeBy(50L)
        runCurrent()

        assertFalse(state.showDismissUndoBar)
        assertFalse(sheet)
        assertEquals(1, dismissCommitted)
        assertTrue(state.dismissedSong == null)
        assertTrue(state.dismissedQueue.isEmpty())
    }
}
