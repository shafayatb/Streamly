package com.shafayatb.streamly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Chooses the start destination from the first stored session value only. Later navigation is
 * driven by screen events, and a restored back stack takes precedence over this start route.
 */
class AppViewModel(
    sessionRepository: SessionRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = flow {
        val session = sessionRepository.session.first()
        emit(AppState.Ready(startRoute = if (session == null) Route.Onboarding else Route.Home))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)
}
