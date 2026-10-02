package com.shafayatb.streamly.navigation

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.ic_download
import streamly.shared.generated.resources.ic_home
import streamly.shared.generated.resources.ic_person
import streamly.shared.generated.resources.ic_shorts
import streamly.shared.generated.resources.nav_downloads
import streamly.shared.generated.resources.nav_home
import streamly.shared.generated.resources.nav_profile
import streamly.shared.generated.resources.nav_shorts

/** The tabs of the app shell. Home is the root of the back stack; every other tab sits on top of it. */
enum class TopLevelDestination(
    val route: Route,
    val label: StringResource,
    val icon: DrawableResource,
) {
    HOME(Route.Home, Res.string.nav_home, Res.drawable.ic_home),
    SHORTS(Route.Shorts, Res.string.nav_shorts, Res.drawable.ic_shorts),
    DOWNLOADS(Route.Downloads, Res.string.nav_downloads, Res.drawable.ic_download),
    PROFILE(Route.Profile, Res.string.nav_profile, Res.drawable.ic_person),
    ;

    companion object {
        /** The tab showing [route], or `null` when [route] is not a tab, such as the player. */
        fun of(route: Any?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}
