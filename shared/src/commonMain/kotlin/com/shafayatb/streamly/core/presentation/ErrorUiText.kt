package com.shafayatb.streamly.core.presentation

import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.player.PlaybackError
import com.shafayatb.streamly.domain.util.DataError
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.download_error_live
import streamly.shared.generated.resources.download_error_network
import streamly.shared.generated.resources.download_error_unknown
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.error_network_not_found
import streamly.shared.generated.resources.error_network_serialization
import streamly.shared.generated.resources.error_network_server
import streamly.shared.generated.resources.error_network_timeout
import streamly.shared.generated.resources.error_network_too_many_requests
import streamly.shared.generated.resources.error_network_unknown
import streamly.shared.generated.resources.error_storage_full
import streamly.shared.generated.resources.error_storage_unknown
import streamly.shared.generated.resources.player_error_format
import streamly.shared.generated.resources.player_error_network
import streamly.shared.generated.resources.player_error_source
import streamly.shared.generated.resources.player_error_unknown

fun DataError.Local.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.Resource(Res.string.error_storage_full)
    DataError.Local.UNKNOWN -> UiText.Resource(Res.string.error_storage_unknown)
}

fun DataError.Network.toUiText(): UiText = UiText.Resource(
    when (this) {
        DataError.Network.REQUEST_TIMEOUT -> Res.string.error_network_timeout
        DataError.Network.TOO_MANY_REQUESTS -> Res.string.error_network_too_many_requests
        DataError.Network.NO_INTERNET -> Res.string.error_network_no_internet
        DataError.Network.NOT_FOUND -> Res.string.error_network_not_found
        DataError.Network.SERVER_ERROR -> Res.string.error_network_server
        DataError.Network.SERIALIZATION -> Res.string.error_network_serialization
        DataError.Network.UNKNOWN -> Res.string.error_network_unknown
    },
)

fun PlaybackError.toUiText(): UiText = UiText.Resource(
    when (this) {
        PlaybackError.NETWORK -> Res.string.player_error_network
        PlaybackError.SOURCE_UNAVAILABLE -> Res.string.player_error_source
        PlaybackError.UNSUPPORTED_FORMAT -> Res.string.player_error_format
        PlaybackError.UNKNOWN -> Res.string.player_error_unknown
    },
)

fun DownloadError.toUiText(): UiText = UiText.Resource(
    when (this) {
        DownloadError.NETWORK -> Res.string.download_error_network
        DownloadError.LIVE_NOT_SUPPORTED -> Res.string.download_error_live
        DownloadError.UNKNOWN -> Res.string.download_error_unknown
    },
)
