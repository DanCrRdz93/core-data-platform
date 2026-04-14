package com.dancr.platform.network.result

sealed class NetworkResult<out T> {

    data class Success<T>(
        val data: T,
        val metadata: ResponseMetadata = ResponseMetadata.EMPTY
    ) : NetworkResult<T>()

    data class Failure(
        val error: NetworkError
    ) : NetworkResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): NetworkError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): NetworkResult<R> = when (this) {
        is Success -> Success(transform(data), metadata)
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> NetworkResult<R>): NetworkResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (NetworkError) -> Unit): NetworkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (NetworkError) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
}
