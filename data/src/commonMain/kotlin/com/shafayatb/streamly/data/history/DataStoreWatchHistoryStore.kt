package com.shafayatb.streamly.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.data.local.tryEdit
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryStore
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One JSON list per account, newest first, so one write updates an account's whole history atomically. */
internal class DataStoreWatchHistoryStore(
    private val dataStore: DataStore<Preferences>,
) : WatchHistoryStore {

    override fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>> = dataStore.data
        .map<Preferences, Result<List<WatchHistoryEntry>, DataError.Local>> { preferences ->
            Result.Success(decode(preferences[keyFor(account)]).map { it.toEntry() })
        }
        .catch { emit(Result.Failure(DataError.Local.UNKNOWN)) }
        .distinctUntilChanged()

    override suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> =
        dataStore.tryEdit { preferences -> putFirst(preferences, account, entry, insertIfMissing = true) }

    // Checked inside the same edit, so a removal can never slip in between the check and the write.
    override suspend fun update(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> =
        dataStore.tryEdit { preferences -> putFirst(preferences, account, entry, insertIfMissing = false) }

    override suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local> =
        dataStore.tryEdit { preferences ->
            val remaining = decode(preferences[keyFor(account)]).filter { it.videoId != videoId }
            if (remaining.isEmpty()) preferences.remove(keyFor(account)) else preferences[keyFor(account)] = encode(remaining)
        }

    override suspend fun clear(account: String): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it.remove(keyFor(account)) }

    private fun putFirst(preferences: MutablePreferences, account: String, entry: WatchHistoryEntry, insertIfMissing: Boolean) {
        val current = decode(preferences[keyFor(account)])
        val others = current.filter { it.videoId != entry.videoId }
        if (!insertIfMissing && others.size == current.size) return
        preferences[keyFor(account)] = encode((listOf(entry.toDto()) + others).take(MAX_ENTRIES))
    }

    // A value that no longer decodes starts the account's history afresh rather than failing every read.
    private fun decode(value: String?): List<WatchHistoryEntryDto> =
        if (value == null) {
            emptyList()
        } else {
            try {
                json.decodeFromString(listSerializer, value)
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
        }

    private fun encode(entries: List<WatchHistoryEntryDto>): String = json.encodeToString(listSerializer, entries)

    private fun keyFor(account: String) = stringPreferencesKey(PREFIX + account)

    private companion object {
        const val PREFIX = "history/"
        const val MAX_ENTRIES = 100
        val json = Json { ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(WatchHistoryEntryDto.serializer())
    }
}
