package com.shafayatb.streamly.domain.util

public sealed interface Result<out D, out E : Error> {
    public data class Success<out D>(val data: D) : Result<D, Nothing>
    public data class Failure<out E : Error>(val error: E) : Result<Nothing, E>
}

public typealias EmptyResult<E> = Result<Unit, E>

public inline fun <T, E : Error, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Failure -> this
    }

public inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

public inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}

public fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }
