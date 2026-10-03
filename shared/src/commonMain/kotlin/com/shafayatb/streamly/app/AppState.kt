package com.shafayatb.streamly.app

import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.navigation.Route

sealed interface AppState {
    /**
     * The stored session or settings have not been read yet. The system splash covers this state;
     * the UI shows a neutral brand surface if it is ever visible.
     */
    data object Loading : AppState

    /** [themeMode] follows the settings; [startRoute] is fixed by the first session read. */
    data class Ready(val startRoute: Route, val themeMode: ThemeMode) : AppState
}
