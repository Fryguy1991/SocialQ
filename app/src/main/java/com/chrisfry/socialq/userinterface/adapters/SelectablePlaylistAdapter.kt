package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.IHolderSelectionListener
import com.chrisfry.socialq.userinterface.adapters.holders.PlaylistSelectionHolder
import kaaes.spotify.webapi.android.models.PlaylistSimple

class SelectablePlaylistAdapter : BaseRecyclerViewAdapter<PlaylistSelectionHolder, PlaylistSimple>(), IHolderSelectionListener<String> {
    var listener: IItemSelectionListener<String>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistSelectionHolder {
        return PlaylistSelectionHolder(LayoutInflater.from(parent.context).inflate(R.layout.simple_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: PlaylistSelectionHolder, position: Int) {
        holder.listener = this
        val itemToDisplay = itemList[position]
        holder.playlistId = itemToDisplay.id
        holder.setText(itemToDisplay.name)
        // TODO: Consider displaying more playlist details?
        // IDEA: Expand playlist description (if available) on click
    }

    override fun onHolderSelected(holderItem: String) {
        listener!!.onItemSelected(holderItem)
    }
}