package com.shafayatb.streamly.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.data.local.tryEdit
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.DownloadPreferences
import com.shafayatb.streamly.domain.settings.DownloadPreferencesSource
import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.SettingsRepository
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/** Device-wide settings. Enums are stored by name; a name this version does not know reads as the default. */
internal class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository, DownloadPreferencesSource {

    private val stored: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    // A store that cannot be read gives the defaults, because the app must still start with a
    // theme, and keeps trying, so the stored settings and later changes still arrive.
    override val settings: Flow<AppSettings> = stored
        .retryWhen { cause, _ -> retryAfter(cause, fallback = AppSettings()) }
        .distinctUntilChanged()

    // Fails safe: a Wi-Fi-only choice that cannot be read must not let downloads use mobile data.
    override val downloadPreferences: Flow<DownloadPreferences> = stored
        .map { it.downloadPreferences }
        .retryWhen { cause, _ -> retryAfter(cause, fallback = AppSettings().downloadPreferences.copy(wifiOnly = true)) }
        .distinctUntilChanged()

    private suspend fun <T> FlowCollector<T>.retryAfter(cause: Throwable, fallback: T): Boolean {
        if (cause !is Exception) return false
        emit(fallback)
        delay(READ_RETRY_DELAY)
        return true
    }

    override suspend fun setThemeMode(mode: ThemeMode): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it[Keys.themeMode] = mode.name }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it[Keys.wifiOnlyDownloads] = enabled }

    override suspend fun setDownloadQuality(quality: DownloadQuality): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it[Keys.downloadQuality] = quality.name }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[Keys.themeMode] } ?: defaults.themeMode,
            wifiOnlyDownloads = this[Keys.wifiOnlyDownloads] ?: defaults.wifiOnlyDownloads,
            downloadQuality = DownloadQuality.entries.firstOrNull { it.name == this[Keys.downloadQuality] }
                ?: defaults.downloadQuality,
        )
    }

    private companion object {
        val READ_RETRY_DELAY = 5.seconds
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val wifiOnlyDownloads = booleanPreferencesKey("wifi_only_downloads")
        val downloadQuality = stringPreferencesKey("download_quality")
    }
}
