package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.ArtistCardViewHolder
import com.chrisfry.socialq.userinterface.interfaces.ISpotifySelectionPositionListener
import kaaes.spotify.webapi.android.models.Artist

class ArtistCardAdapter : BaseRecyclerViewAdapter<ArtistCardAdapter.SelectableArtistCardViewHolder, Artist>(), ISpotifySelectionPositionListener {
    lateinit var listener: ISpotifySelectionPositionListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableArtistCardViewHolder {
        return SelectableArtistCardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.artist_card_view, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableArtistCardViewHolder, position: Int) {
        val artistToDisplay = itemList[position]
        holder.setName(artistToDisplay.name)
        if (artistToDisplay.images.size > 0) {
            holder.setImageUrl(artistToDisplay.images[0].url)
        }
        holder.listener = this
        holder.uri = artistToDisplay.uri
    }

    override fun onSelection(uri: String, position: Int) {
        listener.onSelection(uri, position)
    }

    class SelectableArtistCardViewHolder(itemView: View) : ArtistCardViewHolder(itemView), View.OnClickListener {
        lateinit var uri: String
        lateinit var listener: ISpotifySelectionPositionListener

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            listener.onSelection(uri, adapterPosition)
        }
    }
}