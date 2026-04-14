package com.dancr.platform.security.session

import com.dancr.platform.security.credential.Credential

sealed interface SessionState {

    data object Idle : SessionState

    data class Active(val credential: Credential) : SessionState

    data object Expired : SessionState
}
