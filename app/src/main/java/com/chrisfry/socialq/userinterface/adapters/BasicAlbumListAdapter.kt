package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BasicImageTextHolder
import kaaes.spotify.webapi.android.models.Album

open class BasicAlbumListAdapter : BaseRecyclerViewAdapter<BasicImageTextHolder, Album>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicImageTextHolder {
        return BasicImageTextHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_image_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicImageTextHolder, position: Int) {
        val albumToDisplay = itemList[position]
        holder.setDisplayName(albumToDisplay.name)
        if (albumToDisplay.images.size > 0) {
            holder.setImage(albumToDisplay.images[0].url)
        }
    }
}