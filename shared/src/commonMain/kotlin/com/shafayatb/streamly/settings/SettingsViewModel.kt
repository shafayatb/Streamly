package com.shafayatb.streamly.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.app.AppInfo
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.settings.SettingsRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.onFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.settings_save_failed
import streamly.shared.generated.resources.settings_version_value

/** Shows only what the store reports, so a failed save never looks saved. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    appInfo: AppInfo,
) : ViewModel() {

    private val version = UiText.Resource(Res.string.settings_version_value, listOf(appInfo.versionName, appInfo.versionCode))
    private val dialog = MutableStateFlow<SettingsDialog?>(null)

    val state: StateFlow<SettingsState> = combine(settingsRepository.settings, dialog) { settings, dialog ->
        SettingsState(settings = settings, dialog = dialog, version = version)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(version = version))

    private val _events = Channel<SettingsEvent>()
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.OpenThemeDialog -> open(SettingsDialog.THEME)
            SettingsIntent.OpenQualityDialog -> open(SettingsDialog.DOWNLOAD_QUALITY)
            is SettingsIntent.SelectTheme -> choose { settingsRepository.setThemeMode(intent.mode) }
            is SettingsIntent.SelectQuality -> choose { settingsRepository.setDownloadQuality(intent.quality) }
            is SettingsIntent.SetWifiOnly -> save { settingsRepository.setWifiOnlyDownloads(intent.enabled) }
            SettingsIntent.DismissDialog -> dialog.value = null
            SettingsIntent.NavigateBack -> viewModelScope.launch { _events.send(SettingsEvent.NavigateBack) }
        }
    }

    // A choice dialog needs the current choice to mark, so nothing opens before the read.
    private fun open(which: SettingsDialog) {
        if (state.value.settings != null) dialog.value = which
    }

    private fun choose(write: suspend () -> EmptyResult<DataError.Local>) {
        dialog.value = null
        save(write)
    }

    private fun save(write: suspend () -> EmptyResult<DataError.Local>) {
        viewModelScope.launch {
            write().onFailure { _events.send(SettingsEvent.ShowMessage(UiText.Resource(Res.string.settings_save_failed))) }
        }
    }
}
