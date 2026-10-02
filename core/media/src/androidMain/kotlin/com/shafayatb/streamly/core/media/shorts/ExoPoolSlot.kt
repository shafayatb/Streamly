package com.shafayatb.streamly.core.media.shorts

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import com.shafayatb.streamly.core.media.player.buildHlsPlayer
import com.shafayatb.streamly.core.media.player.toPlaybackState
import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.shorts.ShortVideo

/** A [PoolSlot] backed by its own [ExoPlayer], which loops whatever short it holds. */
@OptIn(UnstableApi::class)
internal class ExoPoolSlot(
    context: Context,
    dataSourceFactory: DataSource.Factory,
    logEvents: Boolean,
    private val onChanged: () -> Unit,
) : PoolSlot {

    val exoPlayer: ExoPlayer = buildHlsPlayer(context, dataSourceFactory).apply {
        repeatMode = Player.REPEAT_MODE_ONE
        addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            },
        )
        if (logEvents) {
            addAnalyticsListener(EventLogger(LOG_TAG))
            Log.d(LOG_TAG, "Created pool player ${Integer.toHexString(System.identityHashCode(this))}")
        }
    }

    override var shortId: String? = null
        private set

    override var playback: PlaybackState = PlaybackState()
        private set

    override fun load(short: ShortVideo, muted: Boolean) {
        shortId = short.id
        val mediaItem = MediaItem.Builder()
            .setMediaId(short.id)
            .setUri(short.hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        exoPlayer.run {
            volume = if (muted) 0f else 1f
            setMediaItem(mediaItem)
            playWhenReady = false
            prepare()
        }
        publish()
    }

    override fun play() {
        exoPlayer.run {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun rewind() {
        exoPlayer.seekToDefaultPosition()
    }

    override fun setMuted(muted: Boolean) {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    override fun retry() {
        exoPlayer.prepare()
    }

    override fun stop() {
        shortId = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        // Its page may already have left the composition without detaching (see ExoVideoPlayer.stop).
        exoPlayer.clearVideoSurface()
        publish()
    }

    override fun release() {
        shortId = null
        exoPlayer.release()
    }

    private fun publish() {
        playback = exoPlayer.toPlaybackState()
        onChanged()
    }

    companion object {
        const val LOG_TAG = "StreamlyShorts"
    }
}
