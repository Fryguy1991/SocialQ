package com.chrisfry.socialq.userinterface.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BaseRecyclerViewAdapter
import com.chrisfry.socialq.userinterface.adapters.holders.HostTrackHolder
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

/**
 * Adapter for displaying a list of tracks (track name, artists, and client who added track)
 */
class HostTrackListAdapter(val context : Context) : BaseRecyclerViewAdapter<HostTrackHolder, PlaylistTrack>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostTrackHolder {
        return HostTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.host_track_holder, parent, false))
    }

    override fun onBindViewHolder(holder: HostTrackHolder, position: Int) {
        val trackToDisplay = itemList[position]
        holder.setTrackName(trackToDisplay.track.name)
        holder.setArtistName(DisplayUtils.getTrackArtistString(trackToDisplay))
        // TODO: Replace with actual user id
        holder.setClientName(String.format(context.getString(R.string.added_by_client_formatter), "not_implemented"))
    }
}