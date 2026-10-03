package com.shafayatb.streamly.settings

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.settings.AppSettings

@Immutable
data class SettingsState(
    /** `null` until the stored settings are read; the rows are disabled meanwhile. */
    val settings: AppSettings? = null,
    /** The choice dialog that is open. In state, so it survives rotation. */
    val dialog: SettingsDialog? = null,
    /** "1.0 (1)". */
    val version: UiText = UiText.DynamicString(""),
)

enum class SettingsDialog { THEME, DOWNLOAD_QUALITY }
