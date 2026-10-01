package com.shafayatb.streamly.onboarding.email

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import com.shafayatb.streamly.domain.validation.EmailValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmailSignInViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EmailSignInState(email = savedStateHandle[KEY_EMAIL] ?: ""),
    )
    val state: StateFlow<EmailSignInState> = _state.asStateFlow()

    private val _events = Channel<EmailSignInEvent>()
    val events: Flow<EmailSignInEvent> = _events.receiveAsFlow()

    fun onIntent(intent: EmailSignInIntent) {
        when (intent) {
            is EmailSignInIntent.EmailChanged -> {
                savedStateHandle[KEY_EMAIL] = intent.email
                _state.update { it.copy(email = intent.email, emailError = null, error = null) }
            }
            EmailSignInIntent.Submit -> submit()
            EmailSignInIntent.NavigateBack -> viewModelScope.launch {
                _events.send(EmailSignInEvent.NavigateBack)
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        val validation = EmailValidator.validate(current.email)
        if (validation is Result.Failure) {
            _state.update { it.copy(emailError = validation.error.toUiText()) }
            return
        }
        _state.update { it.copy(isSubmitting = true, emailError = null, error = null) }
        viewModelScope.launch {
            sessionRepository.signInWithEmail(current.email.trim())
                // Stay submitting on success so the form does not re-enable during the transition.
                .onSuccess { _events.send(EmailSignInEvent.NavigateToHome) }
                .onFailure { error ->
                    _state.update { it.copy(isSubmitting = false, error = error.toUiText()) }
                }
        }
    }

    private companion object {
        const val KEY_EMAIL = "email"
    }
}
