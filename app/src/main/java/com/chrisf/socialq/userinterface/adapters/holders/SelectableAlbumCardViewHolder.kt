package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import com.chrisf.socialq.userinterface.interfaces.ISpotifySelectionPositionListener

class SelectableAlbumCardViewHolder(itemView: View) : AlbumCardViewHolder(itemView), View.OnClickListener {
    lateinit var uri: String
    lateinit var listener: ISpotifySelectionPositionListener

    init {
        itemView.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener.onSelection(uri, adapterPosition)
    }
}