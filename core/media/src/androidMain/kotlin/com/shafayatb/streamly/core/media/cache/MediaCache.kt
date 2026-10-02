package com.shafayatb.streamly.core.media.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The app's only Media3 cache. Downloads write into it, and playback reads from it before
 * going to the network, so a downloaded video plays offline through the same player.
 *
 * It never evicts, because evicting would silently delete downloads. That is also why playback
 * does not write streamed media into it: only downloads fill the cache.
 */
@OptIn(UnstableApi::class)
internal class MediaCache(context: Context) {

    private val appContext = context.applicationContext

    /** Shared with `DownloadManager`, whose download index lives in the same database. */
    val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(appContext)

    // SimpleCache locks its folder, so the process must create exactly one, for its whole life.
    val cache: Cache = SimpleCache(
        File(appContext.filesDir, "media-cache"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    val upstreamDataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
        appContext,
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
    )

    /** Reads cached media first and streams the rest without caching it. */
    fun playbackDataSourceFactory(): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun release() {
        cache.release()
    }
}
