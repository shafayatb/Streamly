package com.shafayatb.streamly.domain.util

public sealed interface DataError : Error {
    public enum class Local : DataError {
        DISK_FULL,
        UNKNOWN,
    }
}
