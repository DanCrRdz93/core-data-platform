package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.error.Diagnostic
import com.dancr.platform.security.error.SecurityError
import com.dancr.platform.security.store.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultSessionController(
    private val store: SecretStore,
    private val refreshTokenProvider: (suspend (refreshToken: String) -> SessionCredentials?)? = null,
    private val clock: () -> Long = { currentTimeMillis() }
) : SessionController {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    override val events: Flow<SessionEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    override suspend fun startSession(credentials: SessionCredentials): Unit = mutex.withLock {
        persistCredentials(credentials)
        _state.value = SessionState.Active(credentials.credential)
        _events.emit(SessionEvent.Started)
    }

    override suspend fun refreshSession(): RefreshOutcome = mutex.withLock {
        val refreshToken = store.getString(KEY_REFRESH_TOKEN)
        val provider = refreshTokenProvider

        if (refreshToken == null || provider == null) {
            val reason = when {
                refreshToken == null -> "No refresh token available"
                else -> "No refresh token provider configured"
            }
            // State unchanged — NotNeeded means "preconditions not met, nothing happened".
            return RefreshOutcome.NotNeeded(reason)
        }

        return try {
            val newCredentials = provider(refreshToken)
            if (newCredentials != null) {
                persistCredentials(newCredentials)
                _state.value = SessionState.Active(newCredentials.credential)
                _events.emit(SessionEvent.Refreshed)
                RefreshOutcome.Refreshed(newCredentials.credential)
            } else {
                val error = SecurityError.TokenRefreshFailed(
                    diagnostic = Diagnostic(description = "Refresh provider returned null")
                )
                _state.value = SessionState.Expired
                _events.emit(SessionEvent.RefreshFailed(error))
                RefreshOutcome.Failed(error)
            }
        } catch (e: Exception) {
            val error = SecurityError.TokenRefreshFailed(
                diagnostic = Diagnostic(
                    description = e.message ?: "Token refresh failed",
                    cause = e
                )
            )
            _state.value = SessionState.Expired
            _events.emit(SessionEvent.RefreshFailed(error))
            RefreshOutcome.Failed(error)
        }
    }

    override suspend fun endSession(): Unit = mutex.withLock {
        clearPersistedCredentials()
        _state.value = SessionState.Idle
        _events.emit(SessionEvent.Ended)
    }

    override suspend fun invalidate(): Unit = mutex.withLock {
        clearPersistedCredentials()
        _state.value = SessionState.Idle
        _events.emit(SessionEvent.Invalidated)
    }

    // -- Persistence helpers --

    private suspend fun persistCredentials(credentials: SessionCredentials) {
        val token = when (val c = credentials.credential) {
            is Credential.Bearer -> c.token
            is Credential.ApiKey -> c.key
            is Credential.Basic -> "${c.username}:${c.password}"
            is Credential.Custom -> c.properties.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
        store.putString(KEY_CREDENTIAL_TYPE, credentials.credential::class.simpleName ?: "Unknown")
        store.putString(KEY_CREDENTIAL_VALUE, token)
        credentials.refreshToken?.let { store.putString(KEY_REFRESH_TOKEN, it) }
        credentials.expiresAtMs?.let { store.putString(KEY_EXPIRES_AT, it.toString()) }
    }

    private suspend fun clearPersistedCredentials() {
        store.remove(KEY_CREDENTIAL_TYPE)
        store.remove(KEY_CREDENTIAL_VALUE)
        store.remove(KEY_REFRESH_TOKEN)
        store.remove(KEY_EXPIRES_AT)
    }

    companion object {
        internal const val KEY_CREDENTIAL_TYPE = "session_credential_type"
        internal const val KEY_CREDENTIAL_VALUE = "session_credential_value"
        internal const val KEY_REFRESH_TOKEN = "session_refresh_token"
        internal const val KEY_EXPIRES_AT = "session_expires_at"
    }
}

internal expect fun currentTimeMillis(): Long
