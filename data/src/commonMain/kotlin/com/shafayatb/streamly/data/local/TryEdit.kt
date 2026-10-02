package com.shafayatb.streamly.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlin.coroutines.cancellation.CancellationException

/** Writes to the store, turning a failure into a typed error instead of an exception. */
internal suspend fun DataStore<Preferences>.tryEdit(transform: (MutablePreferences) -> Unit): EmptyResult<DataError.Local> =
    try {
        edit(transform)
        Result.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Failure(e.toLocalError())
    }

private fun Exception.toLocalError(): DataError.Local =
    if (message?.contains("No space left", ignoreCase = true) == true) {
        DataError.Local.DISK_FULL
    } else {
        DataError.Local.UNKNOWN
    }
