package com.chrisfry.socialq.business.SpotifyWebApi

import android.util.Base64
import com.chrisfry.socialq.business.AppConstants
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.android.MainThreadExecutor
import retrofit.converter.GsonConverter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class FrySpotifyWebApi {
    // Endpoint for authorization refresh
    val SPOTIFY_ACCOUNTS_WEB_ENDPOINT = "https://accounts.spotify.com/"
    // Service for calls to spotify for authorization
    val authorizationService: FrySpotifyAuthorizationService

    constructor() {
        val httpExecutor = Executors.newSingleThreadExecutor()
        val callbackExecutor = MainThreadExecutor()
        authorizationService = init(httpExecutor, callbackExecutor)
    }

    constructor(httpExecutor: Executor, callbackExecutor: Executor) : super() {
        authorizationService = init(httpExecutor, callbackExecutor)
    }

    private inner class WebApiAuthenticator() : RequestInterceptor {
        override fun intercept(request: RequestInterceptor.RequestFacade?) {
            if (request != null) {
//                val clientId = Base64.encodeToString((AppConstants.CLIENT_ID + ":0b22caf9a4674adea18c4b977d85a209").toByteArray(), android.util.Base64.NO_WRAP)
//                val secret = Base64.encodeToString("0b22caf9a4674adea18c4b977d85a209".toByteArray(), android.util.Base64.DEFAULT)
//                request.addHeader("Authorization", "Basic $clientId")
//                request.addHeader("Authorization", "Basic")
            }
        }
    }

    private fun init(httpExecutor: Executor, callbackExecutor: Executor): FrySpotifyAuthorizationService {
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient().newBuilder().addInterceptor(interceptor).build()

        val gson = GsonBuilder().setLenient().create()

        val retrofit = Retrofit.Builder()
                .baseUrl(SPOTIFY_ACCOUNTS_WEB_ENDPOINT)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build()
//        val restAdapter = RestAdapter.Builder().setConverter(ScalarsConverterFactory.create().stringConverter())
//                .setLogLevel(RestAdapter.LogLevel.BASIC)
//                .setExecutors(httpExecutor, callbackExecutor)
//                .setEndpoint(SPOTIFY_ACCOUNTS_WEB_ENDPOINT)
//                .setRequestInterceptor(WebApiAuthenticator())
//                .build()

        return retrofit.create(FrySpotifyAuthorizationService::class.java)
    }

}