package com.shafayatb.streamly.settings

import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.ThemeMode

sealed interface SettingsIntent {
    data object OpenThemeDialog : SettingsIntent
    data object OpenQualityDialog : SettingsIntent
    data class SelectTheme(val mode: ThemeMode) : SettingsIntent
    data class SelectQuality(val quality: DownloadQuality) : SettingsIntent
    data class SetWifiOnly(val enabled: Boolean) : SettingsIntent
    data object DismissDialog : SettingsIntent
    data object NavigateBack : SettingsIntent
}
