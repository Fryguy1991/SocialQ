package com.chrisf.socialq.services

import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.os.Binder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.extensions.addTo
import com.chrisf.socialq.model.spotify.Playlist
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.network.FrySpotifyService
import com.chrisf.socialq.processor.BaseProcessor
import com.chrisf.socialq.utils.DisplayUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.io.IOException
import java.net.URL

abstract class SpotifyAccessService<State, Action, Processor: BaseProcessor<State, Action>> : BaseService<State, Action, Processor>() {

    open inner class SpotifyAccessServiceBinder : Binder() {
        open fun getService(): SpotifyAccessService<State, Action, Processor> {
            return this@SpotifyAccessService
        }
    }

    // TODO: Delete this class so client and host are no longer coupled










//    override fun onDestroy() {
//        // Ensure media session is released
//        mediaSession.release()
//
//        isServiceEnding = true
//
//        subscriptions.clear()
//        super.onDestroy()
//    }

    abstract fun playlistRefreshComplete()

    abstract fun newTrackRetrievalComplete(newTrackIndex: Int)

    abstract fun authorizationFailed()
}