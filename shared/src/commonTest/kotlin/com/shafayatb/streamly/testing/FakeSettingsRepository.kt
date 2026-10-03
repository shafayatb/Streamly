package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.SettingsRepository
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class FakeSettingsRepository(initial: AppSettings? = AppSettings()) : SettingsRepository {

    /** `null` while the settings are still being read. */
    val current = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = current.filterNotNull()

    /** When set, every write fails with this error and changes nothing. */
    var failure: DataError.Local? = null

    override suspend fun setThemeMode(mode: ThemeMode): EmptyResult<DataError.Local> =
        write { it.copy(themeMode = mode) }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean): EmptyResult<DataError.Local> =
        write { it.copy(wifiOnlyDownloads = enabled) }

    override suspend fun setDownloadQuality(quality: DownloadQuality): EmptyResult<DataError.Local> =
        write { it.copy(downloadQuality = quality) }

    private fun write(change: (AppSettings) -> AppSettings): EmptyResult<DataError.Local> {
        failure?.let { return Result.Failure(it) }
        current.value = change(current.value ?: AppSettings())
        return Result.Success(Unit)
    }
}
