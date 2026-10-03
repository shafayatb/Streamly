package com.shafayatb.streamly.player

import androidx.compose.runtime.Composable

/**
 * Asks the system for [lock]'s orientation, each time it changes and again after a rotation
 * recreates the activity. `OrientationReleaseEffect` hands it back once the Player is not on top.
 */
@Composable
expect fun OrientationLockEffect(lock: OrientationLock)

/**
 * While [lock] is held, reports how the phone is held and whether auto-rotate is on, each time
 * either changes and once when the lock changes. Off while no lock is held.
 */
@Composable
expect fun DeviceOrientationEffect(lock: OrientationLock, onChange: (DeviceOrientation, autoRotate: Boolean) -> Unit)
