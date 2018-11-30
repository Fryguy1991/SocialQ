package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.BaseRecyclerViewAdapter
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.Album

class AlbumCardAdapter : BaseRecyclerViewAdapter<AlbumCardViewHolder, Album>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumCardViewHolder {
        return AlbumCardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.album_card_view, parent, false))
    }

    override fun onBindViewHolder(holder: AlbumCardViewHolder, position: Int) {
        val albumToDisplay = itemList[position]
        holder.setAlbumName(albumToDisplay.name)
        holder.setArtistName(DisplayUtils.getAlbumArtistString(albumToDisplay))
        if (albumToDisplay.images.size > 0) {
            holder.setImageUrl(albumToDisplay.images[0].url)
        }
    }
}