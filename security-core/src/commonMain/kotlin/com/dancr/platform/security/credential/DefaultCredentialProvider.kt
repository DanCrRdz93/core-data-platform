package com.dancr.platform.security.credential

import com.dancr.platform.security.session.RefreshOutcome
import com.dancr.platform.security.session.SessionController
import com.dancr.platform.security.session.SessionState

class DefaultCredentialProvider(
    private val sessionController: SessionController
) : CredentialProvider {

    override suspend fun current(): Credential? =
        when (val state = sessionController.state.value) {
            is SessionState.Active -> state.credential
            is SessionState.Idle -> null
            is SessionState.Expired -> null
        }

    override suspend fun refresh(): Credential? =
        when (val outcome = sessionController.refreshSession()) {
            is RefreshOutcome.Refreshed -> outcome.credential
            is RefreshOutcome.NotNeeded -> current()
            is RefreshOutcome.Failed -> null
        }

    override suspend fun invalidate() {
        sessionController.invalidate()
    }
}
