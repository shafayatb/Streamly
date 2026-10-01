package com.shafayatb.streamly.player

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.error_network_not_found

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val video = testVideo(id = "v1", title = "Media3 in 10 minutes", channelName = "CodeLabs")
    private val repository = FakeVideoRepository(videos = listOf(video))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsLoadingUntilTheVideoArrives() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = PlayerViewModel("v1", repository)

        assertEquals(PlayerContent.Loading, viewModel.state.value.content)

        gate.complete(Unit)
        assertEquals(PlayerContent.Loaded(video.toDetailsUi()), viewModel.state.value.content)
    }

    @Test
    fun loadsTheRequestedVideo() {
        val viewModel = PlayerViewModel("v1", repository)

        val details = (viewModel.state.value.content as PlayerContent.Loaded).video
        assertEquals("Media3 in 10 minutes", details.title)
        assertEquals("CodeLabs", details.channelName)
    }

    @Test
    fun unknownVideoShowsNotFound() {
        val viewModel = PlayerViewModel("missing", repository)

        assertEquals(
            PlayerContent.Error(UiText.Resource(Res.string.error_network_not_found)),
            viewModel.state.value.content,
        )
    }

    @Test
    fun retryReloadsAfterAFailure() {
        repository.failure = DataError.Network.NO_INTERNET
        val viewModel = PlayerViewModel("v1", repository)
        assertEquals(
            PlayerContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            viewModel.state.value.content,
        )

        repository.failure = null
        viewModel.onIntent(PlayerIntent.Retry)

        assertEquals(PlayerContent.Loaded(video.toDetailsUi()), viewModel.state.value.content)
    }

    @Test
    fun backSendsNavigateBack() = runTest {
        val viewModel = PlayerViewModel("v1", repository)

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.NavigateBack)

            assertEquals(PlayerEvent.NavigateBack, awaitItem())
        }
    }
}
