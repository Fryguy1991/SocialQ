package com.chrisfry.socialq.userinterface.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.holders.BaseTrackHolder
import com.chrisfry.socialq.utils.DisplayUtils
import kaaes.spotify.webapi.android.models.PlaylistTrack

class PlaylistTrackListAdapter() : RecyclerView.Adapter<BaseTrackHolder>() {
    private lateinit var trackList : MutableList<PlaylistTrack>

    constructor(trackList : MutableList<PlaylistTrack>) : this() {
        this.trackList = trackList

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTrackHolder {
        return BaseTrackHolder(LayoutInflater.from(parent.context).inflate(R.layout.base_track_holder, parent, false))
    }

    override fun getItemCount(): Int {
        return trackList.size
    }

    override fun onBindViewHolder(holder: BaseTrackHolder, position: Int) {
        val playlistTrack = trackList[position]
        holder.setArtistName(DisplayUtils.getTrackArtistString(playlistTrack))
        holder.setTrackName(playlistTrack.track.name)
    }

    fun updateQueueList(tracks: MutableList<PlaylistTrack>) {
        trackList = tracks
        notifyDataSetChanged()
    }
}