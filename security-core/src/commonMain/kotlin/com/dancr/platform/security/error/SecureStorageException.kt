package com.dancr.platform.security.error

// Thin wrapper that makes SecurityError.SecureStorageFailure throwable.
// SecretStore implementations need to throw on failure, but SecurityError
// is a sealed value class (not Throwable). This bridges the gap while
// keeping the "errors as values" philosophy intact.
//
// Consumers catch this and extract the typed error:
//     try { store.putString(k, v) }
//     catch (e: SecureStorageException) { handle(e.error) }
class SecureStorageException(
    val error: SecurityError.SecureStorageFailure
) : Exception(error.message, error.diagnostic?.cause)
