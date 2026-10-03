package com.shafayatb.streamly.core.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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

@Composable
actual fun SystemBarsEffect(darkTheme: Boolean, windowBackground: Color) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    val background = windowBackground.toArgb()
    LaunchedEffect(activity, darkTheme, background) {
        activity.enableEdgeToEdge(
            // Onboarding and the headers are dark brand surfaces in both themes, so status bar icons stay light.
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(DarkScrim)
            } else {
                SystemBarStyle.light(LightScrim, DarkScrim)
            },
        )
        activity.window.setBackgroundDrawable(ColorDrawable(background))
    }
}

// androidx.activity's default scrims, used on three-button navigation before API 29.
private val LightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
