package com.shafayatb.streamly.domain.settings

public enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The largest rendition a new download saves. The pixel caps belong to the media layer. */
public enum class DownloadQuality { DATA_SAVER, STANDARD, HIGH }

/**
 * Device-wide: the same for every account, and kept across sign-out. The defaults are the app's
 * behaviour before Settings existed.
 */
public data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wifiOnlyDownloads: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.STANDARD,
) {
    val downloadPreferences: DownloadPreferences
        get() = DownloadPreferences(wifiOnly = wifiOnlyDownloads, quality = downloadQuality)
}

public data class DownloadPreferences(
    val wifiOnly: Boolean,
    val quality: DownloadQuality,
)
