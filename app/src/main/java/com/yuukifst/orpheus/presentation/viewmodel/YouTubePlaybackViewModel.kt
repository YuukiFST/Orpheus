package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.service.player.DualPlayerEngine
import com.yuukifst.orpheus.data.youtube.YouTubeCachedTrackRepository
import com.yuukifst.orpheus.data.youtube.YouTubePlaybackResolver
import com.yuukifst.orpheus.data.youtube.YouTubeStreamExtractor
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.utils.MediaItemBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

internal fun shouldReplaceQueueForSearchPlay(
    currentIsYouTubeSession: Boolean,
    explicitAddToQueue: Boolean,
): Boolean = !explicitAddToQueue

internal fun isSearchQueueName(queueName: String): Boolean =
    queueName.startsWith("Search")

internal fun YouTubeTrack.toPlaybackSong(filePath: String? = null): Song = Song(
    id = mediaId,
    title = effectiveTitle,
    artist = channelName,
    artistId = -1L,
    album = "YouTube",
    albumId = -1L,
    path = filePath.orEmpty(),
    contentUriString = filePath.orEmpty(),
    albumArtUriString = thumbnailUrl.takeIf { it.isNotBlank() },
    duration = durationMs,
    mimeType = null,
    bitrate = null,
    sampleRate = null,
)

internal data class OptimisticYouTubePlaybackUi(
    val song: Song,
    val mediaItemIndex: Int,
    val queueName: String,
)

/**
 * The mini player is gated on `currentSong != null`, so the search-result
 * metadata is published before the stream is resolved. Must not read the
 * player: `DualPlayerEngine.masterPlayer` builds ExoPlayer on first access.
 */
internal fun optimisticUiForTrack(
    track: YouTubeTrack,
    index: Int = 0,
    queueName: String = "YouTube",
): OptimisticYouTubePlaybackUi = OptimisticYouTubePlaybackUi(
    song = track.toPlaybackSong(),
    mediaItemIndex = index.coerceAtLeast(0),
    queueName = queueName,
)

data class YouTubePlaybackQueueUpdate(
    val songs: List<Song>,
    val queueName: String,
    val currentIndex: Int,
)

