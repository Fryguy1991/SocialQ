package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener

class SelectableTrackAlbumHolder(view: View) : BasicTrackAlbumHolder(view), View.OnClickListener {

    private val baseLayout = view.findViewById<View>(R.id.cl_track_album_holder_base)
    lateinit var uri: String
    lateinit var listener: ISpotifySelectionPositionListener

    init {
        baseLayout.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri, adapterPosition)
    }
}