package com.shafayatb.streamly.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.BrandTopBar
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.resolve
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.ThemeMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.settings_quality
import streamly.shared.generated.resources.settings_quality_note
import streamly.shared.generated.resources.settings_section_about
import streamly.shared.generated.resources.settings_section_appearance
import streamly.shared.generated.resources.settings_section_downloads
import streamly.shared.generated.resources.settings_theme
import streamly.shared.generated.resources.settings_title
import streamly.shared.generated.resources.settings_version
import streamly.shared.generated.resources.settings_wifi_only
import streamly.shared.generated.resources.settings_wifi_only_detail

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.NavigateBack -> onNavigateBack()
            is SettingsEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    SettingsScreen(state = state, onIntent = viewModel::onIntent, snackbarHostState = snackbarHostState)
}

/** A pushed destination with no tab bar, so it keeps the system navigation bar clear itself. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val settings = state.settings
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrandTopBar(
                title = stringResource(Res.string.settings_title),
                onBack = { onIntent(SettingsIntent.NavigateBack) },
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SectionHeader(stringResource(Res.string.settings_section_appearance))
                    ChoiceRow(
                        title = stringResource(Res.string.settings_theme),
                        value = settings?.let { stringResource(it.themeMode.label) },
                        onClick = { onIntent(SettingsIntent.OpenThemeDialog) },
                    )
                    HorizontalDivider()
                    SectionHeader(stringResource(Res.string.settings_section_downloads))
                    SwitchRow(
                        title = stringResource(Res.string.settings_wifi_only),
                        detail = stringResource(Res.string.settings_wifi_only_detail),
                        checked = settings?.wifiOnlyDownloads == true,
                        enabled = settings != null,
                        onCheckedChange = { onIntent(SettingsIntent.SetWifiOnly(it)) },
                    )
                    ChoiceRow(
                        title = stringResource(Res.string.settings_quality),
                        value = settings?.let { stringResource(it.downloadQuality.label) },
                        onClick = { onIntent(SettingsIntent.OpenQualityDialog) },
                    )
                    Text(
                        text = stringResource(Res.string.settings_quality_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    SectionHeader(stringResource(Res.string.settings_section_about))
                    InfoRow(title = stringResource(Res.string.settings_version), value = state.version.asString())
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (settings != null) {
        when (state.dialog) {
            SettingsDialog.THEME -> SettingsChoiceDialog(
                title = stringResource(Res.string.settings_theme),
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = { it.label },
                onSelect = { onIntent(SettingsIntent.SelectTheme(it)) },
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            )
            SettingsDialog.DOWNLOAD_QUALITY -> SettingsChoiceDialog(
                title = stringResource(Res.string.settings_quality),
                options = DownloadQuality.entries,
                selected = settings.downloadQuality,
                label = { it.label },
                onSelect = { onIntent(SettingsIntent.SelectQuality(it)) },
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
            )
            null -> Unit
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** [value] is `null` while the settings load, which also disables the row. */
@Composable
private fun ChoiceRow(title: String, value: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = value != null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = value.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The whole row is the switch, so TalkBack reads one control with its title and state. */
@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PreviewState = SettingsState(settings = AppSettings(), version = UiText.DynamicString("1.0 (1)"))

@Preview
@Composable
private fun SettingsScreenPreview() {
    StreamlyTheme(darkTheme = false) { SettingsScreen(state = PreviewState, onIntent = {}) }
}

@Preview
@Composable
private fun SettingsScreenDarkPreview() {
    StreamlyTheme(darkTheme = true) { SettingsScreen(state = PreviewState, onIntent = {}) }
}

@Preview
@Composable
private fun SettingsThemeDialogPreview() {
    StreamlyTheme(darkTheme = false) {
        SettingsScreen(state = PreviewState.copy(dialog = SettingsDialog.THEME), onIntent = {})
    }
}
