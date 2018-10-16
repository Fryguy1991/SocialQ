package com.chrisfry.socialq.userinterface.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.TrackHolder
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

class PlaylistTrackListAdapter() : RecyclerView.Adapter<TrackHolder>() {
    // TODO: Consider using a different holder.  TrackHolder has a lot of functionality only needed for search
    private lateinit var trackList : MutableList<PlaylistTrack>

    constructor(trackList : MutableList<PlaylistTrack>) : this() {
        this.trackList = trackList

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder {
        return TrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.track_list_holder, parent, false))
    }

    override fun getItemCount(): Int {
        return trackList.size
    }

    override fun onBindViewHolder(holder: TrackHolder, position: Int) {
        val playlistTrack = trackList.get(position)
        holder.setArtistName(DisplayUtils.getTrackArtistString(playlistTrack))
        holder.setTrackName(playlistTrack.track.name)
        holder.setTrackUri(playlistTrack.track.uri)
        holder.setAddButtonStatus(false)
    }

    fun updateQueueList(tracks: MutableList<PlaylistTrack>) {
        trackList = tracks
        notifyDataSetChanged()
    }
}