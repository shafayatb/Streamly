package com.shafayatb.streamly.onboarding

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeSessionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_storage_full

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val repository = FakeSessionRepository()
    private lateinit var viewModel: OnboardingViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = OnboardingViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun googleSignInStoresSessionAndNavigatesHome() = runTest {
        viewModel.events.test {
            viewModel.onIntent(OnboardingIntent.ContinueWithGoogle)

            assertEquals(OnboardingEvent.NavigateToHome, awaitItem())
        }
        assertIs<Session.SignedIn>(repository.session.value)
    }

    @Test
    fun guestStoresGuestSessionAndNavigatesHome() = runTest {
        viewModel.events.test {
            viewModel.onIntent(OnboardingIntent.ContinueAsGuest)

            assertEquals(OnboardingEvent.NavigateToHome, awaitItem())
        }
        assertEquals(Session.Guest, repository.session.value)
    }

    @Test
    fun emailChoiceNavigatesToEmailSignInWithoutWriting() = runTest {
        viewModel.events.test {
            viewModel.onIntent(OnboardingIntent.SignInWithEmail)

            assertEquals(OnboardingEvent.NavigateToEmailSignIn, awaitItem())
        }
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun showsPendingMethodWhileSigningInAndIgnoresRepeatTaps() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        viewModel.onIntent(OnboardingIntent.ContinueWithGoogle)
        viewModel.onIntent(OnboardingIntent.ContinueAsGuest)
        viewModel.onIntent(OnboardingIntent.ContinueWithGoogle)

        assertEquals(SignInMethod.GOOGLE, viewModel.state.value.pendingMethod)
        assertEquals(1, repository.writeCount)
        gate.complete(Unit)
    }

    @Test
    fun failureShowsErrorAndReenablesActions() = runTest {
        repository.failure = DataError.Local.DISK_FULL

        viewModel.onIntent(OnboardingIntent.ContinueAsGuest)

        val state = viewModel.state.value
        assertNull(state.pendingMethod)
        assertEquals(UiText.Resource(Res.string.error_storage_full), state.error)
        assertNull(repository.session.value)
    }

    @Test
    fun retryClearsPreviousError() = runTest {
        repository.failure = DataError.Local.UNKNOWN
        viewModel.onIntent(OnboardingIntent.ContinueWithGoogle)
        repository.failure = null
        repository.gate = CompletableDeferred()

        viewModel.onIntent(OnboardingIntent.ContinueWithGoogle)

        assertNull(viewModel.state.value.error)
        repository.gate?.complete(Unit)
    }
}
