package com.shafayatb.streamly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.settings.SettingsRepository
import com.shafayatb.streamly.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Chooses the start destination from the first stored session value only, then follows the theme
 * setting. Later navigation is driven by screen events, and a restored back stack takes precedence
 * over this start route. `Ready` waits for both reads, so the splash covers a stored dark theme.
 */
class AppViewModel(
    sessionRepository: SessionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = flow {
        val session = sessionRepository.session.first()
        val startRoute = if (session == null) Route.Onboarding else Route.Home
        emitAll(settingsRepository.settings.map { AppState.Ready(startRoute, it.themeMode) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)
}
