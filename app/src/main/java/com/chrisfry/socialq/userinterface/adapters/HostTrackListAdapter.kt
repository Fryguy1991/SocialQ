package com.chrisfry.socialq.userinterface.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.model.ClientRequestData
import com.chrisfry.socialq.userinterface.adapters.holders.HostTrackHolder
import com.chrisfry.socialq.utils.DisplayUtils

/**
 * Adapter for displaying a list of tracks (track name, artists, and client who added track)
 */
class HostTrackListAdapter(val context : Context) : BaseRecyclerViewAdapter<HostTrackHolder, ClientRequestData>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostTrackHolder {
        return HostTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.host_track_holder, parent, false))
    }

    override fun onBindViewHolder(holder: HostTrackHolder, position: Int) {
        val trackToDisplay = itemList[position]
        holder.setTrackName(trackToDisplay.track.track.name)
        holder.setArtistName(DisplayUtils.getTrackArtistString(trackToDisplay.track))
        // Ensure track has an album image before attempting to access
        if (trackToDisplay.track.track.album.images.size > 0) {
            holder.setAlbumImage(trackToDisplay.track.track.album.images[0].url)
        }
        // If display name is blank display user ID instead
        if (trackToDisplay.user.display_name != null && !trackToDisplay.user.display_name.isEmpty()) {
            holder.setClientName(trackToDisplay.user.display_name)
        } else {
            holder.setClientName(trackToDisplay.user.id)
        }
    }
}