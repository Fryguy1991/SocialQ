package com.chrisfry.socialq.services

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.os.Binder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chrisfry.socialq.business.AppConstants
import com.chrisfry.socialq.model.AccessModel
import com.chrisfry.socialq.userinterface.activities.AccessTokenReceiverActivity
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Pager
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.Track
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit.client.Response
import java.io.IOException
import java.net.URL
import kotlin.collections.HashMap

abstract class SpotifyAccessService : Service() {
    companion object {
        val TAG = SpotifyAccessService::class.java.name
    }

    open inner class SpotifyAccessServiceBinder : Binder() {
        open fun getService(): SpotifyAccessService {
            return this@SpotifyAccessService
        }
    }

    // API for retrieve SpotifyService object
    private val spotifyApi = SpotifyApi()
    // Service for adding songs to the queue
    protected lateinit var spotifyService: SpotifyService
    // Flag for indicating if we actually need a new access token (set to true when shutting down)
    private var isServiceEnding = false
    // User object for host's Spotify account
    protected var playlistOwnerUserId: String = ""
    // Playlist object for the queue
    protected lateinit var playlist: Playlist
    // Tracks from queue playlist
    protected val playlistTracks = mutableListOf<PlaylistTrack>()
    // Flag indicating if the service is a host
    private var isHost = true

    // NOTIFICATION ELEMENTS
    // Reference to notification manager
    protected lateinit var notificationManager: NotificationManager
    // Builder for foreground notification
    protected lateinit var notificationBuilder: NotificationCompat.Builder
    // Reference to media session
    protected lateinit var mediaSession: MediaSessionCompat
    // Reference to meta data builder
    protected val metaDataBuilder = MediaMetadataCompat.Builder()

    fun accessTokenUpdated() {
        if (AccessModel.getAccessExpireTime() > SystemClock.elapsedRealtime()) {
            startAccessRefreshThread()
            initSpotifyElements(AccessModel.getAccessToken())
        }
    }

    fun authCodeReceived() {
        if (AccessModel.getAuthorizationCode().isNullOrEmpty()) {
            Log.e(TAG, "Error invalid authorization code")
        } else {
            Log.d(TAG, "Have authorization code. Request access/refresh tokens")
            val client = OkHttpClient()
            val request = Request.Builder().url("http://54.86.80.241/" + AccessModel.getAuthorizationCode()).build()

            val response = client.newCall(request).execute()
            val responseString = response.body()?.string()

            if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val refreshToken = bodyJson.getString(AppConstants.JSON_REFRESH_TOEKN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Log.d(TAG, "Received authorization:\nAccess Token: $accessToken\nRefresh Token: $refreshToken\nExpires In: $expiresIn seconds")

                // Store refresh token
                AccessModel.setRefreshToken(refreshToken)

                // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val expireTime = SystemClock.elapsedRealtime() + (expiresIn - 60) * 1000
                // Set access token and expire time into model
                AccessModel.setAccess(accessToken, expireTime)

                startAccessRefreshThread()
                initSpotifyElements(accessToken)
            } else {
                Log.e(TAG, "Response was unsuccessful or response string was null")
            }
        }
    }

    fun authFailed() {
        Log.e(TAG, "Authorization failed. Shutting down service")
        stopSelf()
    }

    protected open fun initSpotifyElements(accessToken: String) {
        Log.d(TAG, "Initializing Spotify elements")

        spotifyApi.setAccessToken(accessToken)
        spotifyService = spotifyApi.service
    }

    protected fun refreshPlaylist() {
        Log.d(TAG, "Refreshing playlist")

        val options = HashMap<String, Any>()
        options[SpotifyService.MARKET] = AppConstants.PARAM_FROM_TOKEN

        spotifyService.getPlaylist(playlistOwnerUserId, playlist.id, options, refreshPlaylistCallback)
    }

