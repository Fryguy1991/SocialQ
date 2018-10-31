package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import com.chrisfry.socialq.R

class ClickableArtistHolder(view : View): BasicArtistHolder(view) {
    private val baseLayout = view.findViewById<View>(R.id.cl_artist_holder)
    private lateinit var artistId : String
    private lateinit var listener : ArtistSelectListener

    init {
        baseLayout.setOnClickListener {listener.onArtistSelected(artistId)}
    }

    fun setArtistId(artistId : String) {
        this.artistId = artistId
    }

    // Logic for listening to when an artist is selected
    interface ArtistSelectListener {
        fun onArtistSelected(artistId : String)
    }

    fun setArtistSelectListener(listener : ArtistSelectListener) {
        this.listener = listener
    }
}