@Singleton
class YouTubePlaybackController @Inject constructor(
    private val playbackResolver: YouTubePlaybackResolver,
    private val streamExtractor: YouTubeStreamExtractor,
    private val dualPlayerEngine: DualPlayerEngine,
    private val playbackStateHolder: PlaybackStateHolder,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val cachedTrackRepository: YouTubeCachedTrackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackListener: Player.Listener? = null
    private var retryCountForCurrentItem = 0
    private var currentMixedTracks: List<PlaylistMixedTrack> = emptyList()
    private var sessionStopOnEnd = false
    private var queueFillJob: kotlinx.coroutines.Job? = null

    private val _playbackErrors = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val playbackErrors: SharedFlow<String> = _playbackErrors.asSharedFlow()

    private val _queueUpdates = MutableSharedFlow<YouTubePlaybackQueueUpdate>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val queueUpdates: SharedFlow<YouTubePlaybackQueueUpdate> = _queueUpdates.asSharedFlow()

    suspend fun playOnce(track: YouTubeTrack): Boolean {
        val previousSong = playbackStateHolder.stablePlayerState.value.currentSong
        val optimistic = optimisticUiForTrack(track)
        currentMixedTracks = listOf(PlaylistMixedTrack.YouTube(track = track, sortOrder = 0))
        sessionStopOnEnd = true
        withContext(Dispatchers.Main.immediate) {
            publishOptimisticPlaybackState(optimistic)
        }
        listeningStatsTracker.onVoluntarySelection(track.mediaId)
        scope.launch { runCatching { cachedTrackRepository.recordPlayed(track) } }

        return runCatching {
            startPlayback(
                tracks = currentMixedTracks,
                startIndex = 0,
                repeatMode = Player.REPEAT_MODE_OFF,
                stopOnEnd = true,
                queueName = "YouTube",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            rollbackOptimisticPlaybackState(track, previousSong)
            _playbackErrors.emit(userFacingPlaybackError(error))
        }.isSuccess
    }

    suspend fun addToQueue(track: YouTubeTrack): Boolean {
        return runCatching {
            val player = dualPlayerEngine.masterPlayer
            val mediaItem = withContext(Dispatchers.IO) {
                playbackResolver.resolveMediaItem(track)
            }
            withContext(Dispatchers.Main.immediate) {
                if (player.mediaItemCount == 0) {
                    playOnce(track)
                    return@withContext
                }
                if (sessionStopOnEnd) {
                    sessionStopOnEnd = false
                    attachPlaybackListener(player, stopOnEnd = false)
                }
                val entry = PlaylistMixedTrack.YouTube(
                    track = track,
                    sortOrder = currentMixedTracks.size,
                )
                currentMixedTracks = currentMixedTracks + entry
                player.addMediaItem(mediaItem)
                publishQueueUpdate(player.currentMediaItemIndex)
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _playbackErrors.emit(userFacingPlaybackError(error))
        }.isSuccess
    }

    suspend fun playPlaylist(tracks: List<YouTubeTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        runCatching {
            val mixed = tracks.mapIndexed { index, track ->
                PlaylistMixedTrack.YouTube(track = track, sortOrder = index)
            }
            mixed.forEach { entry ->
                if (entry is PlaylistMixedTrack.YouTube) {
                    cachedTrackRepository.recordPlayed(entry.track)
                }
            }
            sessionStopOnEnd = false
            startPlayback(
                tracks = mixed,
                startIndex = startIndex,
                repeatMode = Player.REPEAT_MODE_ALL,
                stopOnEnd = false,
                queueName = "YouTube Playlist",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _playbackErrors.emit(userFacingPlaybackError(error))
        }
    }

    suspend fun playMixedPlaylist(
        tracks: List<PlaylistMixedTrack>,
        startIndex: Int = 0,
        repeatMode: Int = Player.REPEAT_MODE_ALL,
        stopOnEnd: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        runCatching {
            tracks.forEach { entry ->
                if (entry is PlaylistMixedTrack.YouTube) {
                    cachedTrackRepository.recordPlayed(entry.track)
                }
            }
            sessionStopOnEnd = stopOnEnd
            startPlayback(
                tracks = tracks,
                startIndex = startIndex,
                repeatMode = repeatMode,
                stopOnEnd = stopOnEnd,
                queueName = "Queue",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _playbackErrors.emit(userFacingPlaybackError(error))
        }
    }

    private suspend fun startPlayback(
        tracks: List<PlaylistMixedTrack>,
        startIndex: Int,
        repeatMode: Int,
        stopOnEnd: Boolean,
        queueName: String,
    ) {
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        currentMixedTracks = tracks
        retryCountForCurrentItem = 0
        queueFillJob?.cancel()

        val startItem = withContext(Dispatchers.IO) {
            resolveMixedEntry(tracks[safeIndex])
        }

        withContext(Dispatchers.Main.immediate) {
            val player = dualPlayerEngine.masterPlayer
            attachPlaybackListener(player, stopOnEnd)
            player.shuffleModeEnabled = false
            player.repeatMode = repeatMode
            player.setMediaItem(startItem, 0L)
            player.prepare()
            player.play()
            publishPlaybackState(safeIndex, repeatMode, false, queueName)
        }

        if (tracks.size > 1) {
            queueFillJob = scope.launch {
                runCatching {
                    val allItems = withContext(Dispatchers.IO) {
                        tracks.map { resolveMixedEntry(it) }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        val player = dualPlayerEngine.masterPlayer
                        if (player.mediaItemCount <= 1) {
                            val position = player.currentPosition
                            player.setMediaItems(allItems, safeIndex, position)
                            player.prepare()
                            if (player.playWhenReady) player.play()
                        }
                    }
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        _playbackErrors.emit(userFacingPlaybackError(error))
                    }
                }
            }
        }
    }

    private suspend fun resolveMixedEntry(entry: PlaylistMixedTrack): MediaItem {
        return when (entry) {
            is PlaylistMixedTrack.Local -> MediaItemBuilder.build(entry.song)
            is PlaylistMixedTrack.YouTube -> playbackResolver.resolveMediaItem(entry.track)
        }
    }

    private fun attachPlaybackListener(player: Player, stopOnEnd: Boolean) {
        detachListener(player)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (
                    stopOnEnd &&
                    playbackState == Player.STATE_ENDED &&
                    player.repeatMode == Player.REPEAT_MODE_OFF
                ) {
                    player.playWhenReady = false
                    player.pause()
                    player.clearMediaItems()
                    currentMixedTracks = emptyList()
                    sessionStopOnEnd = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                songForMixedIndex(index)?.let { song ->
                    syncListeningStats(player, song, forceNewSession = true)
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = song,
                            currentMediaItemIndex = index,
                            totalDuration = song.duration.coerceAtLeast(0L),
                        )
                    }
                    publishQueueUpdate(index)
                }
                retryCountForCurrentItem = 0
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                songForMixedIndex(player.currentMediaItemIndex)?.let { song ->
                    listeningStatsTracker.onPlayStateChanged(isPlaying, player.currentPosition)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val currentItem = player.currentMediaItem ?: return
                val videoId = currentItem.mediaMetadata.extras
                    ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_YOUTUBE_VIDEO_ID)
                if (videoId != null && retryCountForCurrentItem < 1) {
                    retryCountForCurrentItem++
                    scope.launch {
                        runCatching {
                            val stream = withContext(Dispatchers.IO) {
                                streamExtractor.extractBestAudioWithRetry(videoId)
                            }
                            val retried = currentItem.buildUpon()
                                .setUri(stream.streamUrl)
                                .setMimeType(stream.mimeType)
                                .build()
                            player.replaceMediaItem(player.currentMediaItemIndex, retried)
                            player.prepare()
                            player.play()
                        }.onFailure { failure ->
                            if (failure !is CancellationException) {
                                _playbackErrors.emit(userFacingPlaybackError(failure))
                            }
                            skipToNextOrStop(player)
                        }
                    }
                    return
                }
                skipToNextOrStop(player)
            }
        }
        playbackListener = listener
        player.addListener(listener)
    }

    private fun publishOptimisticPlaybackState(optimistic: OptimisticYouTubePlaybackUi) {
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = optimistic.song,
                currentMediaItemIndex = optimistic.mediaItemIndex,
                isPlaying = true,
                playWhenReady = true,
                totalDuration = optimistic.song.duration.coerceAtLeast(0L),
            )
        }
        publishQueueUpdate(optimistic.mediaItemIndex, optimistic.queueName)
    }

    private suspend fun rollbackOptimisticPlaybackState(
        track: YouTubeTrack,
        previousSong: Song?,
    ) = withContext(Dispatchers.Main.immediate) {
        val current = playbackStateHolder.stablePlayerState.value.currentSong
        if (current?.id != track.mediaId) return@withContext
        Timber.w("Rolling back optimistic YouTube playback for videoId=%s", track.videoId)
        currentMixedTracks = emptyList()
        sessionStopOnEnd = false
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = previousSong,
                isPlaying = false,
                playWhenReady = false,
                totalDuration = previousSong?.duration?.coerceAtLeast(0L) ?: 0L,
            )
        }
    }

    private fun publishPlaybackState(
        index: Int,
        repeatMode: Int,
        isShuffleEnabled: Boolean,
        queueName: String = "YouTube",
    ) {
        val startSong = songForMixedIndex(index)
        startSong?.let { song ->
            syncListeningStats(dualPlayerEngine.masterPlayer, song, forceNewSession = true)
        }
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = startSong,
                currentMediaItemIndex = index,
                isPlaying = true,
                playWhenReady = true,
                totalDuration = startSong?.duration?.coerceAtLeast(0L) ?: 0L,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
            )
        }
        publishQueueUpdate(index, queueName)
    }

    private fun publishQueueUpdate(currentIndex: Int, queueName: String = "YouTube") {
        val songs = currentMixedTracks.mapNotNull { entry ->
            when (entry) {
                is PlaylistMixedTrack.Local -> entry.song
                is PlaylistMixedTrack.YouTube -> entry.track.toPlaybackSong()
            }
        }
        _queueUpdates.tryEmit(
            YouTubePlaybackQueueUpdate(
                songs = songs,
                queueName = queueName,
                currentIndex = currentIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
            ),
        )
    }

    private fun syncListeningStats(player: Player, song: Song, forceNewSession: Boolean) {
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val durationMs = player.duration
        if (forceNewSession) {
            listeningStatsTracker.onTrackChanged(
                songId = song.id,
                positionMs = positionMs,
                durationMs = durationMs,
                fallbackDurationMs = song.duration,
                isPlaying = player.isPlaying,
            )
        } else {
            listeningStatsTracker.ensureSession(
                songId = song.id,
                positionMs = positionMs,
                durationMs = durationMs,
                fallbackDurationMs = song.duration,
                isPlaying = player.isPlaying,
            )
        }
    }

    private fun skipToNextOrStop(player: Player) {
        retryCountForCurrentItem = 0
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else {
            player.pause()
            player.playWhenReady = false
        }
    }

    private fun detachListener(player: Player) {
        playbackListener?.let(player::removeListener)
        playbackListener = null
    }

    private fun songForMixedIndex(index: Int): Song? {
        return when (val entry = currentMixedTracks.getOrNull(index)) {
            is PlaylistMixedTrack.Local -> entry.song
            is PlaylistMixedTrack.YouTube -> entry.track.toPlaybackSong()
            null -> null
        }
    }

    private fun userFacingPlaybackError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is UnknownHostException ||
                message.contains("Unable to resolve host", ignoreCase = true) ->
                "No internet connection. Check your network and try again."
            message.contains("cancel", ignoreCase = true) -> ""
            else -> message.ifBlank { "Playback failed" }
        }
    }
}

@HiltViewModel
class YouTubePlaybackViewModel @Inject constructor(
    private val playbackController: YouTubePlaybackController,
) : ViewModel() {

    fun playOnce(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.playOnce(track)
        }
    }

    fun addToQueue(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.addToQueue(track)
        }
    }

    fun playPlaylist(tracks: List<YouTubeTrack>, startIndex: Int = 0) {
        viewModelScope.launch {
            playbackController.playPlaylist(tracks, startIndex)
        }
    }
}
