package com.chrisfry.socialq.userinterface.activities

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.chrisfry.socialq.R
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.business.dagger.modules.SpotifyModule
import com.chrisfry.socialq.business.dagger.modules.components.DaggerSpotifyComponent
import com.chrisfry.socialq.enums.RequestType
import com.chrisfry.socialq.model.AccessModel
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPrivate
import java.util.HashMap

abstract class BaseActivity : AppCompatActivity(){
    private val TAG = BaseActivity::class.java.name

    // Spotify elements
    lateinit var mSpotifyApi: SpotifyApi
    lateinit var mSpotifyService: SpotifyService
    var mCurrentUser: UserPrivate? = null
    var mPlaylist: Playlist? = null
    val mPlaylistTracks = mutableListOf<PlaylistTrack>()

    var accessScopes: Array<String>? = null

    // Handler for sending messages to the UI thread
    protected val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                AppConstants.ACCESS_TOKEN_REFRESH ->
                    // Don't request access tokens if activity is being shut down
                    if (!isFinishing) {
                        Log.d(TAG, "Requesting new access token on UI thread")
                        requestNewAccessToken()
                    }
            }
        }
    }

    /**
     * Use Spotify login activity to retrieve an access token
     */
    protected fun requestNewAccessToken() {
        val builder = AuthenticationRequest.Builder(
                AppConstants.CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                AppConstants.REDIRECT_URI)
        builder.setScopes(accessScopes!!)
        val request = builder.build()
        AuthenticationClient.openLoginActivity(this, RequestType.SPOTIFY_AUTHENTICATION_REQUEST.requestCode, request)
    }

    protected fun refreshPlaylist() {
        Log.d(TAG, "Refreshing playlist")
        mPlaylist = mSpotifyService.getPlaylist(mCurrentUser!!.id, mPlaylist!!.id)
        refreshPlaylistTracks()
    }

    protected fun refreshPlaylistTracks() {
        Log.d(TAG, "Refreshing playlist track list")
        mPlaylistTracks.clear()
        // refreshPlaylist already retrieved the first 100 tracks, add to list
        mPlaylistTracks.addAll(mPlaylist!!.tracks.items)
        // TODO: This can slow down app functionality if there are a lot of tracks (every 100 tracks in the playlist is another call to the spotify API)
        var i = 100
        while (i < mPlaylist!!.tracks.total) {
            val iterationQueryParameters = HashMap<String, Any>()
            iterationQueryParameters["offset"] = i
            val iterationTracks = mSpotifyService.getPlaylistTracks(mCurrentUser!!.id, mPlaylist!!.id, iterationQueryParameters)

            mPlaylistTracks.addAll(iterationTracks.items)
            i += 100
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_fragment -> {
                val searchIntent = Intent(this, SearchActivity::class.java)
                startActivityForResult(searchIntent, RequestType.SEARCH_REQUEST.requestCode)
                return true
            }
            else ->
                // Do nothing
                return false
        }
    }

    protected fun initSpotifyElements(accessToken: String) {
        Log.d(TAG, "Initializing Spotify elements")

        // Setup service for searching Spotify library
        val componenet = DaggerSpotifyComponent.builder().spotifyModule(
                SpotifyModule(accessToken)).build()

        mSpotifyApi = componenet.api()
        mSpotifyService = componenet.service()

        mCurrentUser = mSpotifyService.getMe()
    }

    protected fun startAccessRefreshThread() {
        AccessRefreshThread().start()
    }

    /**
     * Inner thread class used to detect when a new access code is needed and send message to handler to request a new one.
     */
    private inner class AccessRefreshThread internal constructor() : Thread(Runnable {
        while (true) {
            if (System.currentTimeMillis() >= AccessModel.getAccessExpireTime()) {
                Log.d(TAG, "Detected that we need a new access token")
                val message = Message()
                message.what = AppConstants.ACCESS_TOKEN_REFRESH
                mHandler.dispatchMessage(message)
                break
            }
        }
    })
}