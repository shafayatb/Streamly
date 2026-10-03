package com.shafayatb.streamly.core.media.shorts

import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.ShortsPlayerLease
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.video.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SlotPoolTest {

    private val shorts = List(10) { index ->
        ShortVideo(
            id = "s$index",
            title = "Short $index",
            channel = Channel(id = "c", name = "Channel"),
            hlsUrl = "https://example.com/s$index.m3u8",
            likeCount = 0,
            commentCount = 0,
        )
    }

    /** How many slots are playing at once, and the most there have ever been. */
    private var playingNow = 0
    private var mostPlayingAtOnce = 0
    private val created = mutableListOf<FakeSlot>()

    private val pool = SlotPool { onChanged -> FakeSlot(onChanged).also { created += it } }

    private fun showPage(lease: ShortsPlayerLease, index: Int, play: Boolean = true) =
        lease.show(shorts[index], shorts.getOrNull(index + 1), playWhenReady = play)

    private fun slotHolding(index: Int): FakeSlot? = pool.slots.firstOrNull { it.shortId == shorts[index].id }

    @Test
    fun playsTheVisibleShortAndPreparesOnlyTheNextOne() {
        val lease = pool.acquire()

        showPage(lease, 0)

        assertEquals(2, pool.slots.size)
        assertTrue(slotHolding(0)!!.isPlaying)
        val next = slotHolding(1)!!
        assertFalse(next.isPlaying)
        assertEquals(Duration.ZERO, next.position)
        assertEquals(setOf("s0", "s1"), lease.state.value.players.keys)
        assertEquals("s0", lease.state.value.visibleId)
    }

    @Test
    fun farPagesHoldNoPlayer() {
        val lease = pool.acquire()

        showPage(lease, 4)

        assertEquals(setOf("s4", "s5"), pool.slots.mapNotNull { it.shortId }.toSet())
        assertTrue(shorts.filterNot { it.id == "s4" || it.id == "s5" }.none { it.id in lease.state.value.players })
    }

    @Test
    fun swipingForwardPlaysThePreparedPlayerAndRecyclesTheOldOne() {
        val lease = pool.acquire()
        showPage(lease, 0)
        val first = slotHolding(0)!!
        val second = slotHolding(1)!!

        showPage(lease, 1)

        assertEquals(2, created.size)
        assertSame(second, slotHolding(1))
        assertEquals(1, second.loads, "the prepared short must not load again")
        assertTrue(second.isPlaying)
        assertSame(first, slotHolding(2), "the old visible player is recycled for the new neighbour")
        assertFalse(first.isPlaying)
        assertNull(slotHolding(0))
    }

    @Test
    fun swipingBackKeepsTheLeftShortAsAPausedRewoundNeighbour() {
        val lease = pool.acquire()
        showPage(lease, 1)
        val left = slotHolding(1)!!
        left.position = 5.seconds

        showPage(lease, 0)

        assertSame(left, slotHolding(1))
        assertEquals(1, left.loads)
        assertFalse(left.isPlaying)
        assertEquals(Duration.ZERO, left.position)
        assertTrue(slotHolding(0)!!.isPlaying)
        assertNull(slotHolding(2))
    }

    @Test
    fun fastSwipesAndJumpsNeverCreateMoreThanTwoPlayersOrPlayTwoAtOnce() {
        val lease = pool.acquire()
        val pages = (0..9) + (9 downTo 0) + listOf(0, 7, 3, 9, 0, 5, 6, 5)

        pages.forEach { page ->
            showPage(lease, page)
            assertTrue(pool.slots.size <= ShortsPlayerPool.MAX_PLAYERS)
            assertEquals(1, pool.slots.count { it.isPlaying })
            assertTrue(slotHolding(page)!!.isPlaying)
        }

        assertEquals(ShortsPlayerPool.MAX_PLAYERS, created.size)
        assertEquals(1, mostPlayingAtOnce)
        assertTrue(created.none { it.released })
    }

    @Test
    fun lastPageStopsTheSparePlayerWithoutReleasingIt() {
        val lease = pool.acquire()
        showPage(lease, 8)

        showPage(lease, 9)

        assertEquals(setOf("s9"), lease.state.value.players.keys)
        val spare = pool.slots.single { it.shortId == null }
        assertEquals(1, spare.stops)
        assertFalse(spare.released)
    }

    @Test
    fun showingWhileHiddenPreparesTheVisibleShortPaused() {
        val lease = pool.acquire()

        showPage(lease, 0, play = false)

        assertTrue(pool.slots.none { it.isPlaying })
        assertEquals(setOf("s0", "s1"), lease.state.value.players.keys)
    }

    @Test
    fun playPauseAndRetryActOnTheVisibleShortOnly() {
        val lease = pool.acquire()
        showPage(lease, 0)

        lease.pause()
        assertTrue(pool.slots.none { it.isPlaying })

        lease.play()
        assertTrue(slotHolding(0)!!.isPlaying)
        assertFalse(slotHolding(1)!!.isPlaying)

        lease.retry()
        assertEquals(1, slotHolding(0)!!.retries)
        assertEquals(0, slotHolding(1)!!.retries)
    }

    @Test
    fun muteIsSharedByEveryPlayerIncludingLaterOnes() {
        val lease = pool.acquire()
        showPage(lease, 8)

        lease.setMuted(true)
        assertTrue(pool.slots.all { it.muted })
        assertTrue(lease.state.value.isMuted)

        showPage(lease, 0)
        assertTrue(pool.slots.all { it.muted })
    }

    @Test
    fun closingTheLeaseReleasesEveryPlayer() {
        val lease = pool.acquire()
        showPage(lease, 0)

        lease.close()

        assertTrue(pool.slots.isEmpty())
        assertTrue(created.all { it.released })
        assertEquals(emptyMap(), lease.state.value.players)

        val next = pool.acquire()
        showPage(next, 0)
        assertEquals(4, created.size, "a new lease builds fresh players")
    }

    @Test
    fun aReplacedLeaseCanNeitherControlNorReleaseThePlayers() {
        val stale = pool.acquire()
        showPage(stale, 3)
        val current = pool.acquire()
        showPage(current, 0)

        stale.pause()
        stale.show(shorts[6], shorts[7], playWhenReady = true)
        stale.setMuted(true)
        stale.close()

        assertTrue(slotHolding(0)!!.isPlaying)
        assertFalse(current.state.value.isMuted)
        assertTrue(created.none { it.released })
    }

    @Test
    fun acquiringSilencesThePreviousOwner() {
        val stale = pool.acquire()
        showPage(stale, 0)

        val current = pool.acquire()

        assertTrue(pool.slots.none { it.isPlaying })
        assertNull(current.state.value.visibleId)
    }

    @Test
    fun releaseAllFreesThePlayersWhoeverLeasesThem() {
        showPage(pool.acquire(), 0)

        pool.releaseAll()

        assertTrue(created.all { it.released })
        assertTrue(pool.slots.isEmpty())
    }

    @Test
    fun publishesEachPlayersStateAsItChanges() {
        val lease = pool.acquire()
        showPage(lease, 0)

        slotHolding(0)!!.report(PlaybackStatus.BUFFERING)

        assertTrue(lease.state.value.visible!!.isBuffering)
        assertEquals(PlaybackStatus.READY, lease.state.value.players.getValue("s1").status)
    }

    private inner class FakeSlot(private val onChanged: () -> Unit) : PoolSlot {
        override var shortId: String? = null
        var isPlaying = false
            private set
        var muted = false
            private set
        var position: Duration = Duration.ZERO
        var status = PlaybackStatus.IDLE
        var loads = 0
        var stops = 0
        var retries = 0
        var released = false

        override val playback: PlaybackState
            get() = PlaybackState(
                videoId = shortId,
                status = status,
                playWhenReady = isPlaying,
                position = position,
                isMuted = muted,
            )

        override fun load(short: ShortVideo, muted: Boolean) {
            check(!released)
            setPlaying(false)
            shortId = short.id
            this.muted = muted
            position = Duration.ZERO
            status = PlaybackStatus.READY
            loads++
            onChanged()
        }

        override fun play() = setPlaying(true)

        override fun pause() = setPlaying(false)

        override fun rewind() {
            position = Duration.ZERO
        }

        override fun setMuted(muted: Boolean) {
            this.muted = muted
        }

        override fun retry() {
            retries++
        }

        override fun stop() {
            setPlaying(false)
            shortId = null
            status = PlaybackStatus.IDLE
            stops++
        }

        override fun release() {
            setPlaying(false)
            shortId = null
            released = true
        }

        fun report(newStatus: PlaybackStatus) {
            status = newStatus
            onChanged()
        }

        private fun setPlaying(playing: Boolean) {
            if (playing == isPlaying) return
            check(!released)
            isPlaying = playing
            playingNow += if (playing) 1 else -1
            mostPlayingAtOnce = maxOf(mostPlayingAtOnce, playingNow)
        }
    }
}
