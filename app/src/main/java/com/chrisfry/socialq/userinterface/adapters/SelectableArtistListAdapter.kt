package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BasicImageTextHolder
import com.chrisfry.socialq.userinterface.adapters.holders.ClickableImageTextHolder

class SelectableArtistListAdapter(val listener: ArtistSelectListener) : BasicArtistListAdapter(), ClickableImageTextHolder.ItemSelectionListener {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClickableImageTextHolder {
        return ClickableImageTextHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_image_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicImageTextHolder, position: Int) {
        super.onBindViewHolder(holder, position)

        if (holder is ClickableImageTextHolder) {
            val artistToDisplay = itemList[position]
            holder.setId(artistToDisplay.id)
            holder.setItemSelectionListener(this)
        }
    }

    override fun onItemSelected(itemId: String) {
        listener.onArtistSelected(itemId)
    }

    // Logic to pass artist selection up from adapter
    interface ArtistSelectListener{
        fun onArtistSelected(artistId: String)
    }
}