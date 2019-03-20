package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.userinterface.adapters.holders.SelectableAlbumCardViewHolder
import com.chrisf.socialq.userinterface.interfaces.ISpotifySelectionPositionListener
import com.chrisf.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.Album

class AlbumCardAdapter : BaseRecyclerViewAdapter<SelectableAlbumCardViewHolder, Album>(), ISpotifySelectionPositionListener {
    var displayArtistFlag = true
    lateinit var listener: ISpotifySelectionPositionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableAlbumCardViewHolder {
        return SelectableAlbumCardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.album_card_view_holder, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableAlbumCardViewHolder, position: Int) {
        val albumToDisplay = itemList[position]
        holder.setAlbumName(albumToDisplay.name)
        if (displayArtistFlag) {
            holder.setArtistName(DisplayUtils.getAlbumArtistString(albumToDisplay))
        }
        if (albumToDisplay.images.size > 0) {
            holder.setImageUrl(albumToDisplay.images[0].url)
        } else {
            holder.setImageUrl("")
        }
        holder.uri = albumToDisplay.uri
        holder.listener = this
    }

    override fun onSelection(uri: String, position: Int) {
        listener.onSelection(uri, position)
    }

}