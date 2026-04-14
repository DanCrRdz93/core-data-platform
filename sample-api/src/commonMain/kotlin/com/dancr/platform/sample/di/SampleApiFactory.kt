package com.dancr.platform.sample.di

import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.config.RetryPolicy
import com.dancr.platform.network.execution.DefaultSafeRequestExecutor
import com.dancr.platform.network.execution.RequestInterceptor
import com.dancr.platform.network.ktor.KtorErrorClassifier
import com.dancr.platform.network.ktor.KtorHttpEngine
import com.dancr.platform.network.observability.NetworkEventObserver
import com.dancr.platform.security.credential.CredentialHeaderMapper
import com.dancr.platform.security.credential.CredentialProvider
import com.dancr.platform.sample.datasource.UserRemoteDataSource
import com.dancr.platform.sample.repository.UserRepository
import kotlin.time.Duration.Companion.seconds

object SampleApiFactory {

    private val defaultConfig = NetworkConfig(
        baseUrl = "https://jsonplaceholder.typicode.com",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 15.seconds,
        readTimeout = 30.seconds,
        retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 2)
    )

    fun create(
        config: NetworkConfig = defaultConfig,
        credentialProvider: CredentialProvider? = null,
        observers: List<NetworkEventObserver> = emptyList()
    ): UserRepository {
        val engine = KtorHttpEngine.create(config)

        val interceptors = buildList {
            if (credentialProvider != null) {
                add(authInterceptor(credentialProvider))
            }
        }

        val executor = DefaultSafeRequestExecutor(
            engine = engine,
            config = config,
            classifier = KtorErrorClassifier(),
            interceptors = interceptors,
            observers = observers
        )

        val dataSource = UserRemoteDataSource(executor)
        return UserRepository(dataSource)
    }

    private fun authInterceptor(provider: CredentialProvider) =
        RequestInterceptor { request, _ ->
            val credential = provider.current() ?: return@RequestInterceptor request
            val headers = CredentialHeaderMapper.toHeaders(credential)
            request.copy(headers = request.headers + headers)
        }
}
