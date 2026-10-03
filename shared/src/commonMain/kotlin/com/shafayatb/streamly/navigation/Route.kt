package com.shafayatb.streamly.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Onboarding : Route

    @Serializable
    data object EmailSignIn : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object Shorts : Route

    @Serializable
    data object Downloads : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data class Player(val videoId: String) : Route

    @Serializable
    data object WatchHistory : Route

    @Serializable
    data object Settings : Route
}
