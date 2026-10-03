package com.shafayatb.streamly.settings

import com.shafayatb.streamly.domain.settings.DownloadQuality
import com.shafayatb.streamly.domain.settings.ThemeMode
import org.jetbrains.compose.resources.StringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.settings_quality_data_saver
import streamly.shared.generated.resources.settings_quality_high
import streamly.shared.generated.resources.settings_quality_standard
import streamly.shared.generated.resources.settings_theme_dark
import streamly.shared.generated.resources.settings_theme_light
import streamly.shared.generated.resources.settings_theme_system

internal val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.settings_theme_system
        ThemeMode.LIGHT -> Res.string.settings_theme_light
        ThemeMode.DARK -> Res.string.settings_theme_dark
    }

internal val DownloadQuality.label: StringResource
    get() = when (this) {
        DownloadQuality.DATA_SAVER -> Res.string.settings_quality_data_saver
        DownloadQuality.STANDARD -> Res.string.settings_quality_standard
        DownloadQuality.HIGH -> Res.string.settings_quality_high
    }
