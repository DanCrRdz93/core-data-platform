package com.dancr.platform.sample.datasource

import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.datasource.RemoteDataSource
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.dto.UserDto
import kotlinx.serialization.json.Json

class UserRemoteDataSource(
    executor: SafeRequestExecutor
) : RemoteDataSource(executor) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchUsers(): NetworkResult<List<UserDto>> = execute(
        request = HttpRequest(
            path = "/users",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )

    suspend fun fetchUser(id: Long): NetworkResult<UserDto> = execute(
        request = HttpRequest(
            path = "/users/$id",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )
}
