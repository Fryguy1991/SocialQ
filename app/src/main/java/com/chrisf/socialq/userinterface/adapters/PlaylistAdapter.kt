package com.chrisf.socialq.userinterface.adapters

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chrisf.socialq.R
import com.chrisf.socialq.model.spotify.PlaylistSimple
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import kotlinx.android.synthetic.main.holder_playlist.view.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PlaylistAdapter @Inject constructor(private val resources: Resources) : BaseRecyclerViewAdapter<PlaylistViewHolder, PlaylistSimple>(), PlaylistClickHandler {

    private val playlistRelay: PublishRelay<String> = PublishRelay.create()
    val playlistSelections: Observable<String> = playlistRelay.hide()

    private var selectedIndex = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(
            playlist = itemList[position],
            clickHandler = this,
            resources = resources,
            isSelected = position == selectedIndex,
            position = position
        )
    }

    override fun playlistSelected(playlistClick: PlaylistClick) {
        selectedIndex = if (playlistClick.playlistIndex == selectedIndex) {
            playlistRelay.accept("")
            -1
        } else {
            playlistRelay.accept(playlistClick.playlistId)
            playlistClick.playlistIndex
        }
        notifyDataSetChanged()
    }

    fun deselectPlaylist() {
        selectedIndex = -1
        notifyDataSetChanged()
    }
}

class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(
            playlist: PlaylistSimple,
            clickHandler: PlaylistClickHandler,
            resources: Resources,
            isSelected: Boolean,
            position: Int
    ) {
        itemView.playlistName.text = playlist.name
        itemView.playlistTrackCount.text = resources.getString(
                R.string.number_of_tracks,
                playlist.tracks.total.toString()
        )
        if (isSelected) setSelected() else setNotSelected()

        itemView.clicks()
                .throttleFirst(300, TimeUnit.MILLISECONDS)
                .subscribe{ clickHandler.playlistSelected(PlaylistClick(playlist.id, position)) }
    }

    private fun setSelected() {
        val backgroundColor = ContextCompat.getColor(itemView.context, R.color.BurntOrangeLight2)
        val trackCountColor = ContextCompat.getColor(itemView.context, R.color.White)
        itemView.setBackgroundColor(backgroundColor)
        itemView.playlistTrackCount.setTextColor(trackCountColor)
    }

    private fun setNotSelected() {
        val backgroundColor = ContextCompat.getColor(itemView.context, R.color.Transparent)
        val trackCountColor = ContextCompat.getColor(itemView.context, R.color.Slate2)
        itemView.setBackgroundColor(backgroundColor)
        itemView.playlistTrackCount.setTextColor(trackCountColor)
    }
}

interface PlaylistClickHandler {
    fun playlistSelected(playlistClick: PlaylistClick)
}

data class PlaylistClick(
        val playlistId: String,
        val playlistIndex: Int
)