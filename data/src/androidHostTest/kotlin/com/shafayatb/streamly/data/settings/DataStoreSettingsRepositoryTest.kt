package com.shafayatb.streamly.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.DownloadPreferences
import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    private val directory: File = createTempDirectory("settings-test").toFile()
    private val storeFile = File(directory, "settings.preferences_pb")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun TestScope.newDataStore(job: Job = Job()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
            produceFile = { storeFile },
        )

    @Test
    fun anEmptyStoreHasTheDefaults() = runTest {
        assertEquals(AppSettings(), DataStoreSettingsRepository(newDataStore()).settings.first())
    }

    @Test
    fun everySettingIsSavedAndSurvivesARestart() = runTest {
        val firstJob = Job()
        DataStoreSettingsRepository(newDataStore(firstJob)).run {
            assertEquals(Result.Success(Unit), setThemeMode(ThemeMode.DARK))
            assertEquals(Result.Success(Unit), setWifiOnlyDownloads(true))
            assertEquals(Result.Success(Unit), setDownloadQuality(DownloadQuality.HIGH))
        }
        firstJob.cancel()

        val restarted = DataStoreSettingsRepository(newDataStore())

        assertEquals(
            AppSettings(themeMode = ThemeMode.DARK, wifiOnlyDownloads = true, downloadQuality = DownloadQuality.HIGH),
            restarted.settings.first(),
        )
    }

    @Test
    fun aValueThisVersionDoesNotKnowReadsAsTheDefault() = runTest {
        val dataStore = newDataStore()
        dataStore.edit {
            it[stringPreferencesKey("theme_mode")] = "SEPIA"
            it[stringPreferencesKey("download_quality")] = "ULTRA"
        }

        assertEquals(AppSettings(), DataStoreSettingsRepository(dataStore).settings.first())
    }

    @Test
    fun aReadErrorGivesTheDefaultTheme() = runTest {
        val repository = DataStoreSettingsRepository(FailingDataStore(IOException("broken")))

        assertEquals(AppSettings(), repository.settings.first())
    }

    @Test
    fun aReadErrorKeepsDownloadsOnWifi() = runTest {
        // Failing safe: a Wi-Fi-only choice that cannot be read must not let downloads use mobile data.
        val repository = DataStoreSettingsRepository(FailingDataStore(IOException("broken")))

        assertEquals(DownloadPreferences(wifiOnly = true, quality = DownloadQuality.STANDARD), repository.downloadPreferences.first())
    }

    @Test
    fun theSettingsRecoverWhenTheStoreCanBeReadAgain() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
        val repository = DataStoreSettingsRepository(FailingOnceDataStore(dataStore))

        repository.settings.test {
            assertEquals(AppSettings(), awaitItem())
            assertEquals(AppSettings(themeMode = ThemeMode.DARK), awaitItem())
        }
    }

    @Test
    fun aFullDiskIsReported() = runTest {
        val repository = DataStoreSettingsRepository(FailingDataStore(IOException("No space left on device")))

        assertEquals(Result.Failure(DataError.Local.DISK_FULL), repository.setThemeMode(ThemeMode.DARK))
    }

    @Test
    fun aThemeChangeDoesNotReachTheDownloadPreferences() = runTest {
        val repository = DataStoreSettingsRepository(newDataStore())

        repository.downloadPreferences.test {
            assertEquals(DownloadPreferences(wifiOnly = false, quality = DownloadQuality.STANDARD), awaitItem())
            repository.setThemeMode(ThemeMode.DARK)
            repository.setWifiOnlyDownloads(true)
            assertEquals(DownloadPreferences(wifiOnly = true, quality = DownloadQuality.STANDARD), awaitItem())
            expectNoEvents()
        }
    }

    /** The first read fails, as an I/O error would; later reads see [store]. */
    private class FailingOnceDataStore(private val store: DataStore<Preferences>) : DataStore<Preferences> {
        private var failed = false
        override val data: Flow<Preferences> = flow {
            if (!failed) {
                failed = true
                throw IOException("busy")
            }
            emitAll(store.data)
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = store.updateData(transform)
    }

    private class FailingDataStore(private val error: IOException) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
    }
}
