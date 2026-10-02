package com.shafayatb.streamly.domain.shorts

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result

/** Read access to the Shorts catalog. Every call is a fresh request; nothing is cached here. */
public interface ShortsRepository {
    /** Every short, in the order the pager shows them. */
    public suspend fun getShorts(): Result<List<ShortVideo>, DataError.Network>
}
