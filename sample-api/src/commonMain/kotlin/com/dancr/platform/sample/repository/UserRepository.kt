package com.dancr.platform.sample.repository

import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.datasource.UserRemoteDataSource
import com.dancr.platform.sample.mapper.UserMapper
import com.dancr.platform.sample.model.User

class UserRepository(
    private val dataSource: UserRemoteDataSource
) {

    suspend fun getUsers(): NetworkResult<List<User>> =
        dataSource.fetchUsers().map(UserMapper::toDomain)

    suspend fun getUser(id: Long): NetworkResult<User> =
        dataSource.fetchUser(id).map(UserMapper::toDomain)
}
