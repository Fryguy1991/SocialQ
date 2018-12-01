package com.chrisfry.socialq.business.presenters

import android.util.Log
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyService

abstract class SpotifyAccessPresenter : BasePresenter(), ISpotifyAccessPresenter {
    // Spotify search elements
    protected var spotifyApi: SpotifyApi? = null
    protected lateinit var spotifyService: SpotifyService

    override fun receiveNewAccessToken(accessToken: String) {
        if (spotifyApi == null) {

            if (accessToken.isNotEmpty()) {
                Log.d(SearchPresenter.TAG, "Received first access token. Init Spotify search elements")

                // Setup service for searching Spotify library
                val componenet = DaggerSpotifyComponent.builder().spotifyModule(
                        SpotifyModule(accessToken)).build()

                spotifyApi = componenet.api()
                spotifyService = spotifyApi!!.service
            } else {
                Log.e(SearchPresenter.TAG, "Access token is empty")
                // TODO: Let view know that the presenter does not have access anymore
            }
        } else {
            Log.d(SearchPresenter.TAG, "Refreshing api/service access token")

            spotifyApi!!.setAccessToken(accessToken)
            spotifyService = spotifyApi!!.service
        }
    }
}