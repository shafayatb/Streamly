package com.shafayatb.streamly.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.shafayatb.streamly.core.presentation.findActivity

@Composable
actual fun OrientationLockEffect(lock: OrientationLock) {
    val activity = LocalContext.current.findActivity() ?: return
    // Set, never reset in between: handing the system UNSPECIFIED on the way from one lock to
    // the next could rotate the screen twice.
    LaunchedEffect(activity, lock) {
        activity.requestedOrientation = when (lock) {
            OrientationLock.NONE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}

@Composable
actual fun DeviceOrientationEffect(lock: OrientationLock, onChange: (DeviceOrientation, autoRotate: Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    DisposableEffect(context, lock) {
        if (lock == OrientationLock.NONE) return@DisposableEffect onDispose {}
        var orientation = DeviceOrientation.UNKNOWN
        var autoRotate: Boolean? = null
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                val nextOrientation = deviceOrientationOf(degrees, orientation)
                val nextAutoRotate = isAutoRotateOn(context)
                if (nextOrientation == orientation && nextAutoRotate == autoRotate) return
                orientation = nextOrientation
                autoRotate = nextAutoRotate
                currentOnChange(nextOrientation, nextAutoRotate)
            }
        }
        // Without an accelerometer nothing is reported, and the lock lasts until the user exits.
        listener.enable()
        onDispose { listener.disable() }
    }
}

private fun isAutoRotateOn(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
