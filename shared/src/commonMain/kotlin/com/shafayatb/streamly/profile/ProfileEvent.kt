package com.shafayatb.streamly.profile

import com.shafayatb.streamly.core.presentation.UiText

sealed interface ProfileEvent {
    data object NavigateToDownloads : ProfileEvent
    data object NavigateToHistory : ProfileEvent
    data object NavigateToOnboarding : ProfileEvent
    data class ShowMessage(val message: UiText) : ProfileEvent
}
