package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.ShortsPlayerLease
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.ShortsPoolState
import com.shafayatb.streamly.domain.shorts.ShortVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [ShortsPlayerPool] that applies commands to its state at once, like streams that never stall.
 * It keeps the visible and upcoming shorts loaded, as the real pool does.
 */
class FakeShortsPlayerPool : ShortsPlayerPool {

    private val _state = MutableStateFlow(ShortsPoolState())

    /** Every `show` call as (visible id, upcoming id, playWhenReady), in order. */
    val shows = mutableListOf<Triple<String, String?, Boolean>>()

    var leases = 0
        private set
    var closed = false
        private set
    var retries = 0
        private set

    override fun acquire(): ShortsPlayerLease {
        leases++
        return Lease()
    }

    /** Simulates something the real players report on their own, such as buffering or an error. */
    fun reportVisible(transform: (PlaybackState) -> PlaybackState) = _state.update { state ->
        val id = state.visibleId ?: return@update state
        state.copy(players = state.players + (id to transform(state.players.getValue(id))))
    }

    private inner class Lease : ShortsPlayerLease {
        override val state: StateFlow<ShortsPoolState> = _state.asStateFlow()

        override fun show(visible: ShortVideo, upcoming: ShortVideo?, playWhenReady: Boolean) {
            shows += Triple(visible.id, upcoming?.id, playWhenReady)
            _state.update { state ->
                val players = buildMap {
                    put(visible.id, ready(visible.id, playWhenReady, state.isMuted))
                    upcoming?.let { put(it.id, ready(it.id, playWhenReady = false, state.isMuted)) }
                }
                state.copy(visibleId = visible.id, players = players)
            }
        }

        override fun play() = updateVisible { it.copy(playWhenReady = true) }

        override fun pause() = updateVisible { it.copy(playWhenReady = false) }

        override fun setMuted(muted: Boolean) = _state.update { state ->
            state.copy(isMuted = muted, players = state.players.mapValues { it.value.copy(isMuted = muted) })
        }

        override fun retry() {
            retries++
            updateVisible { it.copy(status = PlaybackStatus.BUFFERING, error = null) }
        }

        override fun close() {
            closed = true
            _state.update { it.copy(visibleId = null, players = emptyMap()) }
        }

        private fun updateVisible(transform: (PlaybackState) -> PlaybackState) = reportVisible(transform)
    }

    private fun ready(id: String, playWhenReady: Boolean, muted: Boolean) = PlaybackState(
        videoId = id,
        status = PlaybackStatus.READY,
        playWhenReady = playWhenReady,
        isMuted = muted,
    )
}
