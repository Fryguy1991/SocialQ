package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.Service
import android.graphics.BitmapFactory
import android.os.Binder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.model.spotify.Playlist
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.utils.DisplayUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import java.io.IOException
import java.net.URL
import javax.inject.Inject

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

    protected val subscriptions = CompositeDisposable()

    // API for retrieving SpotifyService object
    @Inject
    protected lateinit var spotifyApi: FrySpotifyService
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
        Timber.d("Refreshing playlist")

        playlistTracks.clear()
        getPlaylistTracks(playlist.id)
    }

    private fun getPlaylistTracks(playlistId: String, offset: Int = 0) {
        // TODO: Get this off the main thread
        @Suppress("CheckResult")
        spotifyApi.getPlaylistTracks(playlistId, 50, offset)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { response ->
                    if (response.body() == null) {
                        // TODO: Try again?
                    } else {
                        val tracks = response.body()!!
                        playlistTracks.addAll(tracks.items)

                        if (tracks.next != null) {
                            val nextOffset = tracks.offset + tracks.items.size
                            getPlaylistTracks(playlistId, nextOffset)
                        } else {
                            playlistRefreshComplete()
                        }
                    }
                }
    }

    protected fun pullNewTrack(newTrackIndex: Int) {
        Timber.d("Pulling newly added track")

        @Suppress("CheckResult")
        spotifyApi.getPlaylistTracks(playlist.id, 1, newTrackIndex)
                .subscribeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { subscriptions.add(it) }
                .subscribe { response ->
                    if (response.body() != null) {
                        val pager = response.body()!!
                        playlistTracks.add(pager.offset, pager.items[0])
                        newTrackRetrievalComplete(pager.offset)
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
        if (trackToShow.album.images.isNotEmpty()) {
            try {
                val url = URL(trackToShow.album.images[0].url)
                // Retrieve album art bitmap
                val albumArtBitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())

                // Set bitmap data for lock screen display
                metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
                // Set bitmap data for notification
                notificationBuilder.setLargeIcon(albumArtBitmap)
            } catch (exception: IOException) {
                Timber.e("Error retrieving image bitmap: ${exception.message.toString()}")
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

        subscriptions.clear()
        super.onDestroy()
    }

    abstract fun playlistRefreshComplete()

    abstract fun newTrackRetrievalComplete(newTrackIndex: Int)

    abstract fun authorizationFailed()
}