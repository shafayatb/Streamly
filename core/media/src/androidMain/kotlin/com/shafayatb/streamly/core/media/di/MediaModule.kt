package com.shafayatb.streamly.core.media.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.shafayatb.streamly.core.media.cache.MediaCache
import com.shafayatb.streamly.core.media.download.MediaDownloads
import com.shafayatb.streamly.core.media.download.OfflineMediaItems
import com.shafayatb.streamly.core.media.player.ExoVideoPlayer
import com.shafayatb.streamly.core.media.shorts.ExoShortsPlayerPool
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.VideoPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose

public val mediaModule: Module = module {
    single { MediaCache(androidContext()) } onClose { it?.release() }

    // Application-scoped: the only DownloadManager, writing into the one media cache.
    single { MediaDownloads(androidContext(), get()) } onClose { it?.release() } binds
        arrayOf(DownloadRepository::class, OfflineMediaItems::class)

    // Application-scoped: one player for every normal-video screen, released only with Koin.
    single {
        ExoVideoPlayer(
            context = androidContext(),
            dataSourceFactory = get<MediaCache>().playbackDataSourceFactory(),
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
