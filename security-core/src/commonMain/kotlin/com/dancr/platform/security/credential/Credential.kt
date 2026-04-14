package com.dancr.platform.security.credential

sealed interface Credential {

    data class Bearer(val token: String) : Credential

    data class ApiKey(val key: String, val headerName: String = "X-API-Key") : Credential

    data class Basic(val username: String, val password: String) : Credential

    data class Custom(val type: String, val properties: Map<String, String>) : Credential
}
