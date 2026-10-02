package com.shafayatb.streamly.shorts

import com.shafayatb.streamly.core.presentation.UiText

sealed interface ShortsEvent {
    data class ShowMessage(val message: UiText) : ShortsEvent
}
