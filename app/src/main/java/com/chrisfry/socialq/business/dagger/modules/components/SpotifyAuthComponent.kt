package com.chrisfry.socialq.business.dagger.modules.components

import com.chrisfry.socialq.business.spotifyauthapi.FrySpotifyAuthorizationService
import com.chrisfry.socialq.business.spotifyauthapi.FrySpotifyWebApi
import com.chrisfry.socialq.business.dagger.modules.SpotifyAuthModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(SpotifyAuthModule::class))
interface SpotifyAuthComponent {
    fun getApi() : FrySpotifyWebApi

    fun getService() : FrySpotifyAuthorizationService
}