package com.shafayatb.streamly.domain.video

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result

/** Read access to the video catalog. Every call is a fresh request; nothing is cached here. */
public interface VideoRepository {
    /** Every long-form video in the home feed, newest first. */
    public suspend fun getFeed(): Result<List<Video>, DataError.Network>

    /** Fails with [DataError.Network.NOT_FOUND] when the catalog has no video with [id]. */
    public suspend fun getVideo(id: String): Result<Video, DataError.Network>

    /** Videos to play after [id], best match first, never including [id] itself. */
    public suspend fun getUpNext(id: String): Result<List<Video>, DataError.Network>
}
