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
        val localOnlyNotification = attachLocalOnlyAndDeleteIntent(notification.notification)
        return MediaNotification(notification.notificationId, localOnlyNotification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()

    private fun attachLocalOnlyAndDeleteIntent(base: Notification): Notification {
        return runCatching {
            Notification.Builder.recoverBuilder(context, base)
                .setLocalOnly(true)
                .setDeleteIntent(stopPendingIntent)
                .build()
        }.getOrElse {
            rebuildNotificationWithDeleteIntent(base)
        }
    }

    private fun rebuildNotificationWithDeleteIntent(base: Notification): Notification {
        val channelId = base.channelId ?: getNotificationChannelInfo().id
        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(base.smallIcon)
            .setContentTitle(base.extras.getCharSequence(Notification.EXTRA_TITLE))
            .setContentText(base.extras.getCharSequence(Notification.EXTRA_TEXT))
            .setSubText(base.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            .setLargeIcon(base.getLargeIcon())
            .setContentIntent(base.contentIntent)
            .setDeleteIntent(stopPendingIntent)
            .setLocalOnly(true)
            .setShowWhen(base.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, false))
            .setOnlyAlertOnce(true)
            .setVisibility(base.visibility)
            .setOngoing(base.flags and Notification.FLAG_ONGOING_EVENT != 0)

        base.actions?.forEach { action ->
            builder.addAction(
                Notification.Action.Builder(
                    action.icon,
                    action.title,
                    action.actionIntent,
                ).build()
            )
        }
        return builder.build()
    }

    private companion object {
        private const val REQUEST_CODE_STOP_AND_UNLOAD = 1001
    }
}
