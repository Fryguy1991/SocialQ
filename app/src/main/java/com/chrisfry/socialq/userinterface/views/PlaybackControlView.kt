package com.chrisfry.socialq.userinterface.views

import android.content.Context
import android.os.Build
import android.transition.TransitionManager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

class PlaybackControlView : ConstraintLayout {
    companion object {
        val TAG = PlaybackControlView::class.java.name
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    // UI ELEMENTS
    // Root constraint layout
    private val rootLayout: ConstraintLayout
    // Current track information
    private val albumArtImage: ImageView
    private val trackNameText: TextView
    private val artistNameText: TextView
    private val userNameText: TextView
    // Playback control buttons
    private val playPauseButton: View
    private val skipButton: View

    // Listener for communicating button presses
    private lateinit var listener: IPlaybackControlListener
    // Flag for if the player is currently playing
    private var isPlaying = false
    // Flag for keeping track of if view is expanded or not
    private var isExpanded = false

    init {
        LayoutInflater.from(context).inflate(R.layout.playback_control_layout, this)

        rootLayout = findViewById(R.id.cl_playback_root)

        albumArtImage = findViewById(R.id.cv_playback_control_album_image)
        trackNameText = findViewById(R.id.tv_playback_control_track_name)
        artistNameText = findViewById(R.id.tv_playback_control_artist_name)
        userNameText = findViewById(R.id.tv_playback_control_user_name)

        playPauseButton = findViewById(R.id.btn_playback_control_play_pause)
        skipButton = findViewById(R.id.btn_playback_control_skip)

        rootLayout.setOnClickListener {
            if (isExpanded) {
                shrinkLayout()
            } else {
                expandLayout()
            }
        }

        playPauseButton.setOnClickListener {
            listener.requestPlayPause(isPlaying)
        }

        skipButton.setOnClickListener {
            listener.requestSkip()
        }
    }

    fun setListener(listener: IPlaybackControlListener) {
        this.listener = listener
    }

    fun shrinkLayout() {
        Log.d(TAG, "Shrinking playback control layout")

        trackNameText.maxLines = 1
        artistNameText.maxLines = 1
        userNameText.maxLines = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition(rootLayout)
        }

        isExpanded = false
    }

    private fun expandLayout() {
        Log.d(TAG, "Expanding playback control layout")

        trackNameText.maxLines = Integer.MAX_VALUE
        artistNameText.maxLines = Integer.MAX_VALUE
        userNameText.maxLines = Integer.MAX_VALUE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionManager.beginDelayedTransition(rootLayout)
        }

        isExpanded = true
    }

    fun setPlaying() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playPauseButton.background = resources.getDrawable(R.drawable.rectangle_pause_button, context.theme)
        } else {
            playPauseButton.background = resources.getDrawable(R.drawable.rectangle_pause_button)
        }
        isPlaying = true
    }

    fun setPaused() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playPauseButton.background = resources.getDrawable(R.drawable.rectangle_play_button, context.theme)
        } else {
            playPauseButton.background = resources.getDrawable(R.drawable.rectangle_play_button)
        }
        isPlaying = false
    }

    fun displayRequest(request: ClientRequestData) {
        userNameText.text = request.user.display_name

        displayTrack(request.track)
    }

    fun displayTrack(track: PlaylistTrack) {
        trackNameText.text = track.track.name
        artistNameText.text = DisplayUtils.getTrackArtistString(track)

        if (track.track.album.images.size > 0) {
            Glide.with(albumArtImage).load(track.track.album.images[0].url).into(albumArtImage)
        }
    }

    fun hideControls() {
        playPauseButton.visibility = View.GONE
        skipButton.visibility = View.GONE
    }

    fun hideUser() {
        userNameText.visibility = View.GONE
    }

    interface IPlaybackControlListener {
        fun requestPlayPause(isPlaying: Boolean)

        fun requestSkip()
    }
}