package com.shafayatb.streamly.profile

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeSessionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import streamly.shared.generated.resources.profile_settings_soon
import streamly.shared.generated.resources.profile_sign_out_failed

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val anika = Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE))
    private val anikaAccount = ProfileAccount.SignedIn("Anika Rahman", "anika@streamly.app", "AR")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsUntilThereIsASession() {
        assertEquals(ProfileAccount.Loading, ProfileViewModel(FakeSessionRepository(null)).state.value.account)
    }

    @Test
    fun showsTheSignedInAccount() {
        assertEquals(anikaAccount, ProfileViewModel(FakeSessionRepository(anika)).state.value.account)
    }

    @Test
    fun showsAGuest() {
        assertEquals(ProfileAccount.Guest, ProfileViewModel(FakeSessionRepository(Session.Guest)).state.value.account)
    }

    @Test
    fun signOutAsksFirst() {
        val sessions = FakeSessionRepository(anika)
        val viewModel = ProfileViewModel(sessions)

        viewModel.onIntent(ProfileIntent.RequestSignOut)

        assertEquals(ProfileDialog.SIGN_OUT, viewModel.state.value.dialog)
        assertEquals(0, sessions.writeCount)
    }

    @Test
    fun aGuestIsAskedToLeaveGuestMode() {
        val viewModel = ProfileViewModel(FakeSessionRepository(Session.Guest))

        viewModel.onIntent(ProfileIntent.RequestSignOut)

        assertEquals(ProfileDialog.LEAVE_GUEST, viewModel.state.value.dialog)
    }

    @Test
    fun cancelKeepsTheSession() {
        val sessions = FakeSessionRepository(anika)
        val viewModel = ProfileViewModel(sessions)
        viewModel.onIntent(ProfileIntent.RequestSignOut)

        viewModel.onIntent(ProfileIntent.DismissDialog)

        assertNull(viewModel.state.value.dialog)
        assertEquals(anika, sessions.session.value)
        assertEquals(0, sessions.writeCount)
    }

    @Test
    fun confirmingSignsOutAndGoesToOnboarding() = runTest {
        val sessions = FakeSessionRepository(anika)
        val viewModel = ProfileViewModel(sessions)

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.RequestSignOut)
            viewModel.onIntent(ProfileIntent.ConfirmSignOut)

            assertEquals(ProfileEvent.NavigateToOnboarding, awaitItem())
        }
        assertNull(sessions.session.value)
        // The header keeps the account while the screen leaves, rather than emptying.
        assertEquals(anikaAccount, viewModel.state.value.account)
    }

    @Test
    fun aSecondConfirmWhileSigningOutIsIgnored() {
        val sessions = FakeSessionRepository(anika).apply { gate = CompletableDeferred() }
        val viewModel = ProfileViewModel(sessions)
        viewModel.onIntent(ProfileIntent.RequestSignOut)

        viewModel.onIntent(ProfileIntent.ConfirmSignOut)
        viewModel.onIntent(ProfileIntent.ConfirmSignOut)
        viewModel.onIntent(ProfileIntent.DismissDialog)

        assertEquals(1, sessions.writeCount)
        assertTrue(viewModel.state.value.isSigningOut)
        assertEquals(ProfileDialog.SIGN_OUT, viewModel.state.value.dialog)
    }

    @Test
    fun aFailedSignOutStaysSignedInAndSaysSo() = runTest {
        val sessions = FakeSessionRepository(anika).apply { failure = DataError.Local.UNKNOWN }
        val viewModel = ProfileViewModel(sessions)

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.RequestSignOut)
            viewModel.onIntent(ProfileIntent.ConfirmSignOut)

            assertEquals(ProfileEvent.ShowMessage(UiText.Resource(Res.string.profile_sign_out_failed)), awaitItem())
        }
        assertEquals(anika, sessions.session.value)
        assertNull(viewModel.state.value.dialog)
        assertFalse(viewModel.state.value.isSigningOut)
    }

    @Test
    fun confirmWithoutTheDialogDoesNothing() {
        val sessions = FakeSessionRepository(anika)

        ProfileViewModel(sessions).onIntent(ProfileIntent.ConfirmSignOut)

        assertEquals(0, sessions.writeCount)
    }

    @Test
    fun downloadsOpensTheDownloadsTab() = runTest {
        val viewModel = ProfileViewModel(FakeSessionRepository(anika))

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.OpenDownloads)

            assertEquals(ProfileEvent.NavigateToDownloads, awaitItem())
        }
    }

    @Test
    fun watchHistoryOpensTheHistory() = runTest {
        val viewModel = ProfileViewModel(FakeSessionRepository(anika))

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.OpenHistory)
            assertEquals(ProfileEvent.NavigateToHistory, awaitItem())
        }
    }

    @Test
    fun settingsAreComingSoon() = runTest {
        val viewModel = ProfileViewModel(FakeSessionRepository(anika))

        viewModel.events.test {
            viewModel.onIntent(ProfileIntent.OpenSettings)
            assertEquals(ProfileEvent.ShowMessage(UiText.Resource(Res.string.profile_settings_soon)), awaitItem())
        }
    }
}
