package com.shafayatb.streamly.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.navigation.AppNavigation
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    StreamlyTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        when (val current = state) {
            AppState.Loading -> BrandBackground(modifier = Modifier.fillMaxSize())
            is AppState.Ready -> AppNavigation(startRoute = current.startRoute)
        }
    }
}
