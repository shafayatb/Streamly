package com.shafayatb.streamly.core.media.shorts

import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.ShortsPlayerLease
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.ShortsPoolState
import com.shafayatb.streamly.domain.shorts.ShortVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One player in a [SlotPool]. The app's slots wrap an ExoPlayer; tests use fakes. */
internal interface PoolSlot {
    /** The short loaded into this slot, set as soon as [load] is called. */
    val shortId: String?
    val playback: PlaybackState

    /** Replaces whatever is loaded with [short] and prepares it, paused, from its start. */
    fun load(short: ShortVideo, muted: Boolean)
    fun play()
    fun pause()
    fun rewind()
    fun setMuted(muted: Boolean)
    fun retry()

    /** Unloads the short and frees its decoders and buffers. The slot can load another short. */
    fun stop()

    /** Frees the player for good. */
    fun release()
}

/**
 * The [ShortsPlayerPool] policy, independent of Media3. Slots are created only when a short
 * needs one and never more than [ShortsPlayerPool.MAX_PLAYERS]; a slot whose short leaves the
 * window is recycled for the next short that enters it, so swiping never creates more players.
 *
 * [createSlot] receives the callback a slot calls whenever its [PoolSlot.playback] changes.
 */
internal class SlotPool<S : PoolSlot>(
    private val createSlot: (onChanged: () -> Unit) -> S,
) : ShortsPlayerPool {

    private val _slots = mutableListOf<S>()

    /** The slots that exist right now, oldest first. */
    val slots: List<S> get() = _slots

    private val _state = MutableStateFlow(ShortsPoolState())
    val state: StateFlow<ShortsPoolState> = _state.asStateFlow()

    private var visibleId: String? = null
    private var isMuted = false
    private var owner: Lease? = null

    override fun acquire(): ShortsPlayerLease {
        // The new owner decides what plays; nothing from the previous one may keep sounding.
        _slots.forEach { it.pause() }
        visibleId = null
        publish()
        return Lease().also { owner = it }
    }

    /** Releases every slot, whoever owns the pool. Koin calls this when it closes. */
    fun releaseAll() {
        _slots.forEach { it.release() }
        _slots.clear()
        visibleId = null
        publish()
    }

    private fun show(visible: ShortVideo, upcoming: ShortVideo?, playWhenReady: Boolean) {
        val wanted = listOfNotNull(visible, upcoming?.takeUnless { it.id == visible.id })
        val wantedIds = wanted.mapTo(mutableSetOf()) { it.id }

        // Silence the short that was on screen before anything else starts.
        _slots.filter { it.shortId != visible.id }.forEach { it.pause() }

        val spare = _slots.filterTo(mutableListOf()) { it.shortId !in wantedIds }
        val holders = wanted.associate { short ->
            val holder = slotHolding(short.id)
                ?: (spare.removeFirstOrNull() ?: newSlot()).also { it.load(short, isMuted) }
            short.id to holder
        }
        spare.forEach { it.stop() }

        upcoming?.let { holders[it.id] }?.takeIf { it.shortId != visible.id }?.rewind()
        val visibleSlot = holders.getValue(visible.id)
        if (playWhenReady) visibleSlot.play() else visibleSlot.pause()
        visibleId = visible.id
        publish()
    }

    private fun setMuted(muted: Boolean) {
        isMuted = muted
        _slots.forEach { it.setMuted(muted) }
        publish()
    }

    private fun visibleSlot(): S? = visibleId?.let(::slotHolding)

    private fun slotHolding(shortId: String): S? = _slots.firstOrNull { it.shortId == shortId }

    private fun newSlot(): S {
        check(_slots.size < ShortsPlayerPool.MAX_PLAYERS) { "The Shorts pool is limited to ${ShortsPlayerPool.MAX_PLAYERS} players" }
        return createSlot(::publish).also { _slots += it }
    }

    private fun publish() {
        _state.value = ShortsPoolState(
            visibleId = visibleId,
            players = _slots.mapNotNull { slot -> slot.shortId?.let { it to slot.playback } }.toMap(),
            isMuted = isMuted,
        )
    }

    private inner class Lease : ShortsPlayerLease {
        private val isOwner: Boolean get() = owner === this

        override val state: StateFlow<ShortsPoolState> get() = this@SlotPool.state

        override fun show(visible: ShortVideo, upcoming: ShortVideo?, playWhenReady: Boolean) {
            if (isOwner) this@SlotPool.show(visible, upcoming, playWhenReady)
        }

        override fun play() {
            if (isOwner) visibleSlot()?.play()
        }

        override fun pause() {
            if (isOwner) visibleSlot()?.pause()
        }

        override fun setMuted(muted: Boolean) {
            if (isOwner) this@SlotPool.setMuted(muted)
        }

        override fun retry() {
            if (isOwner) visibleSlot()?.retry()
        }

        override fun close() {
            if (!isOwner) return
            owner = null
            releaseAll()
        }
    }
}
