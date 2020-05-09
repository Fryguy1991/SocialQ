package com.chrisf.socialq.userinterface.views

import android.content.Context
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.chrisf.socialq.R
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.userinterface.views.PlaybackControlEvent.PlayPauseButtonTouched
import com.chrisf.socialq.userinterface.views.PlaybackControlEvent.SkipButtonTouched
import com.chrisf.socialq.utils.DisplayUtils
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.playback_control_layout.view.*
import timber.log.Timber

class PlaybackControlView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defStyle) {

    // Flag for keeping track of if view is expanded or not
    private var isExpanded = false

    private val eventRelay: PublishRelay<PlaybackControlEvent> = PublishRelay.create()
    val eventStream: Observable<PlaybackControlEvent> = eventRelay.hide()

    val subscriptions = CompositeDisposable()

    init {
        LayoutInflater.from(context).inflate(R.layout.playback_control_layout, this)

        playbackControlRoot.setOnClickListener {
            if (isExpanded) {
                shrinkLayout()
            } else {
                expandLayout()
            }
        }

        Observable.merge(
            playbackControlPlayPauseButton.clicks().map { PlayPauseButtonTouched },
            playbackControlSkipButton.clicks().map { SkipButtonTouched }
        ).subscribe(eventRelay)
            .addTo(subscriptions)
    }

    fun shrinkLayout() {
        Timber.d("Shrinking playback control layout")

        playbackControlTrackName.maxLines = 1
        playbackControlArtistName.maxLines = 1
        playbackControlUserName.maxLines = 1

        TransitionManager.beginDelayedTransition(playbackControlRoot)

        isExpanded = false
    }

    private fun expandLayout() {
        Timber.d("Expanding playback control layout")

        playbackControlTrackName.maxLines = Integer.MAX_VALUE
        playbackControlArtistName.maxLines = Integer.MAX_VALUE
        playbackControlUserName.maxLines = Integer.MAX_VALUE

        TransitionManager.beginDelayedTransition(playbackControlRoot)

        isExpanded = true
    }

    fun setPlaying() {
        playbackControlPlayPauseButton.background =
            resources.getDrawable(R.drawable.rectangle_pause_button, context.theme)
    }

    fun setPaused() {
        playbackControlPlayPauseButton.background =
            resources.getDrawable(R.drawable.rectangle_play_button, context.theme)
    }

    fun displayRequest(request: ClientRequestData) {
        playbackControlUserName.text = request.user.display_name

        displayTrack(request.track)
    }

    fun displayTrack(track: PlaylistTrack) {
        playbackControlTrackName.text = track.track.name
        playbackControlArtistName.text = DisplayUtils.getTrackArtistString(track)

        if (track.track.album.images.isNotEmpty()) {
            Glide.with(playbackControlAlbumImage)
                .load(track.track.album.images[0].url)
                .into(playbackControlAlbumImage)
        }
    }

    fun hideControls() {
        playbackControlPlayPauseButton.visibility = View.GONE
        playbackControlSkipButton.visibility = View.GONE
    }

    fun hideUser() {
        playbackControlUserName.visibility = View.GONE
    }
}

/**
 * Events that can be received from the playback control view event observable
 */
sealed class PlaybackControlEvent {
    object PlayPauseButtonTouched : PlaybackControlEvent()

    object SkipButtonTouched : PlaybackControlEvent()
}