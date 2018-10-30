package com.chrisfry.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BaseRecyclerViewAdapter
import com.chrisfry.socialq.userinterface.adapters.holders.BasicTrackHolder
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

/**
 * Adapter for displaying a list of tracks (track name and artists)
 */
class BasicTrackListAdapter : BaseRecyclerViewAdapter<BasicTrackHolder, PlaylistTrack>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicTrackHolder {
        return BasicTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_track_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicTrackHolder, position: Int) {
        val playlistTrack = itemList[position]
        holder.setArtistName(DisplayUtils.getTrackArtistString(playlistTrack))
        holder.setTrackName(playlistTrack.track.name)
    }
}