package com.shafayatb.streamly.app

import com.shafayatb.streamly.domain.settings.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModesTest {

    @Test
    fun systemFollowsTheDeviceAndTheOthersAreFixed() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
    }
}
