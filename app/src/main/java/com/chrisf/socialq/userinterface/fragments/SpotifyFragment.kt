package com.chrisf.socialq.userinterface.fragments

import android.os.Bundle
import com.chrisf.socialq.business.dagger.modules.SpotifyModule
import com.chrisf.socialq.business.dagger.modules.components.DaggerSpotifyComponent
import com.chrisf.socialq.model.AccessModel
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyService

/**
 * Base fragment class for fragments that need to access the Spotify web API
 */
open class SpotifyFragment: BaseFragment() {
    companion object {
        fun newInstance(args: Bundle) : SpotifyFragment {
            val newFragment = SpotifyFragment()
            newFragment.arguments = args
            return newFragment
        }
    }

    // Spotify elements
    protected lateinit var mSpotifyApi: SpotifyApi
    protected lateinit var mSpotifyService: SpotifyService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve access token
        val accessToken = AccessModel.getAccessToken()

        if (!accessToken.isNullOrEmpty()) {
            // Setup service for searching Spotify library
            val component = DaggerSpotifyComponent.builder().spotifyModule(
                    SpotifyModule(accessToken)).build()

            mSpotifyApi = component.api()
            mSpotifyService = component.service()
        }
    }

    fun refreshAccessToken(token: String) {
        if (!token.isNullOrEmpty()) {
            mSpotifyApi.setAccessToken(token)
            mSpotifyService = mSpotifyApi.service
        }
    }
}