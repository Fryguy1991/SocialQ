package com.chrisf.socialq.dagger.modules

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.chrisf.socialq.model.AccessModel
import com.chrisf.socialq.network.SpotifyApi
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import io.reactivex.disposables.CompositeDisposable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
class AppModule(private val app: Context) {

    @Provides
    fun providesAppContext(): Context {
        return app
    }

    @Provides
    fun providesResources(): Resources {
        return app.resources
    }

    @Provides
    fun providesSharedPrefs(): SharedPreferences {
        return app.getSharedPreferences("socialq_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providesSpotifyApi(): SpotifyApi {
        val builder = OkHttpClient.Builder()
        builder.addInterceptor { chain ->
            val request = chain.request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer " + AccessModel.getAccessToken())
                    .build()
            chain.proceed(request)
        }
        builder.addInterceptor (
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        )

        val gson = GsonBuilder()
                .setPrettyPrinting()
                .create()

        return Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl(SpotifyApi.SPOTIFY_BASE_API_ENDPOINT)
                .client(builder.build())
                .build()
                .create(SpotifyApi::class.java)
    }

    @Provides
    fun providesCompositeDisposable(): CompositeDisposable {
        return CompositeDisposable()
    }
}