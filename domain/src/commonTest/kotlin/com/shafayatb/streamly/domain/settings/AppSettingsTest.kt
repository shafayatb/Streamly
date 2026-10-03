package com.shafayatb.streamly.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {

    @Test
    fun theDefaultsKeepTodaysBehaviour() {
        assertEquals(
            AppSettings(themeMode = ThemeMode.SYSTEM, wifiOnlyDownloads = false, downloadQuality = DownloadQuality.STANDARD),
            AppSettings(),
        )
    }

    @Test
    fun downloadPreferencesCarryOnlyTheDownloadSettings() {
        val settings = AppSettings(themeMode = ThemeMode.DARK, wifiOnlyDownloads = true, downloadQuality = DownloadQuality.HIGH)

        assertEquals(DownloadPreferences(wifiOnly = true, quality = DownloadQuality.HIGH), settings.downloadPreferences)
    }
}
