package com.chrisf.socialq.business.dagger.modules

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import dagger.Module
import dagger.Provides
import kaaes.spotify.webapi.android.SpotifyApi
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
    fun providesSpotifyApi() : SpotifyApi {
        return SpotifyApi()
    }
}