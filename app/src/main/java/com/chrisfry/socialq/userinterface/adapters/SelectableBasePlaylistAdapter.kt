package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SelectableBasePlaylistHolder
import kaaes.spotify.webapi.android.models.PlaylistSimple

class SelectableBasePlaylistAdapter : BaseRecyclerViewAdapter<SelectableBasePlaylistHolder, PlaylistSimple>(), IItemSelectionListener<Int> {
    private var selectedIndex = -1

    private lateinit var listener: IItemSelectionListener<String>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableBasePlaylistHolder {
        return SelectableBasePlaylistHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_base_playlist, parent, false))
    }

    override fun onBindViewHolder(holder: SelectableBasePlaylistHolder, position: Int) {
        if (position >= 0 && position < itemList.size) {
            val playlistToDisplay = itemList[position]
            holder.setSelectionListener(this)
            holder.setPlaylistName(playlistToDisplay.name)
            holder.setTrackCoundText(playlistToDisplay.tracks.total)

            if (position == selectedIndex) {
                holder.setSelected()
            } else {
                holder.setNotSelected()
            }
        }
    }

    override fun onItemSelected(selectedItem: Int) {
        if (selectedItem >= 0 && selectedItem < itemList.size) {
            if (selectedIndex == selectedItem) {
                selectedIndex = -1
                listener.onItemSelected("")
            } else {
                selectedIndex = selectedItem
                listener.onItemSelected(itemList[selectedItem].id)
            }
            notifyDataSetChanged()
        }
    }

    fun setListener(listener: IItemSelectionListener<String>) {
        this.listener = listener
    }

    fun clearSelection() {
        selectedIndex = -1
        notifyDataSetChanged()
    }
}