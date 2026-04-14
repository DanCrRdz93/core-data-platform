package com.dancr.platform.security.credential

import com.dancr.platform.security.util.Base64

object CredentialHeaderMapper {

    fun toHeaders(credential: Credential): Map<String, String> = when (credential) {
        is Credential.Bearer ->
            mapOf("Authorization" to "Bearer ${credential.token}")
        is Credential.ApiKey ->
            mapOf(credential.headerName to credential.key)
        is Credential.Basic -> {
            val encoded = Base64.encodeToString("${credential.username}:${credential.password}")
            mapOf("Authorization" to "Basic $encoded")
        }
        is Credential.Custom ->
            credential.properties
    }
}
