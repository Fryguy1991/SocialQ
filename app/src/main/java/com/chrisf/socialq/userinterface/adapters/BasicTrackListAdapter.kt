package com.chrisf.socialq.userinterface.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisf.socialq.R
import com.chrisf.socialq.userinterface.adapters.holders.BasicTrackAlbumHolder
import com.chrisf.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

/**
 * Adapter for displaying a list of tracks (track name and artists)
 */
class BasicTrackListAdapter : BaseRecyclerViewAdapter<BasicTrackAlbumHolder, PlaylistTrack>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicTrackAlbumHolder {
        return BasicTrackAlbumHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_track_album_holder, parent, false))
    }

    override fun onBindViewHolder(holder: BasicTrackAlbumHolder, position: Int) {
        val playlistTrack = itemList[position]
        holder.setArtistName(DisplayUtils.getTrackArtistString(playlistTrack))
        holder.setName(playlistTrack.track.name)
        if (playlistTrack.track.album.images.size > 0) {
            holder.setAlbumImage(playlistTrack.track.album.images[0].url)
        } else {
            holder.setAlbumImage("")
        }
    }
}