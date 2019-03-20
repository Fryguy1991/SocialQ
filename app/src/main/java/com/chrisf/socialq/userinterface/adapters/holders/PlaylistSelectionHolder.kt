package com.chrisf.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import kaaes.spotify.webapi.android.models.PlaylistSimple

class PlaylistSelectionHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
    var playlistId: String? = null
    var listener: IHolderSelectionListener<String>? = null

    private val nameText = itemView.findViewById<TextView>(R.id.tv_playlist_name)
    private val trackCountText = itemView.findViewById<TextView>(R.id.tv_playlist_track_count)

    init {
        itemView.setOnClickListener(this)
    }

    fun setPlaylist(playlist: PlaylistSimple) {
        playlistId = playlist.id

        nameText.text = playlist.name
        trackCountText.text = String.format(trackCountText.context.getString(R.string.number_of_tracks), playlist.tracks.total)
    }

    override fun onClick(v: View?) {
        listener!!.onHolderSelected(playlistId!!)
    }
}