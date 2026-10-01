package com.shafayatb.streamly.app

import androidx.compose.foundation.layout.fillMaxSize
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
    StreamlyTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        when (val current = state) {
            AppState.Loading -> BrandBackground(modifier = Modifier.fillMaxSize())
            is AppState.Ready -> AppNavigation(startRoute = current.startRoute)
        }
    }
}
