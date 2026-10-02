package com.shafayatb.streamly.app

import com.shafayatb.streamly.navigation.Route

sealed interface AppState {
    /**
     * The stored session has not been read yet. The system splash covers this state; the UI
     * shows a neutral brand surface if it is ever visible.
     */
    data object Loading : AppState

    data class Ready(val startRoute: Route) : AppState
}
