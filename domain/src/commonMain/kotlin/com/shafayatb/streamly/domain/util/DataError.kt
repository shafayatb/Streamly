package com.shafayatb.streamly.domain.util

public sealed interface DataError : Error {
    public enum class Network : DataError {
        REQUEST_TIMEOUT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        NOT_FOUND,
        SERVER_ERROR,
        SERIALIZATION,
        UNKNOWN,
    }

    public enum class Local : DataError {
        DISK_FULL,
        UNKNOWN,
    }
}
