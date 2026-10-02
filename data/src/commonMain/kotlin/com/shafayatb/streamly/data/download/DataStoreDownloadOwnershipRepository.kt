package com.shafayatb.streamly.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shafayatb.streamly.data.local.tryEdit
import com.shafayatb.streamly.domain.download.DownloadOwnershipRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One string-set entry per downloaded video: the account keys that saved it. */
internal class DataStoreDownloadOwnershipRepository(
    private val dataStore: DataStore<Preferences>,
) : DownloadOwnershipRepository {

    override val owners: Flow<Result<Map<String, Set<String>>, DataError.Local>> = dataStore.data
        .map<Preferences, Result<Map<String, Set<String>>, DataError.Local>> { preferences ->
            Result.Success(
                preferences.asMap().keys
                    .filter { it.name.startsWith(PREFIX) }
                    .associate { it.name.removePrefix(PREFIX) to preferences[stringSetPreferencesKey(it.name)].orEmpty() },
            )
        }
        .catch { emit(Result.Failure(DataError.Local.UNKNOWN)) }
        .distinctUntilChanged()

    override suspend fun addOwner(videoId: String, account: String): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it[keyFor(videoId)] = it[keyFor(videoId)].orEmpty() + account }

    override suspend fun removeOwner(videoId: String, account: String): Result<Set<String>, DataError.Local> {
        var remaining = emptySet<String>()
        return dataStore.tryEdit { preferences ->
            remaining = preferences[keyFor(videoId)].orEmpty() - account
            if (remaining.isEmpty()) preferences.remove(keyFor(videoId)) else preferences[keyFor(videoId)] = remaining
        }.map { remaining }
    }

    private fun keyFor(videoId: String) = stringSetPreferencesKey(PREFIX + videoId)

    private companion object {
        const val PREFIX = "owners/"
    }
}
