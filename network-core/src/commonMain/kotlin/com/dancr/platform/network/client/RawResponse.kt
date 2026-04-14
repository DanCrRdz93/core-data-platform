package com.dancr.platform.network.client

data class RawResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray? = null
) {
    val isSuccessful: Boolean get() = statusCode in 200..299

    val contentType: String?
        get() = headers.entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value?.firstOrNull()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RawResponse
        return statusCode == other.statusCode &&
            headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "RawResponse(statusCode=$statusCode, headers=[${headers.keys.joinToString()}], body=${body?.size ?: 0} bytes)"
}
