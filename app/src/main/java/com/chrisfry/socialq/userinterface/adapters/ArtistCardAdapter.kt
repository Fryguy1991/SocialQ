package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.ArtistCardViewHolder
import kaaes.spotify.webapi.android.models.Artist

class ArtistCardAdapter : BaseRecyclerViewAdapter<ArtistCardViewHolder, Artist>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistCardViewHolder {
        return ArtistCardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.artist_card_view, parent, false))
    }

    override fun onBindViewHolder(holder: ArtistCardViewHolder, position: Int) {
        val artistToDisplay = itemList[position]
        holder.setName(artistToDisplay.name)
        if (artistToDisplay.images.size > 0) {
            holder.setImageUrl(artistToDisplay.images[0].url)
        }
    }
}