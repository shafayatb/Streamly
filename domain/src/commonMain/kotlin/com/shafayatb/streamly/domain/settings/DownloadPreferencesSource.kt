package com.shafayatb.streamly.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * The download manager's view of the settings. Separate from [SettingsRepository] so the media
 * layer cannot change settings, and a theme change never reaches it.
 */
public interface DownloadPreferencesSource {
    /** Emits only when a download setting changes. */
    public val downloadPreferences: Flow<DownloadPreferences>
}
