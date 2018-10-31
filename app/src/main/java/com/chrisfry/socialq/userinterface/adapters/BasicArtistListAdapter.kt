package com.chrisfry.socialq.userinterface.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BasicArtistHolder
import kaaes.spotify.webapi.android.models.Artist

class BasicArtistListAdapter(private val context : Context) : BaseRecyclerViewAdapter<BasicArtistHolder, Artist>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicArtistHolder {
        return BasicArtistHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_artist_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicArtistHolder, position: Int) {
        val artistToDisplay = itemList[position]
        holder.setArtistName(artistToDisplay.name)
        if (artistToDisplay.images.size > 0) {
            holder.setArtistImage(artistToDisplay.images[0].url)
        }
    }
}