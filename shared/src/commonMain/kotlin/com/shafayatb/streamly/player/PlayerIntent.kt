package com.shafayatb.streamly.player

sealed interface PlayerIntent {
    data object Retry : PlayerIntent
    data object NavigateBack : PlayerIntent
}
