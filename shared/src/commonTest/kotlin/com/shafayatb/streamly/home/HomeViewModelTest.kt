package com.shafayatb.streamly.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_network_no_internet

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val recap = testVideo(id = "recap", category = Category.TECH)
    private val synthwave = testVideo(id = "synthwave", category = Category.MUSIC)
    private val radio = testVideo(id = "radio", category = Category.MUSIC, duration = null)
    private val studio = testVideo(id = "studio", category = Category.TECH, duration = null)
    private val repository = FakeVideoRepository(videos = listOf(recap, synthwave, radio, studio))
    private val savedStateHandle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(repository, clock, savedStateHandle)

    private fun HomeViewModel.videoIds(): List<String> =
        assertIs<FeedContent.Loaded>(state.value.feed).videos.map { it.id }

    @Test
    fun showsLoadingUntilTheFeedArrives() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        assertEquals(FeedContent.Loading, viewModel.state.value.feed)

        gate.complete(Unit)
        assertIs<FeedContent.Loaded>(viewModel.state.value.feed)
    }

    @Test
    fun loadedFeedKeepsRepositoryOrderAndFormatsCards() {
        val viewModel = viewModel()

        assertEquals(listOf("recap", "synthwave", "radio", "studio"), viewModel.videoIds())
        val cards = (viewModel.state.value.feed as FeedContent.Loaded).videos
        assertEquals(recap.toCardUi(now), cards.first())
        assertEquals(FeedFilter.ALL, viewModel.state.value.selectedFilter)
    }

    @Test
    fun emptyCatalogIsEmpty() {
        repository.videos = emptyList()

        assertEquals(FeedContent.Empty, viewModel().state.value.feed)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() {
        repository.failure = DataError.Network.NO_INTERNET
        val viewModel = viewModel()
        assertEquals(
            FeedContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            viewModel.state.value.feed,
        )

        repository.failure = null
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        viewModel.onIntent(HomeIntent.Retry)
        assertEquals(FeedContent.Loading, viewModel.state.value.feed)

        gate.complete(Unit)
        assertEquals(4, viewModel.videoIds().size)
        assertEquals(2, repository.feedRequests)
    }

    @Test
    fun retryWhileLoadingDoesNotRequestAgain() {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onIntent(HomeIntent.Retry)

        assertEquals(1, repository.feedRequests)
        repository.gate?.complete(Unit)
    }

    @Test
    fun musicChipShowsOnlyMusic() {
        val viewModel = viewModel()

        viewModel.onIntent(HomeIntent.SelectFilter(FeedFilter.MUSIC))

        assertEquals(FeedFilter.MUSIC, viewModel.state.value.selectedFilter)
        assertEquals(listOf("synthwave", "radio"), viewModel.videoIds())
    }

    @Test
    fun liveChipShowsOnlyLiveStreams() {
        val viewModel = viewModel()

        viewModel.onIntent(HomeIntent.SelectFilter(FeedFilter.LIVE))

        assertEquals(listOf("radio", "studio"), viewModel.videoIds())
    }

    @Test
    fun chipWithNoMatchesIsEmptyAndAllRestoresTheFeed() {
        repository.videos = listOf(recap)
        val viewModel = viewModel()

        viewModel.onIntent(HomeIntent.SelectFilter(FeedFilter.LIVE))
        assertEquals(FeedContent.Empty, viewModel.state.value.feed)

        viewModel.onIntent(HomeIntent.SelectFilter(FeedFilter.ALL))
        assertEquals(listOf("recap"), viewModel.videoIds())
        assertEquals(1, repository.feedRequests)
    }

    @Test
    fun selectedChipIsRestoredFromSavedState() {
        viewModel().onIntent(HomeIntent.SelectFilter(FeedFilter.MUSIC))

        val restored = viewModel()

        assertEquals(FeedFilter.MUSIC, restored.state.value.selectedFilter)
        assertEquals(listOf("synthwave", "radio"), restored.videoIds())
    }

    @Test
    fun openingAVideoNavigatesToItsPlayer() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(HomeIntent.OpenVideo("radio"))

            assertEquals(HomeEvent.NavigateToPlayer("radio"), awaitItem())
        }
    }
}
