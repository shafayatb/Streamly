package com.shafayatb.streamly.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.profile_sign_out_failed

class ProfileViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // A null session after sign-out keeps the last account while the screen leaves.
            sessionRepository.session.filterNotNull().collect { session ->
                _state.update { it.copy(account = session.toAccount()) }
            }
        }
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.OpenDownloads -> send(ProfileEvent.NavigateToDownloads)
            ProfileIntent.OpenHistory -> send(ProfileEvent.NavigateToHistory)
            ProfileIntent.OpenSettings -> send(ProfileEvent.NavigateToSettings)
            ProfileIntent.RequestSignOut -> _state.update {
                when (it.account) {
                    ProfileAccount.Loading -> it
                    ProfileAccount.Guest -> it.copy(dialog = ProfileDialog.LEAVE_GUEST)
                    is ProfileAccount.SignedIn -> it.copy(dialog = ProfileDialog.SIGN_OUT)
                }
            }
            ProfileIntent.ConfirmSignOut -> signOut()
            ProfileIntent.DismissDialog -> _state.update { if (it.isSigningOut) it else it.copy(dialog = null) }
        }
    }

    private fun signOut() {
        val current = _state.value
        if (current.dialog == null || current.isSigningOut) return
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            when (sessionRepository.signOut()) {
                // The dialog stays until the screen is gone, so nothing flashes on the way out.
                is Result.Success -> _events.send(ProfileEvent.NavigateToOnboarding)
                is Result.Failure -> {
                    _state.update { it.copy(dialog = null, isSigningOut = false) }
                    _events.send(ProfileEvent.ShowMessage(UiText.Resource(Res.string.profile_sign_out_failed)))
                }
            }
        }
    }

    private fun send(event: ProfileEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

private fun Session.toAccount(): ProfileAccount = when (this) {
    Session.Guest -> ProfileAccount.Guest
    is Session.SignedIn -> ProfileAccount.SignedIn(
        name = user.name,
        email = user.email,
        initials = initialsOf(user.name),
    )
}
