package com.chrisfry.socialq.userinterface.adapters

import androidx.recyclerview.widget.RecyclerView
import com.chrisfry.socialq.userinterface.adapters.holders.TrackHolder
import kaaes.spotify.webapi.android.models.TrackSimple

// TODO: Refactor track selection adapter to extend the newly created base adapter.
class SelectableTrackListAdapter(trackList: List<TrackSimple>) : SearchTrackListAdapter(trackList) {

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val trackHolder = holder as TrackHolder
        val track = mTrackList[position]
        trackHolder.setTrackName(track.name)
        trackHolder.setTrackUri(track.uri)
        trackHolder.setItemSelectionListener(this)
        trackHolder.setAddButtonStatus(mExposedUri == track.uri)
    }
}