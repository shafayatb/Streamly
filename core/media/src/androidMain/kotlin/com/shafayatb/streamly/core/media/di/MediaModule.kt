package com.shafayatb.streamly.core.media.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.shafayatb.streamly.core.media.cache.MediaCache
import com.shafayatb.streamly.core.media.download.MediaDownloads
import com.shafayatb.streamly.core.media.download.OfflineMediaItems
import com.shafayatb.streamly.core.media.player.ExoVideoPlayer
import com.shafayatb.streamly.core.media.shorts.ExoShortsPlayerPool
import com.shafayatb.streamly.domain.download.DeviceDownloads
import com.shafayatb.streamly.domain.download.DownloadAccess
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.VideoPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

public val mediaModule: Module = module {
    single { MediaCache(androidContext()) } onClose { it?.release() }

    // Application-scoped: the only DownloadManager, writing into the one media cache. It sees
    // every account's downloads, and the device-wide download settings; the app reads the
    // downloads through the account-scoped repository.
    single { MediaDownloads(androidContext(), get(), get()) } onClose { it?.release() } bind DeviceDownloads::class

    // Application-scoped: one player for every normal-video screen, released only with Koin.
    single {
        val downloads = get<MediaDownloads>()
        val access = get<DownloadAccess>()
        ExoVideoPlayer(
            context = androidContext(),
            dataSourceFactory = get<MediaCache>().playbackDataSourceFactory(),
            // Another account's saved copy streams instead, like any video this account has not saved.
            offlineMediaItems = OfflineMediaItems { videoId ->
                if (access.canPlayOffline(videoId)) downloads.completedMediaItem(videoId) else null
            },
            logEvents = androidContext().isDebuggable(),
        )
    } onClose { it?.release() } bind VideoPlayer::class

    // Application-scoped like the media cache it reads through, so only one pool can ever hold
    // players. Its players are screen-scoped: the Shorts screen's lease builds and releases them.
    single {
        ExoShortsPlayerPool(
            context = androidContext(),
            dataSourceFactory = get<MediaCache>().playbackDataSourceFactory(),
            logEvents = androidContext().isDebuggable(),
        )
    } onClose { it?.release() } bind ShortsPlayerPool::class
}

private fun Context.isDebuggable(): Boolean =
    applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
