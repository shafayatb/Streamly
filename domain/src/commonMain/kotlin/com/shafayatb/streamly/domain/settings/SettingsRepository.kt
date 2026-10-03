package com.shafayatb.streamly.domain.settings

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

public interface SettingsRepository {
    /** Never fails: a store that cannot be read gives the defaults, so the app always has a theme. */
    public val settings: Flow<AppSettings>

    public suspend fun setThemeMode(mode: ThemeMode): EmptyResult<DataError.Local>
    public suspend fun setWifiOnlyDownloads(enabled: Boolean): EmptyResult<DataError.Local>
    public suspend fun setDownloadQuality(quality: DownloadQuality): EmptyResult<DataError.Local>
}
