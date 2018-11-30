package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SelectableTrackAlbumHolder
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.Track

class SearchTrackAdapter : BaseRecyclerViewAdapter<SelectableTrackAlbumHolder, Track>(), ISpotifySelectionPositionListener {
    lateinit var listener: ISpotifySelectionPositionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableTrackAlbumHolder {
        return SelectableTrackAlbumHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_track_album_holder, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableTrackAlbumHolder, position: Int) {
        val trackToDisplay = itemList[position]
        holder.setArtistName(DisplayUtils.getTrackArtistString(trackToDisplay))
        holder.setName(trackToDisplay.name)
        if (trackToDisplay.album.images.size > 0) {
            holder.setAlbumImage(trackToDisplay.album.images[0].url)
        }
        holder.uri = trackToDisplay.uri
        holder.listener = this
    }

    override fun onSelection(uri: String, position: Int) {
        listener.onSelection(uri, position)
    }
}