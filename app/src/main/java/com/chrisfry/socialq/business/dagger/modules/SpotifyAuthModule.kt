package com.chrisfry.socialq.business.dagger.modules

import com.chrisfry.socialq.business.SpotifyWebApi.FrySpotifyAuthorizationService
import com.chrisfry.socialq.business.SpotifyWebApi.FrySpotifyWebApi
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class SpotifyAuthModule {

    @Provides @Singleton
    fun provideSpotifyAuthApi() : FrySpotifyWebApi {
        return FrySpotifyWebApi()
    }

    @Provides @Singleton
    fun provideSpotifyAuthorizationService(spotifyAuthApi : FrySpotifyWebApi) : FrySpotifyAuthorizationService {
        return spotifyAuthApi.authorizationService
    }
}