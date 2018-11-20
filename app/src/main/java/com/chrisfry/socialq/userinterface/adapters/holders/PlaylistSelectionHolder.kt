package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View

class PlaylistSelectionHolder(itemView: View) : SimpleTextHolder(itemView), View.OnClickListener {
    var playlistId: String? = null
    var listener: IHolderSelectionListener<String>? = null

    init {
        itemView.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        listener!!.onHolderSelected(playlistId!!)
    }
}