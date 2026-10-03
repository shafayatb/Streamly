package com.shafayatb.streamly.app

import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.navigation.Route
import com.shafayatb.streamly.testing.FakeSessionRepository
import com.shafayatb.streamly.testing.FakeSettingsRepository
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

        assertEquals(AppState.Loading, AppViewModel(pending, FakeSettingsRepository()).state.value)
    }

    @Test
    fun startsAtOnboardingWithoutASession() {
        val viewModel = AppViewModel(FakeSessionRepository(initial = null), FakeSettingsRepository())

        assertEquals(AppState.Ready(Route.Onboarding, ThemeMode.SYSTEM), viewModel.state.value)
    }

    @Test
    fun startsAtHomeForASignedInUser() {
        val user = User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE)
        val viewModel = AppViewModel(FakeSessionRepository(initial = Session.SignedIn(user)), FakeSettingsRepository())

        assertEquals(AppState.Ready(Route.Home, ThemeMode.SYSTEM), viewModel.state.value)
    }

    @Test
    fun startsAtHomeForAGuest() {
        val viewModel = AppViewModel(FakeSessionRepository(initial = Session.Guest), FakeSettingsRepository())

        assertEquals(AppState.Ready(Route.Home, ThemeMode.SYSTEM), viewModel.state.value)
    }

    @Test
    fun keepsTheFirstStartRouteWhenTheSessionChangesLater() {
        val repository = FakeSessionRepository(initial = null)
        val viewModel = AppViewModel(repository, FakeSettingsRepository())

        repository.setSession(Session.Guest)

        assertEquals(AppState.Ready(Route.Onboarding, ThemeMode.SYSTEM), viewModel.state.value)
    }

    @Test
    fun staysLoadingUntilTheSettingsAreRead() {
        val viewModel = AppViewModel(FakeSessionRepository(initial = Session.Guest), FakeSettingsRepository(initial = null))

        assertEquals(AppState.Loading, viewModel.state.value)
    }

    @Test
    fun usesTheStoredTheme() {
        val viewModel = AppViewModel(
            FakeSessionRepository(initial = Session.Guest),
            FakeSettingsRepository(initial = AppSettings(themeMode = ThemeMode.DARK)),
        )

        assertEquals(AppState.Ready(Route.Home, ThemeMode.DARK), viewModel.state.value)
    }

    @Test
    fun aThemeChangeKeepsTheStartRoute() {
        val sessions = FakeSessionRepository(initial = null)
        val settings = FakeSettingsRepository()
        val viewModel = AppViewModel(sessions, settings)

        sessions.setSession(Session.Guest)
        settings.current.value = AppSettings(themeMode = ThemeMode.LIGHT)

        assertEquals(AppState.Ready(Route.Onboarding, ThemeMode.LIGHT), viewModel.state.value)
    }
}
