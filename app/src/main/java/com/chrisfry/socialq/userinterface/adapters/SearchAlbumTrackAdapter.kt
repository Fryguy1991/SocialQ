package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.SimpleTextHolder
import kaaes.spotify.webapi.android.models.TrackSimple

class SearchAlbumTrackAdapter : BaseRecyclerViewAdapter<SimpleTextHolder, TrackSimple>() {
    // TODO: Use a holder and refactor to take touches from displayed tracks
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleTextHolder {
        return SimpleTextHolder(LayoutInflater.from(parent.context).inflate(R.layout.simple_text_holder, parent, false))
    }

    override fun onBindViewHolder(holder: SimpleTextHolder, position: Int) {
        val trackToShow = itemList[position]
        holder.setText(trackToShow.name)
    }
}