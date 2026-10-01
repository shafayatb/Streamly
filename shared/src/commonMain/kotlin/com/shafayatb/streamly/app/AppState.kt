package com.shafayatb.streamly.app

import com.shafayatb.streamly.navigation.Route

sealed interface AppState {
    /** The stored session has not been read yet; the UI shows a neutral brand surface. */
    data object Loading : AppState

    data class Ready(val startRoute: Route) : AppState
}
