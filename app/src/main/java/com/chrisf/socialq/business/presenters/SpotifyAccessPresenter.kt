package com.chrisf.socialq.business.presenters

import com.chrisf.socialq.business.dagger.components.AppComponent
import kaaes.spotify.webapi.android.SpotifyApi
import javax.inject.Inject

abstract class SpotifyAccessPresenter(spotifyComponent: AppComponent) : BasePresenter() {
    companion object {
        val TAG = SpotifyAccessPresenter::class.java.name
    }

    init {
        // Setup service for searching Spotify library
        spotifyComponent.inject(this)
    }

    // Spotify API reference for access to Spotify data
    @Inject
    protected lateinit var spotifyApi: SpotifyApi
}