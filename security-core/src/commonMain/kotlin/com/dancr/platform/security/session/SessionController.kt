package com.dancr.platform.security.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Manages the authentication session lifecycle.
//
// Contract:
//   startSession()   — Persists credentials, transitions state to Active, emits Started.
//   refreshSession() — Attempts to refresh; returns RefreshOutcome indicating what happened.
//   invalidate()     — Force-logout from any layer (e.g. on 401). Clears credentials, transitions to Idle.
//   endSession()     — Graceful logout. Same effect as invalidate() but semantically intentional.
//   isAuthenticated  — Derived from state. true only when state is Active. Never duplicated state.
interface SessionController {

    val state: StateFlow<SessionState>

    val events: Flow<SessionEvent>

    // Convenience: true when state is Active. Derived — never stored separately.
    val isAuthenticated: Boolean
        get() = state.value is SessionState.Active

    suspend fun startSession(credentials: SessionCredentials)

    // Returns a sealed RefreshOutcome instead of Boolean:
    //   Refreshed  — new credential obtained, session is Active.
    //   NotNeeded  — preconditions not met (no token, no provider). State unchanged.
    //   Failed     — refresh attempted but failed. Session transitions to Expired.
    suspend fun refreshSession(): RefreshOutcome

    suspend fun endSession()

    // Force-logout from any layer. Clears persisted credentials and transitions to Idle.
    // Use for 401 responses or security-triggered invalidation.
    suspend fun invalidate()
}
