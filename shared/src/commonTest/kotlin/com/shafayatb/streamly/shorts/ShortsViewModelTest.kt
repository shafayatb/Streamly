package com.shafayatb.streamly.shorts

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.player.PlaybackError
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeShortsPlayerPool
import com.shafayatb.streamly.testing.FakeShortsRepository
import com.shafayatb.streamly.testing.testShort
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.player_error_network
import streamly.shared.generated.resources.player_share_unavailable
import streamly.shared.generated.resources.shorts_comments_unavailable

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsViewModelTest {

    private val shorts = listOf(
        testShort("s0", likeCount = 999),
        testShort("s1"),
        testShort("s2"),
    )
    private val repository = FakeShortsRepository(shorts)
    private val pool = FakeShortsPlayerPool()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ShortsViewModel(repository, pool)

    private fun shown(visible: String, upcoming: String?, playWhenReady: Boolean) =
        Triple(visible, upcoming, playWhenReady)

    private fun ShortsViewModel.loadedShorts() = assertIs<ShortsContent.Loaded>(state.value.content).shorts

    @Test
    fun showsLoadingUntilTheShortsArrive() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        assertEquals(ShortsContent.Loading, viewModel.state.value.content)

        gate.complete(Unit)
        assertEquals(listOf("s0", "s1", "s2"), viewModel.loadedShorts().map { it.id })
        assertEquals("@channel", "@" + viewModel.loadedShorts().first().channelHandle)
        assertEquals("999", viewModel.loadedShorts().first().likes)
    }

    @Test
    fun leasesThePoolButPlaysNothingUntilAPageSettles() {
        viewModel()

        assertEquals(1, pool.leases)
        assertTrue(pool.shows.isEmpty())
    }

    @Test
    fun emptyCatalogShowsEmptyAndRetryReloads() {
        repository.shorts = emptyList()
        val viewModel = viewModel()
        assertEquals(ShortsContent.Empty, viewModel.state.value.content)

        repository.shorts = shorts
        viewModel.onIntent(ShortsIntent.Retry)

        assertEquals(3, viewModel.loadedShorts().size)
    }

    @Test
    fun failureShowsTheErrorAndRetryRecovers() {
        repository.failure = DataError.Network.NO_INTERNET
        val viewModel = viewModel()
        assertEquals(
            ShortsContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            viewModel.state.value.content,
        )

        repository.failure = null
        viewModel.onIntent(ShortsIntent.Retry)

        assertEquals(2, repository.requests)
        assertEquals(3, viewModel.loadedShorts().size)
    }

    @Test
    fun settledPagePlaysThatShortAndPreparesTheNextOne() {
        val viewModel = viewModel()

        viewModel.onIntent(ShortsIntent.PageSettled(0))
        viewModel.onIntent(ShortsIntent.PageSettled(1))

        assertEquals(listOf(shown("s0", "s1", true), shown("s1", "s2", true)), pool.shows)
        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun lastPageHasNoUpcomingShort() {
        val viewModel = viewModel()

        viewModel.onIntent(ShortsIntent.PageSettled(2))

        assertEquals(shown("s2", null, true), pool.shows.single())
    }

    @Test
    fun reSettlingOnThePlayingPageDoesNotReloadIt() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(1))

        viewModel.onIntent(ShortsIntent.PageSettled(1))

        assertEquals(1, pool.shows.size)
    }

    @Test
    fun pagesOutsideTheListAreIgnored() {
        val viewModel = viewModel()

        viewModel.onIntent(ShortsIntent.PageSettled(7))

        assertTrue(pool.shows.isEmpty())
    }

    @Test
    fun tapPausesAndResumesTheCurrentShort() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))
        assertFalse(viewModel.state.value.playback.isPaused)

        viewModel.onIntent(ShortsIntent.TogglePlayPause)
        assertTrue(viewModel.state.value.playback.isPaused)

        viewModel.onIntent(ShortsIntent.TogglePlayPause)
        assertFalse(viewModel.state.value.playback.isPaused)
    }

    @Test
    fun muteIsSharedAcrossPages() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))

        viewModel.onIntent(ShortsIntent.ToggleMute)
        viewModel.onIntent(ShortsIntent.PageSettled(1))

        assertTrue(viewModel.state.value.isMuted)
        viewModel.onIntent(ShortsIntent.ToggleMute)
        assertFalse(viewModel.state.value.isMuted)
    }

    @Test
    fun showsBufferingAndPlaybackErrorsForTheCurrentShort() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))

        pool.reportVisible { it.copy(status = PlaybackStatus.BUFFERING) }
        assertTrue(viewModel.state.value.playback.isBuffering)

        pool.reportVisible { it.copy(status = PlaybackStatus.IDLE, error = PlaybackError.NETWORK) }
        assertEquals(UiText.Resource(Res.string.player_error_network), viewModel.state.value.playback.error)
        assertFalse(viewModel.state.value.playback.isPaused)

        viewModel.onIntent(ShortsIntent.RetryPlayback)
        assertEquals(1, pool.retries)
        assertNull(viewModel.state.value.playback.error)
    }

    @Test
    fun tappingAFailedShortDoesNotPauseItsRetry() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))
        pool.reportVisible { it.copy(status = PlaybackStatus.IDLE, error = PlaybackError.NETWORK) }

        viewModel.onIntent(ShortsIntent.TogglePlayPause)
        viewModel.onIntent(ShortsIntent.RetryPlayback)

        assertFalse(viewModel.state.value.playback.isPaused)
    }

    @Test
    fun backgroundPausesAndReturnResumes() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))

        viewModel.onIntent(ShortsIntent.ScreenHidden)
        assertTrue(viewModel.state.value.playback.isPaused)

        viewModel.onIntent(ShortsIntent.ScreenShown)
        assertFalse(viewModel.state.value.playback.isPaused)
    }

    @Test
    fun aShortPausedByTheUserStaysPausedAfterTheBackground() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.PageSettled(0))
        viewModel.onIntent(ShortsIntent.TogglePlayPause)

        viewModel.onIntent(ShortsIntent.ScreenHidden)
        viewModel.onIntent(ShortsIntent.ScreenShown)

        assertTrue(viewModel.state.value.playback.isPaused)
    }

    @Test
    fun aPageThatSettlesWhileHiddenWaitsToBeSeen() {
        val viewModel = viewModel()
        viewModel.onIntent(ShortsIntent.ScreenHidden)

        viewModel.onIntent(ShortsIntent.PageSettled(0))
        assertEquals(shown("s0", "s1", false), pool.shows.single())

        viewModel.onIntent(ShortsIntent.ScreenShown)
        assertFalse(viewModel.state.value.playback.isPaused)
    }

    @Test
    fun likeTogglesAndCountsTheUsersLike() {
        val viewModel = viewModel()

        viewModel.onIntent(ShortsIntent.ToggleLike("s0"))
        viewModel.loadedShorts().first().let {
            assertTrue(it.isLiked)
            assertEquals("1K", it.likes)
        }
        assertFalse(viewModel.loadedShorts()[1].isLiked)

        viewModel.onIntent(ShortsIntent.ToggleLike("s0"))
        assertFalse(viewModel.loadedShorts().first().isLiked)
    }

    @Test
    fun commentAndShareAreStubsThatSaySo() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(ShortsIntent.Comment("s0"))
            assertEquals(
                ShortsEvent.ShowMessage(UiText.Resource(Res.string.shorts_comments_unavailable)),
                awaitItem(),
            )
            viewModel.onIntent(ShortsIntent.Share("s0"))
            assertEquals(
                ShortsEvent.ShowMessage(UiText.Resource(Res.string.player_share_unavailable)),
                awaitItem(),
            )
        }
    }

    @Test
    fun clearingTheScreenReleasesThePlayers() {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })
            .get(ShortsViewModel::class)
        viewModel.onIntent(ShortsIntent.PageSettled(0))

        store.clear()

        assertTrue(pool.closed)
    }
}