    protected fun pullNewTrack(newTrackIndex: Int) {
        Log.d(TAG, "Pulling newly added track")

        val options = HashMap<String, Any>()
        options[SpotifyService.MARKET] = AppConstants.PARAM_FROM_TOKEN
        options[SpotifyService.LIMIT] = 1
        options[SpotifyService.OFFSET] = newTrackIndex

        spotifyService.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, newTrackCallback)
    }

    private val newTrackCallback = object : SpotifyCallback<Pager<PlaylistTrack>>() {
        override fun success(pager: Pager<PlaylistTrack>?, response: Response?) {
            if (pager != null) {
                Log.d(TAG, "Successfully pulled newly added track")

                playlistTracks.add(pager.offset, pager.items[0])
                newTrackRetrievalComplete(pager.offset)
            } else {
                Log.e(TAG, "Pager was null when retrieving newly added track")
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to retrieve newly added track")
        }
    }

    private val refreshPlaylistCallback = object : SpotifyCallback<Playlist>() {
        override fun success(playlist: Playlist?, response: Response?) {
            if (playlist != null) {
                Log.d(TAG, "Successfully retrieved playlist")

                this@SpotifyAccessService.playlist = playlist
                playlistTracks.clear()
                playlistTracks.addAll(playlist.tracks.items)

                if (playlist.tracks.offset + playlist.tracks.items.size < playlist.tracks.total) {
                    // Need to pull more tracks
                    val options = HashMap<String, Any>()
                    options[SpotifyService.OFFSET] = playlistTracks.size
                    options[SpotifyService.LIMIT] = AppConstants.PLAYLIST_TRACK_LIMIT

                    spotifyService.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, playlistTrackCallback)
                } else {
                    Log.d(TAG, "Finished retrieving playlist tracks")
                    playlistRefreshComplete()
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to retrieve playlist")
        }
    }

    protected val playlistTrackCallback = object : SpotifyCallback<Pager<PlaylistTrack>>() {
        override fun success(trackPager: Pager<PlaylistTrack>?, response: Response?) {
            if (trackPager != null) {
                playlistTracks.addAll(trackPager.items)

                if (trackPager.offset + trackPager.items.size < trackPager.total) {
                    Log.d(TAG, "Successfully retrieved some playlist tracks")
                    val options = HashMap<String, Any>()
                    options[SpotifyService.OFFSET] = playlistTracks.size

                    spotifyService.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, this)
                } else {
                    Log.d(TAG, "Finished retrieving playlist tracks")
                    playlistRefreshComplete()
                }
            }
        }

        override fun failure(spotifyError: SpotifyError?) {
            Log.e(TAG, spotifyError?.errorDetails?.message.toString())
            Log.e(TAG, "Failed to retrieve playlist tracks")
        }
    }

    protected fun requestHostAuthorization() {
        isHost = true
        val accessIntent = Intent()
        accessIntent.setClass(applicationContext, AccessTokenReceiverActivity::class.java)
        accessIntent.putExtra(AppConstants.IS_HOST_KEY, true)
        accessIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(accessIntent)
    }

    protected fun requestClientAuthorization() {
        isHost = false
        val accessIntent = Intent()
        accessIntent.setClass(applicationContext, AccessTokenReceiverActivity::class.java)
        accessIntent.putExtra(AppConstants.IS_HOST_KEY, false)
        accessIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(accessIntent)
    }

    private fun refreshAccessToken() {
        if (AccessModel.getRefreshToken().isNullOrEmpty()) {
            Log.e(TAG, "Error invalid refresh token")
        } else {
            Log.d(TAG, "Request new access token")
            // TODO: Should be able to support requesting multiple AWS instances
            val client = OkHttpClient()
            val request = Request.Builder().url("http://54.86.80.241/" + AccessModel.getRefreshToken()).build()

            val response = client.newCall(request).execute()
            val responseString = response.body()?.string()

            if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                val bodyJson = JSONObject(responseString).getJSONObject(AppConstants.JSON_BODY_KEY)

                val accessToken = bodyJson.getString(AppConstants.JSON_ACCESS_TOKEN_KEY)
                val expiresIn = bodyJson.getInt(AppConstants.JSON_EXPIRES_IN_KEY)

                Log.d(TAG, "Received new access token:\nAccess Token: $accessToken\nExpires In: $expiresIn seconds")

                // Calculate when access token expires (response "ExpiresIn" is in seconds, subtract a minute to worry less about timing)
                val expireTime = SystemClock.elapsedRealtime() + (expiresIn - 60) * 1000
                // Set access token and expire time into model
                AccessModel.setAccess(accessToken, expireTime)

                startAccessRefreshThread()
                initSpotifyElements(accessToken)

                // Broadcast that the access code has been updated
                Log.d(TAG, "Broadcasting that access token has been updated")
                val accessRefreshIntent = Intent(AppConstants.BR_INTENT_ACCESS_TOKEN_UPDATED)
                LocalBroadcastManager.getInstance(this).sendBroadcast(accessRefreshIntent)
            } else {
                Log.e(TAG, "Response was unsuccessful or response string was null")
            }
        }
    }

    /**
     * Sets up metadata for displaying a track in the service notification and updates that notification.
     * WARNING: Notification manager and builder need to be setup by child class before using this method.
     */
    protected fun showTrackInNotification(trackToShow: Track, serviceIsHost: Boolean) {
        // Update metadata for media session
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackToShow.album?.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, DisplayUtils.getTrackArtistString(trackToShow))

        // Attempt to update album art in notification and metadata
        if (trackToShow.album.images.size > 0) {
            try {
                val url = URL(trackToShow.album.images[0].url)
                // Retrieve album art bitmap
                val albumArtBitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                // Set bitmap data for lock screen display
                metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
                // Set bitmap data for notification
                notificationBuilder.setLargeIcon(albumArtBitmap)
            } catch (exception: IOException) {
                Log.e(TAG, "Error retrieving image bitmap: ${exception.message.toString()}")
                System.out.println(exception)
            }
        }
        mediaSession.setMetadata(metaDataBuilder.build())

        // Update notification data
        notificationBuilder.setContentTitle(trackToShow.name)
        notificationBuilder.setContentText(DisplayUtils.getTrackArtistString(trackToShow))

        val notificationId = when (serviceIsHost) {
            true -> AppConstants.HOST_SERVICE_ID
            false -> AppConstants.CLIENT_SERVICE_ID
        }

        // Display updated notification
        notificationManager.notify(notificationId, notificationBuilder.build())
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
                    refreshAccessToken()
                    break
                }
            }
        }
    })

    override fun onDestroy() {
        // Ensure media session is released
        mediaSession.release()

        isServiceEnding = true

        // Clear access model. Should fire our access refresh thread (which won't due anything due to the flag change above)
        AccessModel.setAccess("", -1)
        super.onDestroy()
    }

    abstract fun playlistRefreshComplete()

    abstract fun newTrackRetrievalComplete(newTrackIndex: Int)
}