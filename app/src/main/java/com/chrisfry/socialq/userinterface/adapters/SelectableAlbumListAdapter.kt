package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BasicImageTextHolder
import com.chrisfry.socialq.userinterface.adapters.holders.ClickableImageTextHolder

class SelectableAlbumListAdapter(val listener: AlbumSelectListener) : BasicAlbumListAdapter(), ClickableImageTextHolder.ItemSelectionListener {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicImageTextHolder {
        return ClickableImageTextHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_image_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicImageTextHolder, position: Int) {
        super.onBindViewHolder(holder, position)

        if (holder is ClickableImageTextHolder) {
            holder.setId(itemList[position].id)
            holder.setItemSelectionListener(this)
        }
    }

    override fun onItemSelected(itemId: String) {
        listener.onAlbumSelected(itemId)
    }

    // Interface for passing album ID up from adapter
    interface AlbumSelectListener{
        fun onAlbumSelected(albumId: String)
    }
}