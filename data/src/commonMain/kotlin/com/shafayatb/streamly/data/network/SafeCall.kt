package com.shafayatb.streamly.data.network

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.ContentConvertException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs [execute], decodes a successful body as [T], and maps it with [transform]. Every failure,
 * including a body or mapping that does not match the contract, becomes a [DataError.Network], so
 * no exception other than cancellation leaves the data layer.
 */
internal suspend inline fun <reified T, R> safeCall(
    execute: () -> HttpResponse,
    transform: (T) -> R,
): Result<R, DataError.Network> {
    val response = try {
        execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return Result.Failure(e.toNetworkError())
    }
    return response.toResult(transform)
}

@PublishedApi
internal suspend inline fun <reified T, R> HttpResponse.toResult(
    transform: (T) -> R,
): Result<R, DataError.Network> = when (status.value) {
    in 200..299 -> try {
        Result.Success(transform(body<T>()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Failure(e.toNetworkError())
    }
    404 -> Result.Failure(DataError.Network.NOT_FOUND)
    408 -> Result.Failure(DataError.Network.REQUEST_TIMEOUT)
    429 -> Result.Failure(DataError.Network.TOO_MANY_REQUESTS)
    in 500..599 -> Result.Failure(DataError.Network.SERVER_ERROR)
    else -> Result.Failure(DataError.Network.UNKNOWN)
}

@PublishedApi
internal fun Exception.toNetworkError(): DataError.Network = when (this) {
    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException -> DataError.Network.REQUEST_TIMEOUT
    // Timeouts are IOExceptions too, so they must be matched first.
    is IOException -> DataError.Network.NO_INTERNET
    is SerializationException,
    is ContentConvertException,
    // Mappers reject values the contract does not allow, such as an unparseable timestamp.
    is IllegalArgumentException -> DataError.Network.SERIALIZATION
    else -> DataError.Network.UNKNOWN
}
