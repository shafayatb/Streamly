package com.shafayatb.streamly.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.SystemBarsEffect
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.navigation.AppNavigation
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    // Thumbnails load over Ktor (OkHttp engine), with Coil's default memory and disk caches.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Until the settings are read the splash is still up; follow the system meanwhile.
    val themeMode = (state as? AppState.Ready)?.themeMode ?: ThemeMode.SYSTEM
    val darkTheme = themeMode.isDark(systemDark = isSystemInDarkTheme())
    StreamlyTheme(darkTheme = darkTheme) {
        SystemBarsEffect(darkTheme = darkTheme, windowBackground = MaterialTheme.colorScheme.background)
        when (val current = state) {
            AppState.Loading -> BrandBackground(modifier = Modifier.fillMaxSize())
            // Same position in the composition for every Ready, so a theme change keeps the back stack.
            is AppState.Ready -> AppNavigation(startRoute = current.startRoute)
        }
    }
}
