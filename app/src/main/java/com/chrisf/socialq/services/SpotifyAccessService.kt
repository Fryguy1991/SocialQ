package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.Service
import android.graphics.BitmapFactory
import android.os.Binder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.SpotifyApi
import kaaes.spotify.webapi.android.SpotifyCallback
import kaaes.spotify.webapi.android.SpotifyError
import kaaes.spotify.webapi.android.SpotifyService
import kaaes.spotify.webapi.android.models.Pager
import kaaes.spotify.webapi.android.models.Playlist
import kaaes.spotify.webapi.android.models.PlaylistTrack
import kaaes.spotify.webapi.android.models.Track
import retrofit.client.Response
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import kotlin.collections.HashMap

abstract class SpotifyAccessService : Service() {
    companion object {
        val TAG = SpotifyAccessService::class.java.name
    }

    override fun onCreate() {
        super.onCreate()

        (application as App).appComponent.inject(this)
    }

    open inner class SpotifyAccessServiceBinder : Binder() {
        open fun getService(): SpotifyAccessService {
            return this@SpotifyAccessService
        }
    }

    // API for retrieving SpotifyService object
    @Inject
    protected lateinit var spotifyApi: SpotifyApi
    // Flag for indicating if we actually need a new access token (set to true when shutting down)
    private var isServiceEnding = false
    // User object for host's Spotify account
    protected var playlistOwnerUserId: String = ""
    // Playlist object for the queue
    protected lateinit var playlist: Playlist
    // Tracks from queue playlist
    protected val playlistTracks = mutableListOf<PlaylistTrack>()

    // NOTIFICATION ELEMENTS
    // Reference to notification manager
    protected lateinit var notificationManager: NotificationManager
    // Builder for foreground notification
    protected lateinit var notificationBuilder: NotificationCompat.Builder
    // Reference to media session
    protected lateinit var mediaSession: MediaSessionCompat
    // Reference to meta data builder
    protected val metaDataBuilder = MediaMetadataCompat.Builder()

    protected fun refreshPlaylist() {
        Log.d(TAG, "Refreshing playlist")

        val options = HashMap<String, Any>()
        options[SpotifyService.MARKET] = AppConstants.PARAM_FROM_TOKEN

        spotifyApi.service.getPlaylist(playlistOwnerUserId, playlist.id, options, refreshPlaylistCallback)
    }

    protected fun pullNewTrack(newTrackIndex: Int) {
        Log.d(TAG, "Pulling newly added track")

        val options = HashMap<String, Any>()
        options[SpotifyService.MARKET] = AppConstants.PARAM_FROM_TOKEN
        options[SpotifyService.LIMIT] = 1
        options[SpotifyService.OFFSET] = newTrackIndex

        spotifyApi.service.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, newTrackCallback)
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

                    spotifyApi.service.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, playlistTrackCallback)
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

                    spotifyApi.service.getPlaylistTracks(playlistOwnerUserId, playlist.id, options, this)
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

    override fun onDestroy() {
        // Ensure media session is released
        mediaSession.release()

        isServiceEnding = true
        super.onDestroy()
    }

    abstract fun playlistRefreshComplete()

    abstract fun newTrackRetrievalComplete(newTrackIndex: Int)

    abstract fun authorizationFailed()
}