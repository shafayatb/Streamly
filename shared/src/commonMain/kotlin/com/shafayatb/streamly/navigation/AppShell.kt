package com.shafayatb.streamly.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The tab chrome around the back stack: a bottom bar on phones and a navigation rail on expanded
 * widths, shown only while a tab is on top. Destinations opened over a tab, like the player,
 * take the whole window.
 *
 * [content] stays at one place in the composition whether or not the chrome shows, so the back
 * stack's state survives the chrome coming and going. It receives the bottom system bar space it
 * must keep clear itself: none above a bottom bar, which already covers it.
 */
@Composable
fun AppShell(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable (bottomInset: Dp) -> Unit,
) {
    val useRail = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val showBar = selected != null && !useRail
    val showRail = selected != null && useRail
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(modifier = Modifier.fillMaxSize()) {
        if (selected != null && showRail) {
            ShellChromeTheme(selected) {
                NavigationRail {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The rail already keeps clear of the start-side cutout and system bar.
                    .then(
                        if (showRail) {
                            Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content(if (showBar) 0.dp else navigationBarInset)
            }
            if (selected != null && showBar) {
                ShellChromeTheme(selected) {
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == selected,
                                onClick = { onSelect(destination) },
                                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shorts is always dark, so the chrome around it is too, rather than a light bar under black video. */
@Composable
private fun ShellChromeTheme(selected: TopLevelDestination, content: @Composable () -> Unit) {
    if (selected == TopLevelDestination.SHORTS) StreamlyTheme(darkTheme = true, content = content) else content()
}
