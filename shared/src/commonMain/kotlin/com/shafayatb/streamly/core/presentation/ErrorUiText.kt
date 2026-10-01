package com.shafayatb.streamly.core.presentation

import com.shafayatb.streamly.domain.util.DataError
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.error_network_not_found
import streamly.shared.generated.resources.error_network_serialization
import streamly.shared.generated.resources.error_network_server
import streamly.shared.generated.resources.error_network_timeout
import streamly.shared.generated.resources.error_network_too_many_requests
import streamly.shared.generated.resources.error_network_unknown
import streamly.shared.generated.resources.error_storage_full
import streamly.shared.generated.resources.error_storage_unknown

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
