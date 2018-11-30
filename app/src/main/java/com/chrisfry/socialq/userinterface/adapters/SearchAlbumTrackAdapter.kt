package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SelectableAlbumTrackHolder
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionListener
import kaaes.spotify.webapi.android.models.TrackSimple

class SearchAlbumTrackAdapter : BaseRecyclerViewAdapter<SelectableAlbumTrackHolder, TrackSimple>(), ISpotifySelectionListener {
    lateinit var listener: ISpotifySelectionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableAlbumTrackHolder {
        return SelectableAlbumTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.simple_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableAlbumTrackHolder, position: Int) {
        val trackToShow = itemList[position]
        holder.setText(String.format(holder.itemView.context.getString(R.string.album_track_with_position_number), position + 1, trackToShow.name))
        holder.uri = trackToShow.uri
        holder.listener = this
    }

    override fun onSelection(uri: String) {
        listener.onSelection(uri)
    }
}