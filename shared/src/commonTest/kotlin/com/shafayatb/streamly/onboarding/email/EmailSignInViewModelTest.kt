package com.shafayatb.streamly.onboarding.email

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
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
import streamly.shared.generated.resources.error_email_blank
import streamly.shared.generated.resources.error_email_invalid
import streamly.shared.generated.resources.error_storage_unknown

@OptIn(ExperimentalCoroutinesApi::class)
class EmailSignInViewModelTest {

    private val repository = FakeSessionRepository()
    private val savedStateHandle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = EmailSignInViewModel(savedStateHandle, repository)

    @Test
    fun typingUpdatesStateAndSavedState() {
        val viewModel = viewModel()

        viewModel.onIntent(EmailSignInIntent.EmailChanged("jane@example.com"))

        assertEquals("jane@example.com", viewModel.state.value.email)
        assertEquals("jane@example.com", savedStateHandle.get<String>("email"))
    }

    @Test
    fun restoresTypedEmailAfterProcessDeath() {
        viewModel().onIntent(EmailSignInIntent.EmailChanged("jane@exa"))

        val restored = viewModel()

        assertEquals("jane@exa", restored.state.value.email)
    }

    @Test
    fun blankSubmitShowsInlineErrorWithoutSigningIn() {
        val viewModel = viewModel()

        viewModel.onIntent(EmailSignInIntent.Submit)

        assertEquals(UiText.Resource(Res.string.error_email_blank), viewModel.state.value.emailError)
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun invalidSubmitShowsInlineErrorThatClearsOnEdit() {
        val viewModel = viewModel()
        viewModel.onIntent(EmailSignInIntent.EmailChanged("jane@"))

        viewModel.onIntent(EmailSignInIntent.Submit)
        assertEquals(UiText.Resource(Res.string.error_email_invalid), viewModel.state.value.emailError)
        assertEquals(0, repository.writeCount)

        viewModel.onIntent(EmailSignInIntent.EmailChanged("jane@example.com"))
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun validSubmitSignsInWithTrimmedEmailAndNavigatesHome() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(EmailSignInIntent.EmailChanged("  jane@example.com "))

        viewModel.events.test {
            viewModel.onIntent(EmailSignInIntent.Submit)

            assertEquals(EmailSignInEvent.NavigateToHome, awaitItem())
        }
        assertEquals(listOf("jane@example.com"), repository.emailSignIns)
    }

    @Test
    fun submittingStateBlocksRepeatSubmits() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()
        viewModel.onIntent(EmailSignInIntent.EmailChanged("jane@example.com"))

        viewModel.onIntent(EmailSignInIntent.Submit)
        viewModel.onIntent(EmailSignInIntent.Submit)

        assertTrue(viewModel.state.value.isSubmitting)
        assertEquals(1, repository.writeCount)
        gate.complete(Unit)
    }

    @Test
    fun repositoryFailureShowsErrorAndReenablesForm() {
        repository.failure = DataError.Local.UNKNOWN
        val viewModel = viewModel()
        viewModel.onIntent(EmailSignInIntent.EmailChanged("jane@example.com"))

        viewModel.onIntent(EmailSignInIntent.Submit)

        val state = viewModel.state.value
        assertFalse(state.isSubmitting)
        assertEquals(UiText.Resource(Res.string.error_storage_unknown), state.error)
    }

    @Test
    fun backSendsNavigateBack() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(EmailSignInIntent.NavigateBack)

            assertEquals(EmailSignInEvent.NavigateBack, awaitItem())
        }
    }
}
