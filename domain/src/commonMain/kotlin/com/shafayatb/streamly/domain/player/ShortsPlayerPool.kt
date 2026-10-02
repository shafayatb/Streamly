package com.shafayatb.streamly.domain.player

import com.shafayatb.streamly.domain.shorts.ShortVideo
import kotlinx.coroutines.flow.StateFlow

/**
 * The Shorts pager's players, kept apart from the long-form [VideoPlayer]. The pool never holds
 * more than [MAX_PLAYERS]: one for the visible short and one for the short after it, prepared
 * but paused so the next swipe starts at once. Every other short holds no player.
 *
 * A screen takes the pool over with [acquire] and gives it back by closing the lease, which
 * releases the players. Only the newest lease controls the pool, so a Shorts screen that is still
 * leaving can never pause or release the players of the screen that replaced it.
 *
 * Call it from the main thread.
 */
public interface ShortsPlayerPool {
    /** Hands the pool to a new owner. Any earlier lease stops having an effect. */
    public fun acquire(): ShortsPlayerLease

    public companion object {
        public const val MAX_PLAYERS: Int = 2
    }
}

/** One screen's hold on the [ShortsPlayerPool]. Every command is ignored once it is closed or replaced. */
public interface ShortsPlayerLease : AutoCloseable {
    public val state: StateFlow<ShortsPoolState>

    /**
     * Makes [visible] the short on screen: it plays if [playWhenReady], and [upcoming] (normally
     * the next page) is prepared paused from its start. Any other short loses its player. A player
     * that already holds [visible] or [upcoming] keeps it, so swiping back and forth never reloads.
     */
    public fun show(visible: ShortVideo, upcoming: ShortVideo?, playWhenReady: Boolean)

    /** Plays the visible short. */
    public fun play()

    /** Pauses the visible short. */
    public fun pause()

    /** Mute is shared by every short, including the ones loaded later. */
    public fun setMuted(muted: Boolean)

    /** Prepares the visible short again after [PlaybackState.error]. */
    public fun retry()

    /** Releases every player. The next [ShortsPlayerPool.acquire] builds them again. */
    override fun close()
}

public data class ShortsPoolState(
    /** The short on screen, or `null` before the first [ShortsPlayerLease.show]. */
    val visibleId: String? = null,
    /** The state of each short that holds a player, keyed by short id. */
    val players: Map<String, PlaybackState> = emptyMap(),
    val isMuted: Boolean = false,
) {
    val visible: PlaybackState? get() = visibleId?.let(players::get)
}
