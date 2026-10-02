package com.shafayatb.streamly.core.media.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.shafayatb.streamly.core.media.R
import org.koin.android.ext.android.inject

/**
 * Keeps downloads running while the app is in the background, as a `dataSync` foreground service
 * with a progress notification.
 */
@OptIn(UnstableApi::class)
public class StreamlyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    private val mediaDownloads: MediaDownloads by inject()
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    // Called once per process, so the listener is added once.
    override fun getDownloadManager(): DownloadManager = mediaDownloads.downloadManager.apply {
        addListener(FinishedNotifier(applicationContext, notificationHelper))
    }

    // Media3 never uses a scheduler on API 31+, and without one the service stays in the
    // foreground until the network returns on every API level, which is the behavior we want.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download_notification,
            openAppIntent(this),
            downloads.firstOrNull { it.state == Download.STATE_DOWNLOADING }?.title(),
            downloads,
            notMetRequirements,
        )

    /** Posts "Download complete" or "Download failed" when a download finishes. */
    private class FinishedNotifier(
        private val context: Context,
        private val helper: DownloadNotificationHelper,
    ) : DownloadManager.Listener {
        private var nextNotificationId = FOREGROUND_NOTIFICATION_ID + 1

        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            val notification = when (download.state) {
                Download.STATE_COMPLETED -> helper.buildDownloadCompletedNotification(
                    context, R.drawable.ic_download_notification, openAppIntent(context), download.title(),
                )
                Download.STATE_FAILED -> helper.buildDownloadFailedNotification(
                    context, R.drawable.ic_download_notification, openAppIntent(context), download.title(),
                )
                else -> return
            }
            NotificationUtil.setNotification(context, nextNotificationId++, notification)
        }
    }

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "downloads"

        fun Download.title(): String = DownloadMetadata.decode(request.data, request.id).title

        fun openAppIntent(context: Context): PendingIntent? =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
    }
}

/**
 * Resumes downloads that a killed process left unfinished. Call it while the app is in the
 * foreground; Android does not let a background app start the service.
 */
@OptIn(UnstableApi::class)
public fun startDownloadService(context: Context) {
    try {
        DownloadService.start(context, StreamlyDownloadService::class.java)
    } catch (e: IllegalStateException) {
        // Started from the background; the next download the user starts starts the service.
    }
}
