package com.shafayatb.streamly.core.presentation

import com.shafayatb.streamly.domain.util.DataError
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_storage_full
import streamly.shared.generated.resources.error_storage_unknown

fun DataError.Local.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.Resource(Res.string.error_storage_full)
    DataError.Local.UNKNOWN -> UiText.Resource(Res.string.error_storage_unknown)
}
