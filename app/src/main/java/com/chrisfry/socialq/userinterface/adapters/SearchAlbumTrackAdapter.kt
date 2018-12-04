package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SelectableAlbumTrackHolder
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener
import kaaes.spotify.webapi.android.models.TrackSimple

class SearchAlbumTrackAdapter : BaseRecyclerViewAdapter<SelectableAlbumTrackHolder, TrackSimple>(), ISpotifySelectionPositionListener {
    lateinit var listener: ISpotifySelectionPositionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableAlbumTrackHolder {
        // TODO: Use new holder with 2 text views so long tracks don't make tracks lists look weird
        return SelectableAlbumTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.simple_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableAlbumTrackHolder, position: Int) {
        val trackToShow = itemList[position]
        holder.setText(trackToShow.name)
        holder.uri = trackToShow.uri
        holder.listener = this
    }

    override fun onSelection(uri: String, position: Int) {
        listener.onSelection(uri, position)
    }
}