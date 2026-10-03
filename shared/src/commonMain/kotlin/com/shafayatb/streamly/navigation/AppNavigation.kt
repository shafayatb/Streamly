package com.shafayatb.streamly.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.shafayatb.streamly.downloads.DownloadsRoot
import com.shafayatb.streamly.history.WatchHistoryRoot
import com.shafayatb.streamly.home.HomeRoot
import com.shafayatb.streamly.onboarding.OnboardingRoot
import com.shafayatb.streamly.onboarding.email.EmailSignInRoot
import com.shafayatb.streamly.player.PlayerRoot
import com.shafayatb.streamly.profile.ProfileRoot
import com.shafayatb.streamly.settings.SettingsRoot
import com.shafayatb.streamly.shorts.ShortsRoot
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Registers every Route so the back stack can be saved and restored across process death.
private val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Onboarding::class)
            subclass(Route.EmailSignIn::class)
            subclass(Route.Home::class)
            subclass(Route.Shorts::class)
            subclass(Route.Downloads::class)
            subclass(Route.Profile::class)
            subclass(Route.Player::class)
            subclass(Route.WatchHistory::class)
            subclass(Route.Settings::class)
        }
    }
}

@Composable
fun AppNavigation(startRoute: Route) {
    val backStack = rememberNavBackStack(navigationConfiguration, startRoute)

    AppShell(
        selected = TopLevelDestination.of(backStack.lastOrNull()),
        onSelect = { backStack.selectTopLevel(it.route) },
    ) { bottomInset ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.popIfNotRoot() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Route.Onboarding> {
                    OnboardingRoot(
                        onNavigateToHome = { backStack.resetTo(Route.Home) },
                        onNavigateToEmailSignIn = { backStack.add(Route.EmailSignIn) },
                    )
                }
                entry<Route.EmailSignIn> {
                    EmailSignInRoot(
                        onNavigateToHome = { backStack.resetTo(Route.Home) },
                        onNavigateBack = { backStack.popIfNotRoot() },
                    )
                }
                entry<Route.Home> {
                    HomeRoot(
                        bottomInset = bottomInset,
                        onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) },
                    )
                }
                entry<Route.Shorts> {
                    ShortsRoot(bottomInset = bottomInset)
                }
                entry<Route.Downloads> {
                    DownloadsRoot(
                        bottomInset = bottomInset,
                        onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) },
                    )
                }
                entry<Route.Profile> {
                    ProfileRoot(
                        bottomInset = bottomInset,
                        onNavigateToDownloads = { backStack.selectTopLevel(Route.Downloads) },
                        onNavigateToHistory = { backStack.add(Route.WatchHistory) },
                        onNavigateToSettings = { backStack.add(Route.Settings) },
                        // Onboarding becomes the only destination: Back exits rather than returning.
                        onNavigateToOnboarding = { backStack.resetTo(Route.Onboarding) },
                    )
                }
                entry<Route.Player> { route ->
                    PlayerRoot(
                        videoId = route.videoId,
                        onNavigateBack = { backStack.popIfNotRoot() },
                        onNavigateToVideo = { videoId -> backStack.replaceTop(Route.Player(videoId)) },
                    )
                }
                entry<Route.Settings> {
                    SettingsRoot(onNavigateBack = { backStack.popIfNotRoot() })
                }
                entry<Route.WatchHistory> {
                    WatchHistoryRoot(
                        onNavigateBack = { backStack.popIfNotRoot() },
                        onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) },
                    )
                }
            },
        )
    }
}
