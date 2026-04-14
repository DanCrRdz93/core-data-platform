package com.dancr.platform.security.credential

// Supplies the active credential for request authentication.
// Implementations should read from SessionController or SecretStore — never cache stale tokens.
// TODO: Add suspend fun refresh(): Credential? for proactive token refresh before expiry.
// TODO: Add fun invalidate() to clear cached credential on 401 (called by auth interceptor).
interface CredentialProvider {

    suspend fun current(): Credential?
}
