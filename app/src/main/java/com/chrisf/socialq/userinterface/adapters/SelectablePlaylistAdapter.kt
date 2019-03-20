package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.userinterface.adapters.holders.IHolderSelectionListener
import com.chrisf.socialq.userinterface.adapters.holders.PlaylistSelectionHolder
import kaaes.spotify.webapi.android.models.PlaylistSimple

class SelectablePlaylistAdapter : BaseRecyclerViewAdapter<PlaylistSelectionHolder, PlaylistSimple>(), IHolderSelectionListener<String> {
    var listener: IItemSelectionListener<String>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistSelectionHolder {
        return PlaylistSelectionHolder(LayoutInflater.from(parent.context).inflate(R.layout.playlist_display_holder, parent, false))
    }

    override fun onBindViewHolder(holder: PlaylistSelectionHolder, position: Int) {
        holder.listener = this
        val itemToDisplay = itemList[position]
        holder.setPlaylist(itemToDisplay)
        // IDEA: Expand playlist description (if available) on click
    }

    override fun onHolderSelected(holderItem: String) {
        listener!!.onItemSelected(holderItem)
    }
}