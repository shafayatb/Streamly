package com.shafayatb.streamly.domain.download

/** Lets the player decide whether a saved copy is the signed-in account's to play. */
public fun interface DownloadAccess {
    public fun canPlayOffline(videoId: String): Boolean
}
