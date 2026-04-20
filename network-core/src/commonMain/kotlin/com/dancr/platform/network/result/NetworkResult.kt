package com.dancr.platform.network.result

/**
 * Result of a network operation — either [Success] with typed data and metadata,
 * or [Failure] with a typed [NetworkError].
 *
 * Inspired by Kotlin's `Result` but specialized for networking with
 * [ResponseMetadata] on success and [NetworkError] on failure.
 *
 * **Example — consuming a result:**
 * ```kotlin
 * val result: NetworkResult<User> = executor.execute(request) { /* deserialize */ }
 *
 * // Pattern-matching
 * when (result) {
 *     is NetworkResult.Success -> showUser(result.data)
 *     is NetworkResult.Failure -> handleError(result.error)
 * }
 *
 * // Functional style
 * result
 *     .map { user -> user.displayName }
 *     .onSuccess { name -> showName(name) }
 *     .onFailure { error -> showError(error.message) }
 *
 * // Fold
 * val message = result.fold(
 *     onSuccess = { "Hello, ${it.displayName}" },
 *     onFailure = { it.message }
 * )
 * ```
 *
 * @param T The type of the successful response data.
 * @see NetworkError for the failure type.
 * @see ResponseMetadata for response metadata on success.
 */
sealed class NetworkResult<out T> {

    /**
     * Successful result containing deserialized [data] and [metadata].
     *
     * @property data     The deserialized response payload.
     * @property metadata Response metadata (status code, headers, duration, attempt count).
     */
    data class Success<T>(
        val data: T,
        val metadata: ResponseMetadata = ResponseMetadata.EMPTY
    ) : NetworkResult<T>()

    /**
     * Failed result containing a typed [error].
     *
     * @property error The classified network error.
     */
    data class Failure(
        val error: NetworkError
    ) : NetworkResult<Nothing>()

    /** `true` if this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** `true` if this is a [Failure]. */
    val isFailure: Boolean get() = this is Failure

    /** Returns the data if [Success], or `null` if [Failure]. */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    /** Returns the error if [Failure], or `null` if [Success]. */
    fun errorOrNull(): NetworkError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    /** Transforms the data on [Success], passes [Failure] through unchanged. */
    inline fun <R> map(transform: (T) -> R): NetworkResult<R> = when (this) {
        is Success -> Success(transform(data), metadata)
        is Failure -> this
    }

    /** Transforms the data into another [NetworkResult], passes [Failure] through unchanged. */
    inline fun <R> flatMap(transform: (T) -> NetworkResult<R>): NetworkResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    /** Executes [action] if this is a [Success]. Returns `this` for chaining. */
    inline fun onSuccess(action: (T) -> Unit): NetworkResult<T> {
        if (this is Success) action(data)
        return this
    }

    /** Executes [action] if this is a [Failure]. Returns `this` for chaining. */
    inline fun onFailure(action: (NetworkError) -> Unit): NetworkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /** Folds the result into a single value by applying [onSuccess] or [onFailure]. */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (NetworkError) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }
}
