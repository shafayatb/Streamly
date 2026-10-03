package com.shafayatb.streamly.settings

import app.cash.turbine.test
import com.shafayatb.streamly.app.AppInfo
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.ThemeMode
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.settings_save_failed
import streamly.shared.generated.resources.settings_version_value

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(settings, AppInfo(versionName = "1.0", versionCode = 1))

    @Test
    fun loadsUntilTheSettingsAreRead() {
        settings.current.value = null

        assertNull(viewModel().state.value.settings)
    }

    @Test
    fun showsTheStoredSettingsAndTheVersion() {
        settings.current.value = AppSettings(themeMode = ThemeMode.DARK, wifiOnlyDownloads = true)

        val state = viewModel().state.value

        assertEquals(AppSettings(themeMode = ThemeMode.DARK, wifiOnlyDownloads = true), state.settings)
        assertEquals(UiText.Resource(Res.string.settings_version_value, listOf("1.0", 1L)), state.version)
    }

    @Test
    fun pickingAThemeSavesItAndClosesTheDialog() {
        val viewModel = viewModel()

        viewModel.onIntent(SettingsIntent.OpenThemeDialog)
        assertEquals(SettingsDialog.THEME, viewModel.state.value.dialog)
        viewModel.onIntent(SettingsIntent.SelectTheme(ThemeMode.LIGHT))

        assertNull(viewModel.state.value.dialog)
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.settings?.themeMode)
    }

    @Test
    fun pickingAQualitySavesItAndClosesTheDialog() {
        val viewModel = viewModel()

        viewModel.onIntent(SettingsIntent.OpenQualityDialog)
        assertEquals(SettingsDialog.DOWNLOAD_QUALITY, viewModel.state.value.dialog)
        viewModel.onIntent(SettingsIntent.SelectQuality(DownloadQuality.DATA_SAVER))

        assertNull(viewModel.state.value.dialog)
        assertEquals(DownloadQuality.DATA_SAVER, viewModel.state.value.settings?.downloadQuality)
    }

    @Test
    fun theWifiSwitchSavesAtOnce() {
        val viewModel = viewModel()

        viewModel.onIntent(SettingsIntent.SetWifiOnly(true))

        assertEquals(true, viewModel.state.value.settings?.wifiOnlyDownloads)
    }

    @Test
    fun cancelClosesTheDialogWithoutAChange() {
        val viewModel = viewModel()

        viewModel.onIntent(SettingsIntent.OpenThemeDialog)
        viewModel.onIntent(SettingsIntent.DismissDialog)

        assertNull(viewModel.state.value.dialog)
        assertEquals(AppSettings(), viewModel.state.value.settings)
    }

    @Test
    fun noDialogOpensBeforeTheSettingsAreRead() {
        settings.current.value = null
        val viewModel = viewModel()

        viewModel.onIntent(SettingsIntent.OpenThemeDialog)
        settings.current.value = AppSettings()

        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun aFailedSaveShowsAMessageAndKeepsTheStoredValue() = runTest {
        settings.failure = DataError.Local.DISK_FULL
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(SettingsIntent.OpenThemeDialog)
            viewModel.onIntent(SettingsIntent.SelectTheme(ThemeMode.DARK))
            assertEquals(SettingsEvent.ShowMessage(UiText.Resource(Res.string.settings_save_failed)), awaitItem())
        }
        assertNull(viewModel.state.value.dialog)
        assertEquals(ThemeMode.SYSTEM, viewModel.state.value.settings?.themeMode)
    }

    @Test
    fun backLeaves() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(SettingsIntent.NavigateBack)
            assertEquals(SettingsEvent.NavigateBack, awaitItem())
        }
    }
}
