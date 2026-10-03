package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.accountKey
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The signed-in account's watch history, kept like its downloads: signing out hides it, signing
 * in again as the same account shows it, and a guest has its own.
 *
 * [record] queues its write in [scope], one at a time in call order, so a save made as a screen
 * closes still lands after that screen's own scope is cancelled. [scope] should live as long as
 * the app.
 */
public class AccountWatchHistory(
    private val store: WatchHistoryStore,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
    scope: CoroutineScope,
) : WatchHistoryRepository {

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) write()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>> =
        sessionRepository.session.flatMapLatest { session ->
            if (session == null) flowOf(Result.Success(emptyList())) else store.entries(session.accountKey())
        }

    override suspend fun resumePosition(videoId: String): Duration =
        when (val result = entries.first()) {
            is Result.Success -> result.data.firstOrNull { it.videoId == videoId }?.resumePosition ?: Duration.ZERO
            is Result.Failure -> Duration.ZERO
        }

    override fun record(video: Video, position: Duration, duration: Duration?) {
        val entry = entryOf(video, position, duration)
        writes.trySend { withAccount { account -> store.upsert(account, entry) } }
    }

    override fun updateProgress(video: Video, position: Duration, duration: Duration?) {
        val entry = entryOf(video, position, duration)
        writes.trySend { withAccount { account -> store.update(account, entry) } }
    }

    override suspend fun remove(videoId: String): EmptyResult<DataError.Local> =
        withAccount { account -> store.remove(account, videoId) }

    override suspend fun clear(): EmptyResult<DataError.Local> = withAccount { account -> store.clear(account) }

    private fun entryOf(video: Video, position: Duration, duration: Duration?) = WatchHistoryEntry(
        videoId = video.id,
        title = video.title,
        channelName = video.channel.name,
        thumbnailUrl = video.thumbnailUrl,
        duration = if (video.isLive) null else duration ?: video.duration,
        position = if (video.isLive) Duration.ZERO else position,
        watchedAt = clock.now(),
    )

    // With nobody signed in there is no history to change, so the write is dropped, never misfiled.
    private suspend fun withAccount(
        write: suspend (account: String) -> EmptyResult<DataError.Local>,
    ): EmptyResult<DataError.Local> {
        val session = sessionRepository.session.first() ?: return Result.Success(Unit)
        return write(session.accountKey())
    }
}
