package com.dancr.platform.sample.repository

import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.datasource.UserRemoteDataSource
import com.dancr.platform.sample.mapper.UserMapper
import com.dancr.platform.sample.model.User

/**
 * Repository that exposes domain-level [User] models from the JSONPlaceholder API.
 *
 * Maps network DTOs to domain models via [UserMapper].
 *
 * **Example:**
 * ```kotlin
 * val repo = SampleApiFactory.create()
 * repo.getUsers()
 *     .onSuccess { users -> showUsers(users) }
 *     .onFailure { error -> showError(error.message) }
 * ```
 *
 * @param dataSource The [UserRemoteDataSource] providing raw network responses.
 * @see UserMapper
 */
class UserRepository(
    private val dataSource: UserRemoteDataSource
) {

    suspend fun getUsers(): NetworkResult<List<User>> =
        dataSource.fetchUsers().map(UserMapper::toDomain)

    suspend fun getUser(id: Long): NetworkResult<User> =
        dataSource.fetchUser(id).map(UserMapper::toDomain)
}
