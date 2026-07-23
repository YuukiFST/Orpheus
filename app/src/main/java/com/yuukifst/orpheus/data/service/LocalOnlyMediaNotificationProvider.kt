package com.yuukifst.orpheus.data.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's default provider and marks playback notifications as local-only.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class LocalOnlyMediaNotificationProvider(
    private val context: Context,
    private val delegate: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider.Builder(context).build(),
) : MediaNotification.Provider {

    private val stopPendingIntent: PendingIntent by lazy {
        PendingIntent.getService(
            context,
            REQUEST_CODE_STOP_AND_UNLOAD,
            Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP_AND_UNLOAD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val notification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback
        )
        val localOnlyNotification = runCatching {
            Notification.Builder.recoverBuilder(context, notification.notification)
                .setLocalOnly(true)
                .setDeleteIntent(stopPendingIntent)
                .build()
        }.getOrElse {
            notification.notification
        }
        return MediaNotification(notification.notificationId, localOnlyNotification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()

    private companion object {
        private const val REQUEST_CODE_STOP_AND_UNLOAD = 1001
    }
}
