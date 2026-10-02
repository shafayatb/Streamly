package com.shafayatb.streamly.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.shafayatb.streamly.home.HomeRoot
import com.shafayatb.streamly.onboarding.OnboardingRoot
import com.shafayatb.streamly.onboarding.email.EmailSignInRoot
import com.shafayatb.streamly.player.PlayerRoot
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
            subclass(Route.Player::class)
        }
    }
}

@Composable
fun AppNavigation(startRoute: Route) {
    val backStack = rememberNavBackStack(navigationConfiguration, startRoute)

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
                HomeRoot(onNavigateToPlayer = { videoId -> backStack.add(Route.Player(videoId)) })
            }
            entry<Route.Player> { route ->
                PlayerRoot(
                    videoId = route.videoId,
                    onNavigateBack = { backStack.popIfNotRoot() },
                    onNavigateToVideo = { videoId -> backStack.replaceTop(Route.Player(videoId)) },
                )
            }
        },
    )
}
