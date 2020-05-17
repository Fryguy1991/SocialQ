package com.chrisf.socialq.network

import com.chrisf.socialq.SocialQPreferences
import com.chrisf.socialq.model.AuthTokens
import com.chrisf.socialq.network.AuthService.AuthResponse.*
import io.reactivex.Completable
import io.reactivex.Single
import java.lang.IllegalStateException
import java.net.SocketTimeoutException
import javax.inject.Inject

/**
 * Service for communicating with the SocialQ auth server
 */
class AuthService @Inject constructor(
    private val authApi: AuthApi,
    private val socialQPreferences: SocialQPreferences
) {
    /**
     * Get access and refresh tokens using the auth code
     */
    fun getTokensWithAuthCode(authCode: String): Single<AuthResponse<AuthTokens>> {
        return authApi.getAuthTokens(authCode)
            .map { response ->
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Success(body.authTokens)
                } else {
                    Failure(IllegalStateException("Request failed or body was null"))
                }
            }
            .onErrorReturn { throwable ->
                if (throwable is SocketTimeoutException) Timeout else Failure(throwable)
            }
    }

    /**
     * Get access token using the refresh token
     */
    fun getAccessTokenWithRefreshToken(refreshCode: String): Single<AuthResponse<String>> {
        return authApi.getAccessToken(refreshCode)
            .map { response ->
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    Success(body.accessToken.accessToken)
                } else {
                    Failure(IllegalStateException("Request failed or body was null"))
                }
            }
            .onErrorReturn { throwable ->
                if (throwable is SocketTimeoutException) Timeout else Failure(throwable)
            }
    }

    /**
     * This method will attempt to fetch a new access token and complete (whether or not it's successful)
     */
    fun attemptAccessTokenRefresh(): Completable {
        val refreshToken = socialQPreferences.refreshToken
        return if (refreshToken.isNullOrBlank()) {
            Completable.complete()
        } else {
            authApi.getAccessToken(refreshToken)
                .flatMapCompletable { response ->
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        socialQPreferences.accessToken = body.accessToken.accessToken
                    }
                    Completable.complete()
                }
                .onErrorComplete()
        }
    }

    sealed class AuthResponse<out T> {
        data class Success<T>(val body: T) : AuthResponse<T>()

        data class Failure(val throwable: Throwable) : AuthResponse<Nothing>()

        object Timeout : AuthResponse<Nothing>()
    }
}
