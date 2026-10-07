package com.yuukifst.orpheus.data.service.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.yuukifst.orpheus.data.youtube.YouTubeStreamExtractor
import com.yuukifst.orpheus.data.youtube.isYouTubeAudioCacheKey
import com.yuukifst.orpheus.data.youtube.youtubeAudioCacheKey
import com.yuukifst.orpheus.data.youtube.youtubeVideoIdFromPlaybackUri
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Player data source: resolves YouTube placeholder URIs (see `youtubePlaybackUri`) into a fresh
 * stream URL when the player loads them, and serves those streams through an on-disk cache so a
 * replayed or re-seeked track costs no mobile data. Every other URI goes straight to [upstreamFactory].
 *
 * Example: `ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(YouTubeStreamDataSourceFactory(DefaultDataSource.Factory(context), { extractor }, { cache })))`.
 */
@OptIn(UnstableApi::class)
class YouTubeStreamDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val streamExtractor: () -> YouTubeStreamExtractor,
    cacheProvider: () -> Cache,
) : DataSource.Factory {

    // Lazy: the cache index is only opened once a YouTube stream is actually played.
    private val cachedFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cacheProvider())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val resolver = ResolvingDataSource.Resolver { dataSpec -> resolveYouTubeDataSpec(dataSpec) }

    override fun createDataSource(): DataSource =
        ResolvingDataSource(
            YouTubeCacheRoutingDataSource(
                direct = upstreamFactory.createDataSource(),
                createCached = { cachedFactory.createDataSource() },
            ),
            resolver,
        )

    // Runs on the ExoPlayer loader thread, so blocking on the extract is allowed here.
    private fun resolveYouTubeDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = youtubeVideoIdFromPlaybackUri(dataSpec.uri.toString()) ?: return dataSpec
        val stream = try {
            runBlocking { streamExtractor().extractBestAudioWithRetry(videoId) }
        } catch (_: InterruptedException) {
            throw InterruptedIOException("YouTube stream resolve interrupted for $videoId")
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Could not resolve YouTube stream for $videoId: ${error.message}", error)
        }
        return dataSpec.buildUpon()
            .setUri(stream.streamUrl.toUri())
            .setKey(youtubeAudioCacheKey(videoId, stream.formatId))
            .build()
    }
}

/** Sends resolved YouTube specs through the disk cache; local files and other hosts bypass it. */
@OptIn(UnstableApi::class)
private class YouTubeCacheRoutingDataSource(
    private val direct: DataSource,
    createCached: () -> DataSource,
) : DataSource {
    private val cached: DataSource by lazy(LazyThreadSafetyMode.NONE) {
        createCached().also { source -> transferListeners.forEach(source::addTransferListener) }
    }
    private val transferListeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isYouTubeAudioCacheKey(dataSpec.key)) cached else direct
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
