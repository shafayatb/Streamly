package com.shafayatb.streamly.history

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.history_error_body
import streamly.shared.generated.resources.history_update_failed

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryViewModelTest {

    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val history = FakeWatchHistoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WatchHistoryViewModel(history, clock)

    private fun entry(id: String, position: Duration, duration: Duration?, watchedAgo: Duration) = WatchHistoryEntry(
        videoId = id,
        title = "Video $id",
        channelName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        duration = duration,
        position = position,
        watchedAt = now - watchedAgo,
    )

    private fun show(vararg entries: WatchHistoryEntry) {
        history.result.value = Result.Success(entries.toList())
    }

    @Test
    fun showsLoadingUntilTheHistoryArrives() {
        history.result.value = null

        assertEquals(WatchHistoryContent.Loading, viewModel().state.value.content)
    }

    @Test
    fun listsEntriesWithLengthProgressAndWhenTheyWereWatched() {
        show(
            entry("a", position = 3.minutes, duration = 10.minutes, watchedAgo = 2.hours),
            entry("live", position = Duration.ZERO, duration = null, watchedAgo = 5.minutes),
        )

        val expected = WatchHistoryContent.Loaded(
            persistentListOf(
                HistoryItemUi("a", "Video a", "Channel", "https://example.com/a.jpg", "10:00", false, 0.3f, formatAge(now - 2.hours, now)),
                HistoryItemUi("live", "Video live", "Channel", "https://example.com/live.jpg", null, true, null, formatAge(now - 5.minutes, now)),
            ),
        )
        val state = viewModel().state.value
        assertEquals(expected, state.content)
        assertTrue(state.canClear)
    }

    @Test
    fun aFinishedVideoShowsAFullBar() {
        show(entry("a", position = 10.minutes, duration = 10.minutes, watchedAgo = 1.hours))

        val item = (viewModel().state.value.content as WatchHistoryContent.Loaded).items.single()
        assertEquals(1f, item.progress)
    }

    @Test
    fun anEmptyHistoryHasNothingToClear() {
        val state = viewModel().state.value

        assertEquals(WatchHistoryContent.Empty, state.content)
        assertFalse(state.canClear)
    }

    @Test
    fun aReadFailureShowsTheErrorAndRetryReadsAgain() {
        history.failNextRead = DataError.Local.UNKNOWN
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()
        assertEquals(WatchHistoryContent.Error(UiText.Resource(Res.string.history_error_body)), viewModel.state.value.content)

        viewModel.onIntent(WatchHistoryIntent.RetryLoad)

        assertTrue(viewModel.state.value.content is WatchHistoryContent.Loaded)
    }

    @Test
    fun openingARowPlaysIt() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.Open("a"))
            assertEquals(WatchHistoryEvent.NavigateToPlayer("a"), awaitItem())
        }
    }

    @Test
    fun removingARowRemovesItFromTheHistory() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))

        viewModel().onIntent(WatchHistoryIntent.Remove("a"))

        assertEquals(listOf("a"), history.removed)
    }

    @Test
    fun aFailedRemoveShowsAMessage() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        history.writeFailure = DataError.Local.UNKNOWN
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.Remove("a"))
            assertEquals(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed)), awaitItem())
        }
    }

    @Test
    fun clearAllAsksFirst() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.onIntent(WatchHistoryIntent.RequestClear)
        assertTrue(viewModel.state.value.isClearDialogShown)
        viewModel.onIntent(WatchHistoryIntent.DismissClear)

        assertFalse(viewModel.state.value.isClearDialogShown)
        assertEquals(0, history.clears)
    }

    @Test
    fun confirmingClearsTheHistory() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()

        viewModel.onIntent(WatchHistoryIntent.RequestClear)
        viewModel.onIntent(WatchHistoryIntent.ConfirmClear)

        assertEquals(1, history.clears)
        assertFalse(viewModel.state.value.isClearDialogShown)
    }

    @Test
    fun aFailedClearShowsAMessage() = runTest {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        history.writeFailure = DataError.Local.DISK_FULL
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.RequestClear)
            viewModel.onIntent(WatchHistoryIntent.ConfirmClear)
            assertEquals(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed)), awaitItem())
        }
    }

    @Test
    fun theClearDialogClosesWhenTheHistoryEmpties() {
        show(entry("a", 3.minutes, 10.minutes, 1.hours))
        val viewModel = viewModel()
        viewModel.onIntent(WatchHistoryIntent.RequestClear)

        show()

        assertFalse(viewModel.state.value.isClearDialogShown)
    }

    @Test
    fun backLeavesTheScreen() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(WatchHistoryIntent.NavigateBack)
            assertEquals(WatchHistoryEvent.NavigateBack, awaitItem())
        }
    }
}
