package com.chrisf.socialq.network

import com.chrisf.socialq.SocialQPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor that injects the access token header from the stored values in SharedPreferences
 */
class AuthInterceptor @Inject constructor(private val preferences: SocialQPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = preferences.accessToken ?: return chain.proceed(chain.request())
        val request = chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        return chain.proceed(request)
    }
}
