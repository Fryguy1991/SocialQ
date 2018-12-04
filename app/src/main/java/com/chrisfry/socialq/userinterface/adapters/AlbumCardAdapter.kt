package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SelectableAlbumCardViewHolder
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener
import com.chrisfry.socialq.utils.DisplayUtils
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