package com.dancr.platform.security.credential

// Supplies the active credential for request authentication.
// Implementations should read from SessionController or SecretStore — never cache stale tokens.
//
// Contract:
//   current()    — Returns the active credential, or null if no session is active.
//                  Called by the auth interceptor before every request.
//                  Must be fast — reads from in-memory state, never performs I/O.
//
//   refresh()    — Proactively refreshes the credential (e.g. before token expiry).
//                  Returns the new credential on success, null if refresh is not possible.
//                  Default: delegates to current() (no proactive refresh).
//
//   invalidate() — Clears the current credential and forces re-authentication.
//                  Called by the auth interceptor on 401 or by security policies.
//                  Default: no-op. Override to delegate to SessionController.invalidate().
interface CredentialProvider {

    suspend fun current(): Credential?

    suspend fun refresh(): Credential? = current()

    suspend fun invalidate() {}
}
