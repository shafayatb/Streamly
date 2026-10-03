package com.shafayatb.streamly

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shafayatb.streamly.app.App
import com.shafayatb.streamly.app.AppState
import com.shafayatb.streamly.app.AppViewModel
import com.shafayatb.streamly.core.media.download.startDownloadService
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Kept until the stored session and settings are read, so the first screen is the right one, in the right theme.
        installSplashScreen().setKeepOnScreenCondition { appViewModel.state.value == AppState.Loading }
        // Onboarding and the home header are dark brand surfaces, so status bar icons stay light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        startDownloadService(this)

        setContent {
            App(viewModel = appViewModel)
        }
    }
}
