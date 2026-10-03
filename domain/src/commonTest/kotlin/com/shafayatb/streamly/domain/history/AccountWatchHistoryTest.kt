package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.download.FakeSessions
import com.shafayatb.streamly.domain.download.video
import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AccountWatchHistoryTest {

    private val anika = Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE))
    private val jane = Session.SignedIn(User("Jane Doe", "jane@example.com", AuthProvider.EMAIL))
    private val anikaKey = "email:anika@streamly.app"

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val store = FakeWatchHistoryStore()
    private val sessions = FakeSessions(anika)

    private fun TestScope.history() = AccountWatchHistory(store, sessions, clock, backgroundScope)

    private suspend fun WatchHistoryRepository.ids(): List<String> =
        (entries.first() as Result.Success).data.map { it.videoId }

    @Test
    fun recordsUnderTheSignedInAccount() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("a"), 2.minutes)

        val expected = WatchHistoryEntry(
            videoId = "a",
            title = "Video a",
            channelName = "Channel",
            thumbnailUrl = "",
            duration = 10.minutes,
            position = 2.minutes,
            watchedAt = now,
        )
        assertEquals(mapOf(anikaKey to listOf(expected)), store.stored.value)
    }

    @Test
    fun eachAccountSeesOnlyItsOwnHistory() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 1.minutes)

        sessions.session.value = jane
        assertEquals(emptyList(), history.ids())
        history.record(video("b"), 1.minutes)
        assertEquals(listOf("b"), history.ids())

        sessions.session.value = anika
        assertEquals(listOf("a"), history.ids())
    }

    @Test
    fun signedOutRecordsNothingAndListsNothing() = runTest(UnconfinedTestDispatcher()) {
        sessions.session.value = null
        val history = history()

        history.record(video("a"), 1.minutes)

        assertEquals(emptyMap(), store.stored.value)
        assertEquals(Result.Success(emptyList<WatchHistoryEntry>()), history.entries.first())
    }

    @Test
    fun writesLandInCallOrder() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        val history = history()

        history.record(video("a"), 1.minutes)
        history.record(video("a"), 2.minutes)
        gate.complete(Unit)

        assertEquals(listOf(2.minutes), store.stored.value.getValue(anikaKey).map { it.position })
    }

    @Test
    fun liveIsRecordedWithoutAPosition() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("live", duration = null), 3.minutes)

        val entry = store.stored.value.getValue(anikaKey).single()
        assertEquals(Duration.ZERO, entry.position)
        assertEquals(null, entry.duration)
    }

    @Test
    fun usesThePlayersDurationWhenKnown() = runTest(UnconfinedTestDispatcher()) {
        history().record(video("a"), 9.minutes, duration = 10.minutes + 3.seconds)

        assertEquals(10.minutes + 3.seconds, store.stored.value.getValue(anikaKey).single().duration)
    }

    @Test
    fun resumeComesFromTheCurrentAccountOnly() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 3.minutes)

        assertEquals(3.minutes, history.resumePosition("a"))
        sessions.session.value = jane
        assertEquals(Duration.ZERO, history.resumePosition("a"))
    }

    @Test
    fun resumeIsZeroWhenTheHistoryCannotBeRead() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 3.minutes)
        store.readFailure = DataError.Local.UNKNOWN

        assertEquals(Duration.ZERO, history.resumePosition("a"))
    }

    @Test
    fun aRemovedVideoIsNotAddedBackByALaterSave() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 10.seconds)
        history.remove("a")

        history.updateProgress(video("a"), 20.seconds)

        assertEquals(emptyList(), history.ids())
    }

    @Test
    fun aLaterSaveUpdatesTheEntryAndMovesItToTheTop() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 10.seconds)
        history.record(video("b"), 10.seconds)

        history.updateProgress(video("a"), 30.seconds)

        val entries = store.stored.value.getValue(anikaKey)
        assertEquals(listOf("a", "b"), entries.map { it.videoId })
        assertEquals(30.seconds, entries.first().position)
    }

    @Test
    fun removeAndClearActOnTheCurrentAccountOnly() = runTest(UnconfinedTestDispatcher()) {
        val history = history()
        history.record(video("a"), 1.minutes)
        history.record(video("b"), 1.minutes)
        sessions.session.value = jane
        history.record(video("c"), 1.minutes)

        history.clear()
        sessions.session.value = anika
        history.remove("b")

        assertEquals(listOf("a"), history.ids())
    }
}
