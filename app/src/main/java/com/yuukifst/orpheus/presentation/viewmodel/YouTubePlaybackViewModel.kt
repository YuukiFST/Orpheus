package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.service.player.DualPlayerEngine
import com.yuukifst.orpheus.data.youtube.YouTubePlaybackResolver
import com.yuukifst.orpheus.data.youtube.YouTubeStreamExtractor
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.utils.MediaItemBuilder
import com.yuukifst.orpheus.utils.isYouTubeMediaId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class YouTubePlaybackController @Inject constructor(
    private val playbackResolver: YouTubePlaybackResolver,
    private val streamExtractor: YouTubeStreamExtractor,
    private val dualPlayerEngine: DualPlayerEngine,
    private val playbackStateHolder: PlaybackStateHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackListener: Player.Listener? = null
    private var retryCountForCurrentItem = 0
    private var currentMixedTracks: List<PlaylistMixedTrack> = emptyList()

    suspend fun playOnce(track: YouTubeTrack) {
        val mixed = listOf(PlaylistMixedTrack.YouTube(track = track, sortOrder = 0))
        playMixedPlaylist(mixed, startIndex = 0, repeatMode = Player.REPEAT_MODE_OFF, stopOnEnd = true)
    }

    suspend fun playPlaylist(tracks: List<YouTubeTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val mixed = tracks.mapIndexed { index, track ->
            PlaylistMixedTrack.YouTube(track = track, sortOrder = index)
        }
        playMixedPlaylist(mixed, startIndex = startIndex, repeatMode = Player.REPEAT_MODE_ALL, stopOnEnd = false)
    }

    suspend fun playMixedPlaylist(
        tracks: List<PlaylistMixedTrack>,
        startIndex: Int = 0,
        repeatMode: Int = Player.REPEAT_MODE_ALL,
        stopOnEnd: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        currentMixedTracks = tracks
        retryCountForCurrentItem = 0

        val mediaItems = withContext(Dispatchers.IO) {
            tracks.map { entry ->
                when (entry) {
                    is PlaylistMixedTrack.Local -> MediaItemBuilder.build(entry.song)
                    is PlaylistMixedTrack.YouTube -> playbackResolver.resolveMediaItem(entry.track)
                }
            }
        }

        withContext(Dispatchers.Main.immediate) {
            val player = dualPlayerEngine.masterPlayer
            detachListener(player)
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        stopOnEnd &&
                        playbackState == Player.STATE_ENDED &&
                        player.repeatMode == Player.REPEAT_MODE_OFF &&
                        player.mediaItemCount <= 1
                    ) {
                        player.pause()
                        player.playWhenReady = false
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = player.currentMediaItemIndex
                    songForMixedIndex(index)?.let { song ->
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = song,
                                currentMediaItemIndex = index,
                                totalDuration = song.duration.coerceAtLeast(0L),
                            )
                        }
                    }
                    retryCountForCurrentItem = 0
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
                            }.onFailure {
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

            player.shuffleModeEnabled = false
            player.repeatMode = repeatMode
            player.setMediaItems(mediaItems, safeIndex, 0L)
            player.prepare()
            player.play()

            val startSong = songForMixedIndex(safeIndex)
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = startSong,
                    currentMediaItemIndex = safeIndex,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = startSong?.duration?.coerceAtLeast(0L) ?: 0L,
                    repeatMode = repeatMode,
                    isShuffleEnabled = false,
                )
            }
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

    fun playPlaylist(tracks: List<YouTubeTrack>, startIndex: Int = 0) {
        viewModelScope.launch {
            playbackController.playPlaylist(tracks, startIndex)
        }
    }
}
