package com.shafayatb.streamly.app

import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.navigation.Route
import com.shafayatb.streamly.testing.FakeSessionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staysLoadingUntilTheSessionIsRead() {
        val pending = object : SessionRepository by FakeSessionRepository() {
            override val session: Flow<Session?> = MutableSharedFlow()
        }

        assertEquals(AppState.Loading, AppViewModel(pending).state.value)
    }

    @Test
    fun startsAtOnboardingWithoutASession() {
        val viewModel = AppViewModel(FakeSessionRepository(initial = null))

        assertEquals(AppState.Ready(Route.Onboarding), viewModel.state.value)
    }

    @Test
    fun startsAtHomeForASignedInUser() {
        val user = User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE)
        val viewModel = AppViewModel(FakeSessionRepository(initial = Session.SignedIn(user)))

        assertEquals(AppState.Ready(Route.Home), viewModel.state.value)
    }

    @Test
    fun startsAtHomeForAGuest() {
        val viewModel = AppViewModel(FakeSessionRepository(initial = Session.Guest))

        assertEquals(AppState.Ready(Route.Home), viewModel.state.value)
    }

    @Test
    fun keepsTheFirstStartRouteWhenTheSessionChangesLater() {
        val repository = FakeSessionRepository(initial = null)
        val viewModel = AppViewModel(repository)

        repository.setSession(Session.Guest)

        assertEquals(AppState.Ready(Route.Onboarding), viewModel.state.value)
    }
}
