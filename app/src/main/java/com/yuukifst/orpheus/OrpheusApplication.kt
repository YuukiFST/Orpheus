package com.yuukifst.orpheus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import com.yuukifst.orpheus.data.repository.ArtistImageRepository
import com.yuukifst.orpheus.data.youtube.YouTubeInitializer
import com.yuukifst.orpheus.presentation.viewmodel.LibraryStateHolder
import com.yuukifst.orpheus.presentation.viewmodel.ThemeStateHolder
import com.yuukifst.orpheus.utils.AlbumArtCacheManager
import com.yuukifst.orpheus.utils.AlbumArtUtils
import com.yuukifst.orpheus.utils.CrashHandler
import com.yuukifst.orpheus.utils.MediaMetadataRetrieverPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class OrpheusApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var localArtworkCoilFetcherFactory: dagger.Lazy<com.yuukifst.orpheus.data.image.LocalArtworkCoilFetcher.Factory>

    @Inject
    lateinit var themeStateHolder: dagger.Lazy<ThemeStateHolder>

    @Inject
    lateinit var artistImageRepository: dagger.Lazy<ArtistImageRepository>

    @Inject
    lateinit var libraryStateHolder: dagger.Lazy<LibraryStateHolder>

    @Inject
    lateinit var userPreferencesRepository: dagger.Lazy<UserPreferencesRepository>

    @Inject
    lateinit var syncManager: dagger.Lazy<com.yuukifst.orpheus.data.worker.SyncManager>

    @Inject
    lateinit var youTubeInitializer: dagger.Lazy<YouTubeInitializer>

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var libraryTrimRestorePending = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "orpheus_music_channel"
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (libraryTrimRestorePending) {
                libraryStateHolder.get().restoreAfterTrimIfNeeded()
                libraryTrimRestorePending = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.BUILD_TYPE != "benchmark") {
            CrashHandler.install(this)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        startupScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Orpheus Music Playback",
                    NotificationManager.IMPORTANCE_LOW,
                )
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
            runCatching { userPreferencesRepository.get().refreshStartupMirrorFromDataStore() }
            youTubeInitializer.get().ensureInitialized()
            // SyncManager.start() registers a ProcessLifecycleOwner observer; that API
            // requires the main thread even when the rest of cold-start work stays on IO.
            withContext(Dispatchers.Main.immediate) {
                syncManager.get().start()
            }
            AlbumArtUtils.migrateLegacyCacheLocation(this@OrpheusApplication)
            val savedLimit = runCatching {
                userPreferencesRepository.get().albumArtCacheLimitMbFlow.first()
            }.getOrNull()
            if (savedLimit != null) {
                AlbumArtCacheManager.configuredCacheLimitMb = savedLimit.toLong()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(localArtworkCoilFetcherFactory.get())
            }
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        imageLoader.get().memoryCache?.trimMemory(level)

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            themeStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            artistImageRepository.get().clearCache()
            MediaMetadataRetrieverPool.clear()
        }

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            libraryTrimRestorePending = true
            libraryStateHolder.get().trimMemory(level)
        }

        if (
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader.get().memoryCache?.clear()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
