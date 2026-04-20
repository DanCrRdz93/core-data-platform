package com.dancr.platform.security.error

/**
 * Thin wrapper that makes [SecurityError.SecureStorageFailure] throwable.
 *
 * [SecretStore][com.dancr.platform.security.store.SecretStore] implementations
 * need to throw on failure, but [SecurityError] is a sealed class (not [Throwable]).
 * This bridges the gap while keeping the "errors as values" philosophy intact.
 *
 * **Example — catching in consumer code:**
 * ```kotlin
 * try {
 *     store.putString("session_token", token)
 * } catch (e: SecureStorageException) {
 *     // Extract the typed error for structured handling
 *     val error: SecurityError.SecureStorageFailure = e.error
 *     logger.error("Storage failed: ${error.diagnostic?.description}")
 * }
 * ```
 *
 * @property error The typed [SecurityError.SecureStorageFailure] that caused this exception.
 */
class SecureStorageException(
    val error: SecurityError.SecureStorageFailure
) : Exception(error.message, error.diagnostic?.cause)
