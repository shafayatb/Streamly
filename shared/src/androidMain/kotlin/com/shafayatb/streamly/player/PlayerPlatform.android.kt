package com.shafayatb.streamly.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shafayatb.streamly.core.media.player.ExoVideoPlayer
import org.koin.compose.koinInject
import com.shafayatb.streamly.core.media.player.VideoSurface as MediaVideoSurface

@Composable
actual fun VideoSurface(videoId: String, modifier: Modifier) {
    // Previews have no Koin graph and no player; the black viewport stands in for the video.
    if (LocalInspectionMode.current) return
    MediaVideoSurface(player = koinInject<ExoVideoPlayer>(), videoId = videoId, modifier = modifier)
}

@Composable
actual fun rememberIsChangingConfigurations(): () -> Boolean {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { { activity?.isChangingConfigurations == true } }
}

@Composable
actual fun ImmersiveModeEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
