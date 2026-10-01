package com.shafayatb.streamly.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>()
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.ContinueWithGoogle -> signIn(SignInMethod.GOOGLE) {
                sessionRepository.signInWithGoogle()
            }
            OnboardingIntent.ContinueAsGuest -> signIn(SignInMethod.GUEST) {
                sessionRepository.continueAsGuest()
            }
            OnboardingIntent.SignInWithEmail -> {
                if (_state.value.isBusy) return
                _state.update { it.copy(error = null) }
                viewModelScope.launch { _events.send(OnboardingEvent.NavigateToEmailSignIn) }
            }
        }
    }

    private fun signIn(
        method: SignInMethod,
        action: suspend () -> EmptyResult<DataError.Local>,
    ) {
        if (_state.value.isBusy) return
        _state.update { it.copy(pendingMethod = method, error = null) }
        viewModelScope.launch {
            action()
                // Stay busy on success so the buttons do not re-enable during the transition.
                .onSuccess { _events.send(OnboardingEvent.NavigateToHome) }
                .onFailure { error ->
                    _state.update { it.copy(pendingMethod = null, error = error.toUiText()) }
                }
        }
    }
}
