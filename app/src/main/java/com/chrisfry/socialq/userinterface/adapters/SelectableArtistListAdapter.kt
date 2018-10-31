package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BasicArtistHolder
import com.chrisfry.socialq.userinterface.adapters.holders.ClickableArtistHolder

class SelectableArtistListAdapter(val listener: ArtistSelectListener) : BasicArtistListAdapter(), ClickableArtistHolder.ArtistSelectListener {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClickableArtistHolder {
        return ClickableArtistHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_artist_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicArtistHolder, position: Int) {
        super.onBindViewHolder(holder, position)

        if (holder is ClickableArtistHolder) {
            val artistToDisplay = itemList[position]
            holder.setArtistId(artistToDisplay.id)
            holder.setArtistSelectListener(this)
        }
    }

    override fun onArtistSelected(artistId: String) {
        listener.onArtistSelected(artistId)
    }

    // Logic to pass artist selection up from adapter
    interface ArtistSelectListener{
        fun onArtistSelected(artistId: String)
    }
}