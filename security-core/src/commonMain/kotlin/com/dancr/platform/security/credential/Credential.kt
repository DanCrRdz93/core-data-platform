package com.dancr.platform.security.credential

sealed interface Credential {

    data class Bearer(val token: String) : Credential {
        override fun toString(): String = "Bearer(token=██)"
    }

    data class ApiKey(val key: String, val headerName: String = "X-API-Key") : Credential {
        override fun toString(): String = "ApiKey(headerName=$headerName, key=██)"
    }

    data class Basic(val username: String, val password: String) : Credential {
        override fun toString(): String = "Basic(username=██, password=██)"
    }

    data class Custom(val type: String, val properties: Map<String, String>) : Credential {
        override fun toString(): String = "Custom(type=$type, properties=[${properties.keys.joinToString()}])"
    }
}
