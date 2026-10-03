package com.shafayatb.streamly.settings

import com.shafayatb.streamly.core.presentation.UiText

sealed interface SettingsEvent {
    data object NavigateBack : SettingsEvent
    data class ShowMessage(val message: UiText) : SettingsEvent
}
