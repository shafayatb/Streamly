package com.shafayatb.streamly.data.history

import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class WatchHistoryEntryDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** `null` for live streams. */
    val durationMs: Long? = null,
    val positionMs: Long = 0,
    val watchedAtMs: Long,
)

internal fun WatchHistoryEntryDto.toEntry(): WatchHistoryEntry = WatchHistoryEntry(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    duration = durationMs?.milliseconds,
    position = positionMs.milliseconds,
    watchedAt = Instant.fromEpochMilliseconds(watchedAtMs),
)

internal fun WatchHistoryEntry.toDto(): WatchHistoryEntryDto = WatchHistoryEntryDto(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = duration?.inWholeMilliseconds,
    positionMs = position.inWholeMilliseconds,
    watchedAtMs = watchedAt.toEpochMilliseconds(),
)
