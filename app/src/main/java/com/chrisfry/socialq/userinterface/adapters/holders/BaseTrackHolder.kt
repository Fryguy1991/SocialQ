package com.chrisfry.socialq.userinterface.adapters.holders

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import com.chrisfry.socialq.R

class BaseTrackHolder(view : View) : RecyclerView.ViewHolder(view) {
    private val trackNameView = view.findViewById<TextView>(R.id.tv_track_name)
    private val artistNameView = view.findViewById<TextView>(R.id.tv_artist_name)

    fun setArtistName(name : String) {
        artistNameView.text = name
    }

    fun setTrackName(name : String) {
        trackNameView.text = name
    }
}