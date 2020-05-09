package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.model.ClientRequestData
import com.chrisf.socialq.userinterface.adapters.holders.HostTrackHolder
import com.chrisf.socialq.utils.DisplayUtils

/**
 * Adapter for displaying a list of tracks (track name, artists, and client who added track)
 */
class HostTrackListAdapter : BaseRecyclerViewAdapter<HostTrackHolder, ClientRequestData>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostTrackHolder {
        return HostTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.holder_host_track, parent, false))
    }

    override fun onBindViewHolder(holder: HostTrackHolder, position: Int) {
        val trackToDisplay = itemList[position]
        holder.setName(trackToDisplay.track.track.name)
        holder.setArtistName(DisplayUtils.getTrackArtistString(trackToDisplay.track))
        // Ensure track has an album image before attempting to access
        if (trackToDisplay.track.track.album.images.size > 0) {
            holder.setAlbumImage(trackToDisplay.track.track.album.images[0].url)
        } else {
            holder.setAlbumImage("")
        }
        // If display name is blank display user ID instead
        if (trackToDisplay.user.display_name != null && !trackToDisplay.user.display_name.isEmpty()) {
            holder.setClientName(trackToDisplay.user.display_name)
        } else {
            holder.setClientName(trackToDisplay.user.id)
        }
    }
}