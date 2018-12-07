package com.chrisfry.socialq.services

import android.app.Service
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.userinterface.activities.AccessTokenReceiverActivity
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Pager
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.UserPublic
import retrofit.client.Response
import java.util.HashMap

abstract class SpotifyAccessService : Service() {
    companion object {
        val TAG = SpotifyAccessService::class.java.name
    }
    // API for retrieve SpotifyService object
    private val spotifyApi = SpotifyApi()
    // Service for adding songs to the queue
    protected lateinit var spotifyService: SpotifyService
    // Flag for indicating if we actually need a new access token (set to true when shutting down)
    private var isServiceEnding = false
    // User object for host's Spotify account
    protected var currentUser: UserPublic? = null
    // Playlist object for the queue
    protected lateinit var playlist: Playlist
    // Tracks from queue playlist
    protected val playlistTracks = mutableListOf<PlaylistTrack>()

    fun accessTokenUpdated() {
        if (AccessModel.getAccessExpireTime() > SystemClock.elapsedRealtime()) {
            startAccessRefreshThread()
            initSpotifyElements(AccessModel.getAccessToken())
        }
    }

    protected open fun initSpotifyElements(accessToken: String) {
        Log.d(TAG, "Initializing Spotify elements")

        spotifyApi.setAccessToken(accessToken)
        spotifyService = spotifyApi.service
    }

    protected fun refreshPlaylist() {
        Log.d(TAG, "Refreshing playlist")
        spotifyService.getPlaylist(currentUser!!.id, playlist.id, refreshPlaylistCallback)
    }

    private val refreshPlaylistCallback = object : SpotifyCallback<Playlist>() {
        override fun success(playlist: Playlist?, response: Response?) {
            if (playlist != null) {
                Log.d(TAG, "Successfully retrieved playlist")

                this@SpotifyAccessService.playlist = playlist
                playlistTracks.clear()
                playlistTracks.addAll(playlist.tracks.items)

                if (playlistTracks.size < playlist.tracks.total) {
                    // Need to pull more tracks
                    val options = HashMap<String, Any>()
                    options[SpotifyService.OFFSET] = playlistTracks.size
                    options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_TRACK_LIMIT

                    spotifyService.getPlaylistTracks(currentUser!!.id, playlist.id, options, playlistTrackCallback)
                } else {
                    Log.d(TAG, "Finished retrieving playlist tracks")
                    playlistRefreshComplete()
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Failed to retrieve playlist")
        }

    }

    private val playlistTrackCallback = object : SpotifyCallback<Pager<PlaylistTrack>>() {
        override fun success(trackPager: Pager<PlaylistTrack>?, response: Response?) {
            if (trackPager != null) {
                playlistTracks.addAll(trackPager.items)

                if (playlistTracks.size < trackPager.total) {
                    Log.d(TAG, "Successfully retrieved some playlist tracks")
                    val options = HashMap<String, Any>()
                    options[SpotifyService.OFFSET] = playlistTracks.size

                    spotifyService.getPlaylistTracks(currentUser!!.id, playlist.id, options, this)
                } else {
                    Log.d(TAG, "Finished retrieving playlist tracks")
                    playlistRefreshComplete()
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            if (spotifyError != null) {
                Log.e(TAG, spotifyError.errorDetails.message)
            }
            Log.e(TAG, "Failed to retrieve playlist tracks")
        }

    }

    protected fun requestAccessToken() {
        val accessIntent = Intent()
        accessIntent.setClass(applicationContext, AccessTokenReceiverActivity::class.java)
        accessIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(accessIntent)
    }

    private fun startAccessRefreshThread() {
        AccessRefreshThread().start()
    }

    /**
     * Inner thread class used to detect when a new access code is needed and send message to handler to request a new one.
     */
    private inner class AccessRefreshThread internal constructor() : Thread(Runnable {
        while (true) {
            if (SystemClock.elapsedRealtime() >= AccessModel.getAccessExpireTime()) {
                if (isServiceEnding) {
                    Log.d(HostService.TAG, "Service is ending, don't need new access token")
                    break
                } else {
                    Log.d(HostService.TAG, "Detected that we need a new access token")
                    requestAccessToken()
                    break
                }
            }
        }
    })

    override fun onDestroy() {
        isServiceEnding = true

        // Clear access model. Should fire our access refresh thread (which won't due anything due to the flag change above)
        AccessModel.setAccess("", -1)
        super.onDestroy()
    }

    abstract fun playlistRefreshComplete()
}