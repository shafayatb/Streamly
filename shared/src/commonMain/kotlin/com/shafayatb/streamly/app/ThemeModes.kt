package com.shafayatb.streamly.app

import com.shafayatb.streamly.domain.settings.ThemeMode

internal fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
