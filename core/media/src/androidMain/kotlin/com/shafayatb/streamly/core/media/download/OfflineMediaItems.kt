package com.shafayatb.streamly.core.media.download

import androidx.media3.common.MediaItem

/** Lets the long-form player play a finished download's saved rendition instead of streaming. */
internal fun interface OfflineMediaItems {
    /**
     * The media item for [videoId]'s completed download, carrying the stream keys of the
     * rendition that was saved, or `null` when it is not completely downloaded.
     */
    fun completedMediaItem(videoId: String): MediaItem?
}
