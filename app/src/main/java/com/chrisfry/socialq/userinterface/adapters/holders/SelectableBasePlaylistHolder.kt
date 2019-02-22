package com.chrisfry.socialq.userinterface.adapters.holders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.annotation.ColorInt
import android.util.TypedValue
import com.chrisfry.socialq.R
import com.chrisfry.socialq.userinterface.adapters.IItemSelectionListener


class SelectableBasePlaylistHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val rootView = itemView
    private val context = itemView.context
    private val playlistNameText = itemView.findViewById<TextView>(R.id.tv_playlist_name)
    private val numberOfTracksText = itemView.findViewById<TextView>(R.id.tv_playlist_track_count)

    private var listener: IItemSelectionListener<Int>? = null

    init {
        itemView.setOnClickListener {listener?.onItemSelected(adapterPosition)}
    }

    fun setPlaylistName(name: String) {
        playlistNameText.text = name
    }

    fun setTrackCoundText(trackCount: Int) {
        numberOfTracksText.text = String.format(context.getString(R.string.number_of_tracks), trackCount)
    }

    fun setSelectionListener(listener: IItemSelectionListener<Int>) {
        this.listener = listener
    }

    fun setSelected() {
        val backgroundTypedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(android.R.attr.colorAccent, backgroundTypedValue, true)
        @ColorInt val backgroundColor = backgroundTypedValue.data

        val titleTextColorTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, titleTextColorTypedValue, true)
        @ColorInt val playlistTextColor = titleTextColorTypedValue.data

        val tracksTextColorTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimaryInverse, tracksTextColorTypedValue, true)
        @ColorInt val tracksTextColor = tracksTextColorTypedValue.data

        rootView.setBackgroundColor(backgroundColor)
        playlistNameText.setTextColor(playlistTextColor)
        numberOfTracksText.setTextColor(tracksTextColor)
    }

    fun setNotSelected() {
        val backgroundTypedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(android.R.attr.windowBackground, backgroundTypedValue, true)
        @ColorInt val backgroundColor = backgroundTypedValue.data

        val titleTextColorTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, titleTextColorTypedValue, true)
        @ColorInt val playlistTextColor = titleTextColorTypedValue.data

        val tracksTextColorTypedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tracksTextColorTypedValue, true)
        @ColorInt val tracksTextColor = tracksTextColorTypedValue.data

        rootView.setBackgroundColor(backgroundColor)
        playlistNameText.setTextColor(playlistTextColor)
        numberOfTracksText.setTextColor(tracksTextColor)
    }
}