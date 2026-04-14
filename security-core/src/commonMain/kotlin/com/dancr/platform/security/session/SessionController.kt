package com.dancr.platform.security.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SessionController {

    val state: StateFlow<SessionState>

    val events: Flow<SessionEvent>

    suspend fun startSession(credentials: SessionCredentials)

    // TODO: Evolve return type to a sealed result (RefreshOutcome) to distinguish
    //  between "refreshed", "refresh not needed", and specific failure reasons.
    suspend fun refreshSession(): Boolean

    suspend fun endSession()

    // TODO: Add suspend fun invalidate() for force-logout from any layer (e.g. on 401).
    // TODO: Add val isAuthenticated: Boolean convenience derived from state.
}
