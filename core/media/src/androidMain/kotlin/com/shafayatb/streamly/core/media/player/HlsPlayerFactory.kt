package com.shafayatb.streamly.core.media.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource

/**
 * The configuration every app player shares. Each one takes audio focus when it plays, so starting
 * one player pauses any other that is still playing, and pauses when headphones are unplugged.
 */
@OptIn(UnstableApi::class)
internal fun buildHlsPlayer(context: Context, dataSourceFactory: DataSource.Factory): ExoPlayer =
    ExoPlayer.Builder(context)
        // Every catalog stream is HLS. The default track selector and bandwidth meter switch
        // between the playlist's variants as the measured bandwidth changes.
        .setMediaSourceFactory(HlsMediaSource.Factory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
