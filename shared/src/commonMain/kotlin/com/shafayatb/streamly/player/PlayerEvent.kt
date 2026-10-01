package com.shafayatb.streamly.player

sealed interface PlayerEvent {
    data object NavigateBack : PlayerEvent
}
