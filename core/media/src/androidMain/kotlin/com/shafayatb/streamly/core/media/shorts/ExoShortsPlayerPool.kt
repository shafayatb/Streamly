package com.shafayatb.streamly.core.media.shorts

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.shafayatb.streamly.domain.player.ShortsPlayerLease
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.ShortsPoolState
import kotlinx.coroutines.flow.StateFlow

/**
 * [ShortsPlayerPool] backed by at most two [ExoPlayer]s, separate from the long-form player. Both
 * read through the app's one media cache, so a short behaves like any other stream.
 *
 * Koin holds the pool for the whole app, but its players exist only while a Shorts screen leases
 * them: they are built on its first swipe and released when it closes the lease. Only
 * [ShortSurface] in this module sees them.
 */
public class ExoShortsPlayerPool internal constructor(
    context: Context,
    dataSourceFactory: DataSource.Factory,
    logEvents: Boolean,
) : ShortsPlayerPool {

    private val appContext = context.applicationContext

    private val pool = SlotPool { onChanged ->
        ExoPoolSlot(appContext, dataSourceFactory, logEvents, onChanged)
    }

    internal val state: StateFlow<ShortsPoolState> get() = pool.state

    override fun acquire(): ShortsPlayerLease = pool.acquire()

    /** The player holding [shortId], or `null` while that short has none. */
    internal fun playerFor(shortId: String): ExoPlayer? =
        pool.slots.firstOrNull { it.shortId == shortId }?.exoPlayer

    /** Frees every player, whoever leases them. Only Koin calls this, when it closes. */
    internal fun release() {
        pool.releaseAll()
    }
}
